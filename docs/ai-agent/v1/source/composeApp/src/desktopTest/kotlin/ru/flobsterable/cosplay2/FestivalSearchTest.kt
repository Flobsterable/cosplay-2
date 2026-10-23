package ru.flobsterable.cosplay2

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.flobsterable.cosplay2.feature.festival.FestivalSearch
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary

class FestivalSearchTest {
    private val search = FestivalSearch()

    @Test
    fun findsCyrillicInTitleAndCityIgnoringCase() {
        val titleMatch = festival(1, "КОСМИЧЕСКИЙ фестиваль", "Томск")
        val cityMatch = festival(2, "Летний бал", "ОМСК")
        val festivals = listOf(titleMatch, cityMatch)

        assertEquals(listOf(titleMatch), search.search(festivals, "космический"))
        assertEquals(listOf(cityMatch), search.search(festivals, "омск"))
    }

    @Test
    fun allWordsMayMatchAcrossTitleAndCityInAnyOrder() {
        val match = festival(1, "Аниме Фестиваль", "Омск")
        val titleOnly = festival(2, "Аниме фестиваль", "Томск")
        val cityOnly = festival(3, "Концерт", "Омск")

        assertEquals(listOf(match), search.search(listOf(match, titleOnly, cityOnly), "ОМСК аниме"))
    }

    @Test
    fun splitsRepeatedAndUnicodeWhitespace() {
        val match = festival(1, "Аниме фестиваль", "Омск")
        val other = festival(2, "Аниме фестиваль", "Томск")

        assertEquals(listOf(match), search.search(listOf(match, other), " \tАниме\n\u00A0ОМСК\r "))
    }

    @Test
    fun blankQueryReturnsAllAndEmptyInputReturnsEmpty() {
        val festivals = listOf(festival(1, "Первый", "Омск"), festival(2, "Второй", "Томск"))

        assertEquals(festivals, search.search(festivals, ""))
        assertEquals(festivals, search.search(festivals, " \t\n\u00A0 "))
        assertEquals(emptyList(), search.search(emptyList(), "Омск"))
        assertEquals(emptyList(), search.search(emptyList(), "  "))
    }

    @Test
    fun ignoresDescriptionAndReturnsNoMatchesWhenAWordIsMissing() {
        val festival = festival(1, "Аниме", "Омск", description = "Косплей")

        assertEquals(emptyList(), search.search(listOf(festival), "косплей"))
        assertEquals(emptyList(), search.search(listOf(festival), "аниме Москва"))
    }

    @Test
    fun preservesOrderAndDoesNotModifyInput() {
        val first = festival(3, "Аниме", "Омск")
        val excluded = festival(2, "Концерт", "Москва")
        val second = festival(1, "Аниме", "Томск")
        val festivals = mutableListOf(first, excluded, second)
        val original = festivals.toList()

        assertEquals(listOf(first, second), search.search(festivals, "аниме"))
        assertEquals(original, festivals)
    }

    private fun festival(id: Long, title: String, city: String, description: String = "") = FestivalSummary(
        id = id,
        title = title,
        description = description,
        city = city,
        country = "Россия",
        eventTypeId = 1,
        startTime = "2026-11-01",
        endTime = "2026-11-02",
        displayTime = "1–2 ноября",
        status = FestivalStatus.Future,
        url = "https://example.com/$id"
    )
}
