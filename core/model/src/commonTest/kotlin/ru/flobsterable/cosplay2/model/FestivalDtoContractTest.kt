package ru.flobsterable.cosplay2.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FestivalDtoContractTest {
    @Test
    fun catalogDecodesServerFieldNamesAndOptionalDefaults() {
        val response = Json.decodeFromString<FestivalListResponse>(
            """{"events":[{"id":42,"event_type_id":8,"start_time":"2026-11-01",
                "end_time":"2026-11-02","title":"Festival","href":"https://example.test",
                "time":"1–2 ноября","timestatus":"future"}]}"""
        )

        val festival = response.events.single()
        assertEquals(42L, festival.id)
        assertEquals(8, festival.eventTypeId)
        assertEquals("2026-11-01", festival.startTime)
        assertEquals("2026-11-02", festival.endTime)
        assertEquals("", festival.description)
        assertEquals("", festival.city)
        assertEquals("", festival.country)
    }

    @Test
    fun missingRequiredCatalogFieldsAreNotAcceptedAsEmptyCatalog() {
        assertFailsWith<SerializationException> {
            Json.decodeFromString<FestivalListResponse>("""{"events":[{"id":42}]}""")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString<FestivalListResponse>("{}")
        }
    }

    @Test
    fun emptyServerCatalogIsValid() {
        assertEquals(emptyList(), Json.decodeFromString<FestivalListResponse>("""{"events":[]}""").events)
    }

    @Test
    fun onlyFutureStatusIsTreatedAsUpcoming() {
        assertEquals(FestivalStatus.Future, FestivalStatus.fromRaw("FuTuRe"))
        listOf("past", "", "unknown").forEach { status ->
            assertEquals(FestivalStatus.Past, FestivalStatus.fromRaw(status))
        }
    }

    @Test
    fun releaseWithoutAssetsStillProvidesReleasePage() {
        val release = Json.decodeFromString<GitHubReleaseDto>(
            """{"tag_name":"v2.0","html_url":"https://example.test/releases/v2.0"}"""
        )
        assertEquals("v2.0", release.tagName)
        assertEquals("https://example.test/releases/v2.0", release.htmlUrl)
        assertEquals(emptyList(), release.assets)
        assertEquals("", release.body)
    }
}
