package ru.alekseandrgrigorev.cosplay.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import ru.alekseandrgrigorev.cosplay.logging.AppLogger

class CosplayRepositoryImpl(
    private val client: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) : CosplayRepository {

    private var schemaCache: Map<String, FestivalSchema>? = null
    private var festivalCache: Map<String, FestivalSummary> = emptyMap()
    private val tag = "CosplayRepo"

    override suspend fun getFestivalCatalog(year: Int): FestivalCatalog {
        val wid = generateWid()
        AppLogger.d(tag, "Loading archive page https://cosplay2.ru/archive/$year")
        val schemasByUrl = runCatching { fetchSchemas("https://cosplay2.ru/archive/$year") }
            .onFailure { AppLogger.e(tag, "Failed to load schema data from archive/$year", it) }
            .getOrElse { emptyMap() }

        AppLogger.d(tag, "Loading festivals from /api/events/filter_list with wid=$wid and active_year=$year")
        val response = runCatching {
            val responseText = client.post("https://cosplay2.ru/api/events/filter_list") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("wid", JsonPrimitive(wid))
                        put("active_year", JsonPrimitive(year))
                    }
                )
            }.body<String>()
            json.decodeFromString<FestivalListResponse>(responseText)
        }.getOrElse {
            AppLogger.e(tag, "Failed to load /api/events/filter_list. Switching to fallback mode.", it)
            return FestivalCatalog(
                festivals = fallbackFestivals(),
                eventTypes = fallbackEventTypes(),
                year = year,
                isFallback = true
            )
        }
        AppLogger.d(tag, "Loaded ${response.events.size} festivals from /api/events/filter_list")

        AppLogger.d(tag, "Loading event types from /api/events/get_types")
        val types = runCatching {
            val responseText = client.get("https://cosplay2.ru/api/events/get_types").body<String>()
            json.decodeFromString<List<EventTypeDto>>(responseText)
        }.getOrElse {
            AppLogger.e(tag, "Failed to load /api/events/get_types. Using fallback event types.", it)
            emptyList()
        }
        AppLogger.d(tag, "Loaded ${types.size} event types from /api/events/get_types")

        val festivals = response.events.map { dto ->
                val normalizedUrl = normalizeUrl(dto.href)
                val schema = schemasByUrl[normalizedUrl]
                FestivalSummary(
                    id = dto.id,
                    title = decodeHtml(dto.title),
                    description = decodeHtml(dto.description),
                    city = decodeHtml(dto.city),
                    country = decodeHtml(dto.country),
                    eventTypeId = dto.eventTypeId,
                    startTime = dto.startTime,
                    endTime = dto.endTime,
                    displayTime = decodeHtml(dto.time),
                    status = FestivalStatus.fromRaw(dto.timestatus),
                    url = dto.href,
                    imageUrl = schema?.imageUrl ?: inferredLogoUrl(dto.id)
                )
            }
        festivalCache = festivals.associateBy { normalizeUrl(it.url) }

        return FestivalCatalog(
            festivals = festivals,
            eventTypes = types.associate { it.id to decodeHtml(it.titleOne.ifBlank { it.title }) }
                .ifEmpty { fallbackEventTypes() },
            year = year,
            isFallback = false
        )
    }

    override suspend fun getFestivalDetail(festival: FestivalSummary): FestivalDetail {
        AppLogger.d(tag, "Loading festival detail for id=${festival.id}, url=${festival.url}")
        val cachedFestival = festivalCache[normalizeUrl(festival.url)] ?: festival
        val year = festival.startTime.take(4).toIntOrNull()
        val schema = runCatching {
            if (year != null) fetchSchemas("https://cosplay2.ru/archive/$year")[normalizeUrl(festival.url)]
            else fetchSchemas("https://cosplay2.ru/")[normalizeUrl(festival.url)]
        }.getOrNull()
        val pageData = runCatching { fetchFestivalHomePageContent(festival.url) }
            .onFailure { AppLogger.e(tag, "Failed to load /get_pages/home for ${festival.url}", it) }
            .getOrDefault(FestivalHomePageData())
        val pageContent = pageData.content.ifBlank { buildFallbackPageContent(cachedFestival, schema) }

        return FestivalDetail(
            summary = cachedFestival,
            description = schema?.description?.ifBlank { cachedFestival.description } ?: cachedFestival.description,
            pageContent = pageContent,
            pageLinks = pageData.links,
            venueCity = schema?.venueCity ?: cachedFestival.city,
            venueCountryCode = schema?.venueCountryCode.orEmpty(),
            startDate = schema?.startDate ?: cachedFestival.startTime,
            endDate = schema?.endDate ?: cachedFestival.endTime,
            imageUrl = schema?.imageUrl ?: cachedFestival.imageUrl
        )
    }

    private suspend fun fetchFestivalHomePageContent(baseUrl: String): FestivalHomePageData {
        val pageUrl = "${baseUrl.trimEnd('/')}/get_pages/home"
        AppLogger.d(tag, "Loading festival home page content from $pageUrl")
        val html = client.get(pageUrl).body<String>()
        return FestivalHomePageData(
            content = extractReadableText(html, baseUrl),
            links = extractActionLinks(html, baseUrl)
        )
    }

    private suspend fun fetchSchemas(sourceUrl: String): Map<String, FestivalSchema> {
        if (sourceUrl == "https://cosplay2.ru/") {
            schemaCache?.let { return it }
        }

        AppLogger.d(tag, "Loading schema data from $sourceUrl")
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
            schemaCache = parsed
        }
        AppLogger.d(tag, "Parsed ${parsed.size} event schemas from $sourceUrl")
        return parsed
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

            is kotlinx.serialization.json.JsonArray -> {
                element.forEach { child -> collectSchemas(child, acc) }
            }

            else -> Unit
        }
    }

    private fun normalizeUrl(url: String): String = url.trim().trimEnd('/').lowercase()

    private fun generateWid(): String {
        fun chunk(): String = buildString {
            repeat(8) {
                append("0123456789abcdef"[Random.nextInt(16)])
            }
        }
        return chunk() + chunk()
    }

    private fun fallbackFestivals(): List<FestivalSummary> = listOf(
        FestivalSummary(
            id = 2109,
            title = "BSD-event: Арабская ночь",
            description = "Мероприятие, посвящённое аниме и манге «Великий из бродячих псов».",
            city = "Москва",
            country = "Россия",
            eventTypeId = 1,
            startTime = "2026-04-26 09:00:00",
            endTime = "2026-04-26 15:00:00",
            displayTime = "26 апреля",
            status = FestivalStatus.Future,
            url = "https://bsdevent11.cosplay2.ru",
            imageUrl = "https://cosplay2.ru/files/2109/logo.png"
        ),
        FestivalSummary(
            id = 2112,
            title = "SuperVerse",
            description = "Пати, посвященное супергеройской тематике.",
            city = "Омск",
            country = "Россия",
            eventTypeId = 1,
            startTime = "2026-05-03 00:00:00",
            endTime = "2026-05-03 17:00:00",
            displayTime = "3 мая",
            status = FestivalStatus.Future,
            url = "https://sv.cosplay2.ru",
            imageUrl = inferredLogoUrl(2112)
        ),
        FestivalSummary(
            id = 2098,
            title = "Magic Fest 2026",
            description = "Большой фестиваль косплея по любимым магическим вселенным и не только.",
            city = "Москва",
            country = "Россия",
            eventTypeId = 1,
            startTime = "2026-04-11 09:00:00",
            endTime = "2026-04-11 18:00:00",
            displayTime = "11 апреля",
            status = FestivalStatus.Future,
            url = "https://magicfest.cosplay2.ru",
            imageUrl = inferredLogoUrl(2098)
        )
    )

    private fun fallbackEventTypes(): Map<Int, String> = mapOf(
        1 to "Фестиваль",
        2 to "Вечеринка",
        3 to "Ярмарка",
        5 to "Фестиваль",
        6 to "Мероприятие",
        8 to "Конвент"
    )

    private fun inferredLogoUrl(eventId: Long): String = "https://cosplay2.ru/files/$eventId/logo.png"

    private fun buildFallbackPageContent(
        festival: FestivalSummary,
        schema: FestivalSchema?
    ): String = buildList {
        if (festival.description.isNotBlank()) {
            add(festival.description.trim())
        }

        val cityText = listOf(
            schema?.venueCity ?: festival.city,
            festival.country
        ).filter { it.isNotBlank() }.joinToString(", ")
        if (cityText.isNotBlank()) {
            add("Город: $cityText")
        }

        val dateText = listOf(
            schema?.startDate ?: festival.startTime,
            schema?.endDate ?: festival.endTime
        ).filter { it.isNotBlank() }.joinToString(" - ")
        if (dateText.isNotBlank()) {
            add("Период проведения: $dateText")
        }
    }.joinToString("\n\n")

    private data class FestivalSchema(
        val imageUrl: String?,
        val description: String,
        val startDate: String,
        val endDate: String,
        val venueCity: String,
        val venueCountryCode: String
    )

    private data class FestivalHomePageData(
        val content: String = "",
        val links: List<FestivalActionLink> = emptyList()
    )

    companion object {
        private val APPLICATION_JSON_REGEX =
            Regex("""<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
    }
}

private val ANCHOR_REGEX =
    Regex("""<a[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private fun extractReadableText(html: String, baseUrl: String): String {
    val focusedHtml = extractPrimaryContent(html)

    val withoutScripts = focusedHtml
        .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<link[^>]*>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE), " ")

    val adapted = withoutScripts
        .replace(Regex("""<a[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val href = resolveUrl(it.groupValues[1].trim(), baseUrl)
            val label = htmlToPlainText(it.groupValues[2]).trim()
            if (label.isBlank() || href.isBlank()) "" else "[[$label|$href]]"
        }
        .replace(Regex("""<(br|br/)\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<h1[^>]*>(.*?)</h1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            "\n${htmlToPlainText(it.groupValues[1])}\n\n"
        }
        .replace(Regex("""<h2[^>]*>(.*?)</h2>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val title = htmlToPlainText(it.groupValues[1]).trim().trimEnd(':')
            if (title.isBlank()) "\n" else "\n$title:\n"
        }
        .replace(Regex("""<p[^>]*>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val paragraph = htmlToPlainText(it.groupValues[1]).trim()
            if (paragraph.isBlank()) "\n" else "$paragraph\n\n"
        }
        .replace(Regex("""<li[^>]*>(.*?)</li>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val item = htmlToPlainText(it.groupValues[1]).trim()
            if (item.isBlank()) "" else "• $item\n"
        }
        .replace(Regex("""</(ul|ol|div|section|article|center)>""", RegexOption.IGNORE_CASE), "\n")

    return normalizeReadableContent(adapted)
}

private fun extractPrimaryContent(html: String): String {
    val mainContainer = Regex(
        """<div[^>]+id=["']maincontainer["'][^>]*>([\s\S]*?)</div>\s*</div>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(html)?.groupValues?.getOrNull(1)

    if (!mainContainer.isNullOrBlank()) return mainContainer

    val contentBlock = Regex(
        """<div[^>]+class=["'][^"']*content[^"']*["'][^>]*>([\s\S]*?)</div>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).find(html)?.groupValues?.getOrNull(1)

    if (!contentBlock.isNullOrBlank()) return contentBlock

    return html
}

private fun htmlToPlainText(fragment: String): String = decodeHtml(
    fragment
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""[ \t\r]+"""), " ")
        .replace(Regex("""\n\s+"""), "\n")
        .trim()
)

private fun normalizeReadableContent(raw: String): String {
    val lines = raw
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""[ \t\r]+"""), " ")
        .replace(Regex("""\n\s+"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .lines()
        .map { decodeHtml(it).trim() }
        .filter { it.isNotBlank() }

    val result = buildList {
        var previous: String? = null
        lines.forEach { line ->
            val normalized = line
                .trimEnd(':')
                .replace(Regex("""\s+"""), " ")

            if (normalized.isBlank()) return@forEach
            if (previous.equals(normalized, ignoreCase = true)) return@forEach
            if (normalized.matches(Regex("""\d{2}\.\d{2}\.\d{4}"""))) return@forEach

            add(line)
            previous = normalized
        }
    }

    return result.joinToString("\n").trim()
}

private fun extractActionLinks(html: String, baseUrl: String): List<FestivalActionLink> {
    return ANCHOR_REGEX.findAll(html)
        .mapNotNull { match ->
            val href = match.groupValues[1].trim()
            val label = htmlToPlainText(match.groupValues[2]).trim()
            if (href.isBlank() || label.isBlank()) return@mapNotNull null
            if (href.startsWith("#")) return@mapNotNull null

            FestivalActionLink(
                title = label,
                url = resolveUrl(href, baseUrl)
            )
        }
        .distinctBy { "${it.title.lowercase()}|${it.url.lowercase()}" }
        .toList()
}

private fun resolveUrl(href: String, baseUrl: String): String {
    val base = baseUrl.trimEnd('/')
    return when {
        href.isBlank() -> ""
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("/") -> "$base$href"
        else -> "$base/$href"
    }
}

private fun decodeHtml(raw: String): String = raw
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&amp;", "&")
    .replace("&laquo;", "«")
    .replace("&raquo;", "»")
    .replace("&mdash;", "—")
    .replace("&ndash;", "–")
    .replace("&nbsp;", " ")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace(Regex("""\s*\(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+00:00\)"""), "")
