package ru.flobsterable.cosplay2.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary

class FavoritesRepositoryTest {
    private class MemoryStorage(var content: String? = null) : FavoritesStorage {
        override fun read() = content
        override fun write(content: String) { this.content = content }
    }

    private fun festival(id: Long) = FestivalSummary(id, "Фестиваль ★ $id", "Описание", "Омск", "RU", 1,
        "2026-11-01", "2026-11-02", "1–2 ноября", FestivalStatus.Future, "https://example.com/$id", null)

    @Test
    fun missingStorageIsEmpty() {
        assertEquals(emptyList(), FavoritesRepository(MemoryStorage()).load())
    }

    @Test
    fun cardsSurviveRepositoryRecreationAndRemoval() {
        val storage = MemoryStorage()
        FavoritesRepository(storage).save(listOf(festival(1), festival(2)))
        assertEquals(listOf(festival(1), festival(2)), FavoritesRepository(storage).load())
        FavoritesRepository(storage).save(listOf(festival(2)))
        assertEquals(listOf(festival(2)), FavoritesRepository(storage).load())
        FavoritesRepository(storage).save(emptyList())
        assertEquals(emptyList(), FavoritesRepository(storage).load())
    }

    @Test
    fun duplicateIdsAreNotSavedTwice() {
        val storage = MemoryStorage()
        FavoritesRepository(storage).save(listOf(festival(1), festival(1)))
        assertEquals(listOf(festival(1)), FavoritesRepository(storage).load())
    }

    @Test
    fun corruptedOrFutureDataIsNotSilentlyReplacedWithEmptyList() {
        for (content in listOf("broken json", "{\"version\":2,\"festivals\":[]}")) {
            val storage = MemoryStorage(content)
            assertFails { FavoritesRepository(storage).load() }
            assertEquals(content, storage.content)
        }
    }
}
