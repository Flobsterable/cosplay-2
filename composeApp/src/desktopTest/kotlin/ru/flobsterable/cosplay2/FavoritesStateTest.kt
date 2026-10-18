package ru.flobsterable.cosplay2

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import ru.flobsterable.cosplay2.feature.festival.UiState
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.platform.FavoritesRepository
import ru.flobsterable.cosplay2.platform.FavoritesStorage

class FavoritesStateTest {
    private class Storage : FavoritesStorage {
        var content: String? = null
        var failWrite = false
        override fun read() = content
        override fun write(content: String) {
            check(!failWrite)
            this.content = content
        }
    }
    private fun festival(id: Long) = FestivalSummary(id, "Фестиваль $id", "", "", "", 1,
        "2026-11-01", "2026-11-02", "", FestivalStatus.Future, "https://example.com/$id")

    @Test
    fun rapidChangesAreSerializedAndPersisted() = runBlocking {
        val storage = Storage()
        val state = FavoritesState(FavoritesRepository(storage))
        state.load()
        (List(10) { launch { state.toggle(festival(1)) } } + launch { state.toggle(festival(2)) }).joinAll()
        assertEquals(listOf(festival(2)), (state.state as UiState.Success).data)
        assertEquals(listOf(festival(2)), FavoritesRepository(storage).load())
    }

    @Test
    fun failedWriteKeepsPreviouslySavedStateAndAllowsRetry() = runBlocking {
        val storage = Storage()
        val state = FavoritesState(FavoritesRepository(storage))
        state.load()
        state.toggle(festival(1))
        storage.failWrite = true
        state.toggle(festival(2))
        assertNotNull(state.error)
        assertEquals(listOf(festival(1)), (state.state as UiState.Success).data)
        assertEquals(listOf(festival(1)), FavoritesRepository(storage).load())
        storage.failWrite = false
        state.toggle(festival(2))
        assertEquals(listOf(festival(1), festival(2)), FavoritesRepository(storage).load())
    }

    @Test
    fun corruptStorageBlocksChangesUntilLoadSucceeds() = runBlocking {
        val storage = Storage().apply { content = "corrupt" }
        val state = FavoritesState(FavoritesRepository(storage))
        state.load()
        assertTrue(state.state is UiState.Error)
        state.toggle(festival(1))
        assertEquals("corrupt", storage.content)
        storage.content = null
        state.load()
        assertTrue(state.state is UiState.Success)
    }
}
