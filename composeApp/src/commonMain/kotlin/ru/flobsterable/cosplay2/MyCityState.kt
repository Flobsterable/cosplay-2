package ru.flobsterable.cosplay2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.flobsterable.cosplay2.platform.FavoritesStorage
import ru.flobsterable.cosplay2.platform.normalizeCity

internal class MyCityState(private val storage: FavoritesStorage) {
    var city by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isSaving by mutableStateOf(false)
        private set
    private val mutex = Mutex()

    suspend fun load() = mutex.withLock {
        try {
            city = withContext(Dispatchers.IO) { normalizeCity(storage.read().orEmpty()) }
        } finally {
            isLoading = false
        }
    }

    suspend fun save(value: String) = mutex.withLock {
        isSaving = true
        try {
            val normalized = normalizeCity(value)
            withContext(Dispatchers.IO) { storage.write(normalized) }
            city = normalized
        } finally {
            isSaving = false
        }
    }
}
