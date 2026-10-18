package ru.flobsterable.cosplay2

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.flobsterable.cosplay2.feature.festival.UiState
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.platform.FavoritesRepository
import ru.flobsterable.cosplay2.platform.FavoritesStorage

class FavoritesStateTest {
    private class Storage : FavoritesStorage {
        var content: String? = null
        var backupContent: String? = null
        override fun backup() { backupContent = content }
        var failWrite = false
        var readEntered: CompletableDeferred<Unit>? = null
        var readGate: CountDownLatch? = null
        var writeEntered: CompletableDeferred<Unit>? = null
        var writeGate: CountDownLatch? = null
        override fun read(): String? {
            readEntered?.complete(Unit)
            readGate?.let { check(it.await(5, TimeUnit.SECONDS)) }
            return content
        }
        override fun write(content: String) {
            check(!failWrite)
            writeEntered?.complete(Unit)
            writeGate?.let { check(it.await(5, TimeUnit.SECONDS)) }
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
        assertFalse(withTimeout(1000) { state.errors.first() }.retryLoad)
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
        assertTrue(withTimeout(1000) { state.errors.first() }.retryLoad)
        state.toggle(festival(1))
        assertEquals("corrupt", storage.content)
        storage.content = null
        state.load()
        assertTrue(state.state is UiState.Success)
    }

    @Test
    fun repeatedEqualErrorsAreQueuedSeparately() = runBlocking {
        val storage = Storage().apply { failWrite = true }
        val state = FavoritesState(FavoritesRepository(storage))
        state.load()
        state.toggle(festival(1))
        state.toggle(festival(1))
        val first = withTimeout(1000) { state.errors.first() }
        val second = withTimeout(1000) { state.errors.first() }
        assertEquals(first, second)
    }

    @Test
    fun directToggleBeforeLoadingExplainsHowToRecover() = runBlocking {
        val storage = Storage()
        val state = FavoritesState(FavoritesRepository(storage))
        state.toggle(festival(1))
        assertTrue(withTimeout(1000) { state.errors.first() }.retryLoad)
        assertEquals(null, storage.content)
    }

    @Test
    fun explicitResetRecoversFromCorruptionAndAllowsSavingAgain() = runBlocking {
        val storage = Storage().apply { content = "corrupt data" }
        val state = FavoritesState(FavoritesRepository(storage))
        state.load()
        assertTrue(state.state is UiState.Error)
        assertTrue(state.reset())
        assertEquals("corrupt data", storage.backupContent)
        assertTrue(state.canModify)
        state.toggle(festival(1))
        assertEquals(listOf(festival(1)), FavoritesRepository(storage).load())
    }

    @Test
    fun reloadKeepsCardsVisibleAndDoesNotLoseAQueuedToggle() = runBlocking {
        val storage = Storage()
        val state = FavoritesState(FavoritesRepository(storage))
        state.load()
        state.toggle(festival(1))
        storage.readEntered = CompletableDeferred()
        val gate = CountDownLatch(1)
        storage.readGate = gate
        val reload = launch { state.load() }
        try {
            withTimeout(3000) { storage.readEntered!!.await() }
            assertEquals(listOf(festival(1)), (state.state as UiState.Success).data)
            assertTrue(state.canModify)
            val toggle = launch { state.toggle(festival(2)) }
            gate.countDown()
            reload.join()
            toggle.join()
            assertEquals(listOf(festival(1), festival(2)), (state.state as UiState.Success).data)
        } finally { gate.countDown() }
    }

    @Test
    fun failedReloadRetainsLastSuccessfulCards() = runBlocking {
        val storage = Storage()
        val state = FavoritesState(FavoritesRepository(storage))
        state.load()
        state.toggle(festival(1))
        storage.content = "corrupt"
        state.load()
        assertEquals(listOf(festival(1)), (state.state as UiState.Success).data)
        assertTrue(withTimeout(1000) { state.errors.first() }.retryLoad)
    }

    @Test
    fun buttonsAreUnavailableOnlyWhileAWriteIsPending() = runBlocking {
        val storage = Storage()
        val state = FavoritesState(FavoritesRepository(storage))
        state.load()
        storage.writeEntered = CompletableDeferred()
        val gate = CountDownLatch(1)
        storage.writeGate = gate
        val save = launch { state.toggle(festival(1)) }
        try {
            withTimeout(3000) { storage.writeEntered!!.await() }
            assertFalse(state.canModify)
            gate.countDown()
            save.join()
            assertTrue(state.canModify)
        } finally { gate.countDown() }
    }
}
