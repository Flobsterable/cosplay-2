package ru.flobsterable.cosplay2

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.flobsterable.cosplay2.feature.festival.favoriteFestivalSummary
import ru.flobsterable.cosplay2.model.FestivalDetail
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary

class FavoriteFestivalSummaryTest {
    @Test
    fun savedCardUsesDetailsShownOnScreenAndRetainsIdentityAndType() {
        val original = FestivalSummary(1, "Фестиваль", "Старое описание", "Старый город", "Россия", 99,
            "2026-01-01", "2026-01-02", "Старые даты", FestivalStatus.Future, "https://example.com", "old.png", "Особый тип")
        val detail = FestivalDetail(original, "Новое описание", "", emptyList(), "Омск", "RU",
            "2026-11-01", "2026-11-02", "new.png")
        val saved = favoriteFestivalSummary(detail)
        assertEquals(original.id, saved.id)
        assertEquals(original.url, saved.url)
        assertEquals(original.eventTypeTitle, saved.eventTypeTitle)
        assertEquals(detail.description, saved.description)
        assertEquals(detail.venueCity, saved.city)
        assertEquals(detail.startDate, saved.startTime)
        assertEquals(detail.endDate, saved.endTime)
        assertEquals(detail.imageUrl, saved.imageUrl)
        assertEquals("1-2 ноября 2026", saved.displayTime)
    }
}
