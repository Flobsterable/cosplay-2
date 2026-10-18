package ru.flobsterable.cosplay2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.flobsterable.cosplay2.feature.festival.UiState
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.platform.FavoritesRepository

internal class FavoritesState(private val repository: FavoritesRepository) {
    var state: UiState<List<FestivalSummary>> by mutableStateOf(UiState.Loading)
        private set
    var isSaving: Boolean by mutableStateOf(false)
        private set
    val canModify: Boolean get() = state is UiState.Success && !isSaving
    private val notifications = Channel<FavoritesError>(Channel.UNLIMITED)
    val errors = notifications.receiveAsFlow()
    private val mutex = Mutex()

    suspend fun load() = mutex.withLock {
        val previous = state as? UiState.Success
        if (previous == null) state = UiState.Loading
        try {
            state = UiState.Success(withContext(Dispatchers.IO) { repository.load() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val message = "Не удалось загрузить избранное. Попробуйте снова."
            state = previous ?: UiState.Error(message)
            notifications.trySend(FavoritesError(message, retryLoad = true))
        }
    }

    suspend fun toggle(festival: FestivalSummary) = mutex.withLock {
        val current = (state as? UiState.Success)?.data
        if (current == null) {
            notifications.trySend(FavoritesError("Избранное не загружено. Повторите загрузку перед изменением списка.", retryLoad = true))
            return@withLock
        }
        val updated = if (current.any { it.id == festival.id }) current.filterNot { it.id == festival.id }
            else current + festival
        isSaving = true
        try {
            withContext(Dispatchers.IO) { repository.save(updated) }
            state = UiState.Success(updated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            notifications.trySend(FavoritesError("Не удалось сохранить избранное. Попробуйте ещё раз."))
        } finally {
            isSaving = false
        }
    }

    suspend fun reset(): Boolean = mutex.withLock {
        isSaving = true
        try {
            withContext(Dispatchers.IO) { repository.reset() }
            state = UiState.Success(emptyList())
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            notifications.trySend(FavoritesError("Не удалось восстановить избранное. Исходные данные сохранены."))
            false
        } finally { isSaving = false }
    }
}

internal data class FavoritesError(val message: String, val retryLoad: Boolean = false)
