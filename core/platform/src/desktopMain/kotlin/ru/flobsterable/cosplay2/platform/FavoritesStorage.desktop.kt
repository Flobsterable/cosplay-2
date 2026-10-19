package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Composable
actual fun rememberFavoritesStorage(): FavoritesStorage = remember {
    FileFavoritesStorage(appDataDirectory().resolve("Cosplay2/favorites.json"))
}

internal fun appDataDirectory(): Path {
    val home = Path.of(System.getProperty("user.home"))
    return when {
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) ->
            System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { Path.of(it) } ?: home.resolve("AppData/Roaming")
        System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> home.resolve("Library/Application Support")
        else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: home.resolve(".local/share")
    }
}

internal class FileFavoritesStorage(private val path: Path) : FavoritesStorage {
    override fun backup() {
        if (Files.notExists(path)) return
        val backup = Files.createTempFile(path.parent, "favorites-recovery-", ".json")
        try {
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING)
            java.nio.channels.FileChannel.open(backup, java.nio.file.StandardOpenOption.WRITE).use { it.force(true) }
        } catch (error: Exception) { Files.deleteIfExists(backup); throw error }
    }

    override fun read(): String? = try {
        Files.readString(path)
    } catch (_: NoSuchFileException) {
        null
    }

    override fun write(content: String) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, "favorites-", ".tmp")
        try {
            Files.writeString(temporary, content)
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
