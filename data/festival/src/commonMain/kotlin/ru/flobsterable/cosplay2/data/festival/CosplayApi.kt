package ru.flobsterable.cosplay2.data.festival

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.flobsterable.cosplay2.model.EventTypeDto
import ru.flobsterable.cosplay2.model.FestivalActionLink
import ru.flobsterable.cosplay2.model.FestivalListResponse

interface CosplayApi {
    suspend fun fetchFestivalList(year: Int, wid: String): FestivalListResponse
    suspend fun fetchEventTypes(): List<EventTypeDto>
    suspend fun fetchSchemas(sourceUrl: String): Map<String, FestivalSchema>
    suspend fun fetchFestivalHomePage(baseUrl: String): FestivalHomePageData
}

class CosplayApiImpl(
    private val client: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) : CosplayApi {

    private var rootSchemaCache: Map<String, FestivalSchema>? = null

    override suspend fun fetchFestivalList(year: Int, wid: String): FestivalListResponse {
        val responseText = client.post("https://cosplay2.ru/api/events/filter_list") {
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("wid", JsonPrimitive(wid))
                    put("active_year", JsonPrimitive(year))
                }
            )
        }.body<String>()
        return json.decodeFromString(responseText)
    }

    override suspend fun fetchEventTypes(): List<EventTypeDto> {
        val responseText = client.get("https://cosplay2.ru/api/events/get_types").body<String>()
        return json.decodeFromString(responseText)
    }

    override suspend fun fetchSchemas(sourceUrl: String): Map<String, FestivalSchema> {
        if (sourceUrl == "https://cosplay2.ru/") {
            rootSchemaCache?.let { return it }
        }

        val html = client.get(sourceUrl).body<String>()
        val scripts = APPLICATION_JSON_REGEX.findAll(html).map { it.groupValues[1] }.toList()

        val parsed = buildMap {
            scripts.forEach { script ->
                runCatching { json.parseToJsonElement(script) }
                    .getOrNull()
                    ?.let { root -> collectSchemas(root, this) }
            }
        }

        if (sourceUrl == "https://cosplay2.ru/") {
            rootSchemaCache = parsed
        }

        return parsed
    }

    override suspend fun fetchFestivalHomePage(baseUrl: String): FestivalHomePageData {
        val pageUrl = "${baseUrl.trimEnd('/')}/get_pages/home"
        val html = client.get(pageUrl).body<String>()
        return FestivalHomePageData(
            content = extractReadableText(html, baseUrl),
            links = extractActionLinks(html, baseUrl)
        )
    }

    private fun collectSchemas(element: JsonElement, acc: MutableMap<String, FestivalSchema>) {
        when (element) {
            is JsonObject -> {
                val type = element["@type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val url = element["url"]?.jsonPrimitive?.contentOrNull
                if (type == "Event" && !url.isNullOrBlank()) {
                    val location = element["location"]?.jsonObject
                    val address = location?.get("address")?.jsonObject
                    acc[normalizeUrl(url)] = FestivalSchema(
                        imageUrl = element["image"]?.jsonPrimitive?.contentOrNull,
                        description = decodeHtml(element["description"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                        startDate = element["startDate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        endDate = element["endDate"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        venueCity = decodeHtml(
                            location?.get("name")?.jsonPrimitive?.contentOrNull
                                ?: address?.get("addressLocality")?.jsonPrimitive?.contentOrNull.orEmpty()
                        ),
                        venueCountryCode = address?.get("addressCountry")?.jsonPrimitive?.contentOrNull.orEmpty()
                    )
                }
                element.values.forEach { child -> collectSchemas(child, acc) }
            }

            is JsonArray -> {
                element.forEach { child -> collectSchemas(child, acc) }
            }

            else -> Unit
        }
    }

    private fun normalizeUrl(url: String): String = url.trim().trimEnd('/').lowercase()

    private companion object {
        private val APPLICATION_JSON_REGEX =
            Regex("""<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
    }
}

data class FestivalSchema(
    val imageUrl: String?,
    val description: String,
    val startDate: String,
    val endDate: String,
    val venueCity: String,
    val venueCountryCode: String
)

data class FestivalHomePageData(
    val content: String = "",
    val links: List<FestivalActionLink> = emptyList()
)
