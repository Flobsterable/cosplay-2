package ru.flobsterable.cosplay2.data.festival

import kotlin.random.Random
import ru.flobsterable.cosplay2.model.FestivalCatalog
import ru.flobsterable.cosplay2.model.FestivalDetail
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.platform.AppLogger

class CosplayRepositoryImpl(
    private val api: CosplayApi
) : CosplayRepository {

    private var festivalCache: Map<String, FestivalSummary> = emptyMap()
    private val tag = "CosplayRepo"

    override suspend fun getFestivalCatalog(year: Int): FestivalCatalog {
        val wid = generateWid()
        AppLogger.d(tag, "Loading archive page https://cosplay2.ru/archive/$year")
        val schemasByUrl = runCatching { api.fetchSchemas("https://cosplay2.ru/archive/$year") }
            .onFailure { AppLogger.e(tag, "Failed to load schema data from archive/$year", it) }
            .getOrElse { emptyMap() }

        AppLogger.d(tag, "Loading festivals from /api/events/filter_list with wid=$wid and active_year=$year")
        val response = runCatching { api.fetchFestivalList(year, wid) }.getOrElse {
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
        val types = runCatching { api.fetchEventTypes() }.getOrElse {
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
            if (year != null) api.fetchSchemas("https://cosplay2.ru/archive/$year")[normalizeUrl(festival.url)]
            else api.fetchSchemas("https://cosplay2.ru/")[normalizeUrl(festival.url)]
        }.getOrNull()
        val pageData = runCatching { api.fetchFestivalHomePage(festival.url) }
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

}
