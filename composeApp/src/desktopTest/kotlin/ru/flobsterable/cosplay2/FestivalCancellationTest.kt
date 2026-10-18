package ru.flobsterable.cosplay2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import ru.flobsterable.cosplay2.data.festival.CosplayApi
import ru.flobsterable.cosplay2.data.festival.CosplayRepositoryImpl
import ru.flobsterable.cosplay2.data.festival.FestivalHomePageData
import ru.flobsterable.cosplay2.data.festival.FestivalSchema
import ru.flobsterable.cosplay2.model.EventTypeDto
import ru.flobsterable.cosplay2.model.FestivalListResponse
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary

class FestivalCancellationTest {
    private class Api(private val cancelledStage: String) : CosplayApi {
        private fun check(stage: String) { if (stage == cancelledStage) throw CancellationException(stage) }
        override suspend fun fetchFestivalList(year: Int, wid: String): FestivalListResponse {
            check("list"); return FestivalListResponse(emptyList())
        }
        override suspend fun fetchEventTypes(): List<EventTypeDto> {
            check("types"); return emptyList()
        }
        override suspend fun fetchSchemas(sourceUrl: String): Map<String, FestivalSchema> {
            check("schemas"); return emptyMap()
        }
        override suspend fun fetchFestivalHomePage(baseUrl: String): FestivalHomePageData {
            check("page"); return FestivalHomePageData()
        }
    }

    @Test
    fun cancellationAtEveryCatalogRequestPropagatesInsteadOfReturningFallback() = runBlocking {
        for (stage in listOf("schemas", "list", "types")) {
            assertFailsWith<CancellationException> { CosplayRepositoryImpl(Api(stage)).getFestivalCatalog(2026) }
        }
    }

    @Test
    fun cancellationAtEveryDetailRequestPropagates() = runBlocking {
        val festival = FestivalSummary(1, "Фестиваль", "", "", "", 1, "2026-11-01", "2026-11-02", "",
            FestivalStatus.Future, "https://example.com")
        for (stage in listOf("schemas", "page")) {
            assertFailsWith<CancellationException> { CosplayRepositoryImpl(Api(stage)).getFestivalDetail(festival) }
        }
    }
}
