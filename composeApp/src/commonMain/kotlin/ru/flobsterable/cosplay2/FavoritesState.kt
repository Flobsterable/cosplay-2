package ru.flobsterable.cosplay2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.flobsterable.cosplay2.feature.festival.UiState
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.platform.FavoritesRepository

internal class FavoritesState(private val repository: FavoritesRepository) {
    var state: UiState<List<FestivalSummary>> by mutableStateOf(UiState.Loading)
        private set
    var error: String? by mutableStateOf(null)
        private set
    private val mutex = Mutex()

    suspend fun load() = mutex.withLock {
        state = UiState.Loading
        try {
            state = UiState.Success(withContext(Dispatchers.IO) { repository.load() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state = UiState.Error("Не удалось загрузить избранное. Попробуйте снова.")
        }
    }

    suspend fun toggle(festival: FestivalSummary) = mutex.withLock {
        val current = (state as? UiState.Success)?.data ?: return@withLock
        val updated = if (current.any { it.id == festival.id }) current.filterNot { it.id == festival.id }
            else current + festival
        try {
            withContext(Dispatchers.IO) { repository.save(updated) }
            state = UiState.Success(updated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Не удалось сохранить избранное. Попробуйте ещё раз."
        }
    }

    fun clearError() { error = null }
}
