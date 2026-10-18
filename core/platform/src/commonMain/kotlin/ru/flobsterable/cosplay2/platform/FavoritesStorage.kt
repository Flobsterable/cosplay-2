package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.flobsterable.cosplay2.model.FestivalSummary

interface FavoritesStorage {
    fun read(): String?
    fun write(content: String)
}

@Composable
expect fun rememberFavoritesStorage(): FavoritesStorage

// Blocking storage operations; callers run these off the UI thread.
class FavoritesRepository(private val storage: FavoritesStorage) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<FestivalSummary> {
        val content = storage.read() ?: return emptyList()
        val stored = json.decodeFromString<StoredFavorites>(content)
        check(stored.version == 1) { "Unsupported favorites format" }
        return stored.festivals.distinctBy { it.id }
    }

    fun save(festivals: List<FestivalSummary>) {
        storage.write(json.encodeToString(StoredFavorites(festivals = festivals.distinctBy { it.id })))
    }
}

@Serializable
private data class StoredFavorites(val version: Int = 1, val festivals: List<FestivalSummary>)
