package ru.flobsterable.cosplay2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import ru.flobsterable.cosplay2.model.FestivalCatalog

class FestivalCatalogCacheTest {
    @Test
    fun navigationReusesLoadedYearAndOtherYearsLoadSeparately() = runBlocking {
        val calls = mutableListOf<Int>()
        val cache = FestivalCatalogCache { year ->
            calls.add(year)
            FestivalCatalog(emptyList(), emptyMap(), year, false)
        }
        assertNull(cache.peek(2026))
        val first = cache.get(2026)
        assertEquals(first, cache.peek(2026))
        assertEquals(first, cache.get(2026))
        cache.get(2025)
        assertEquals(first, cache.get(2026))
        assertEquals(listOf(2026, 2025), calls)
    }

    @Test
    fun cancelledLoadPropagatesAndDoesNotPoisonCache() = runBlocking {
        var calls = 0
        val cache = FestivalCatalogCache { year ->
            if (++calls == 1) throw CancellationException("left screen")
            FestivalCatalog(emptyList(), emptyMap(), year, false)
        }
        assertFailsWith<CancellationException> { cache.get(2026) }
        assertNull(cache.peek(2026))
        assertEquals(2026, cache.get(2026).year)
        assertEquals(2, calls)
    }

    @Test
    fun demonstrationFallbackDoesNotPreventNetworkRecovery() = runBlocking {
        var calls = 0
        val cache = FestivalCatalogCache { year ->
            FestivalCatalog(emptyList(), emptyMap(), year, ++calls == 1)
        }
        assertEquals(true, cache.get(2026).isFallback)
        assertNull(cache.peek(2026))
        assertEquals(false, cache.get(2026).isFallback)
        cache.get(2026)
        assertEquals(2, calls)
    }
}
