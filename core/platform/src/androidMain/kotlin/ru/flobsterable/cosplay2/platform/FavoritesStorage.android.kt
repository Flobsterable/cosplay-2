package ru.flobsterable.cosplay2.platform

import android.util.AtomicFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileNotFoundException

@Composable
actual fun rememberFavoritesStorage(): FavoritesStorage {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidFavoritesStorage(File(context.filesDir, "favorites.json")) }
}

internal class AndroidFavoritesStorage(path: File) : FavoritesStorage {
    private val file = AtomicFile(path)
    private val directory = requireNotNull(path.parentFile)

    override fun backup() {
        val input = try { file.openRead() } catch (error: FileNotFoundException) {
            if (file.baseFile.exists()) throw error else return
        }
        input.use {
            val backup = File.createTempFile("favorites-recovery-", ".json", directory)
            try {
                backup.outputStream().use { stream -> input.copyTo(stream); stream.fd.sync() }
            } catch (error: Exception) { backup.delete(); throw error }
        }
    }

    override fun read(): String? = try {
        file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (error: FileNotFoundException) {
        if (file.baseFile.exists()) throw error
        null
    }

    override fun write(content: String) {
        val stream = file.startWrite()
        try {
            stream.write(content.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }
}
