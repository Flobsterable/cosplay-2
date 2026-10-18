package ru.flobsterable.cosplay2.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertContentEquals

class FileFavoritesStorageTest {
    @Test
    fun recoveryBackupsAreSeparateAndDoNotOverwriteEachOther() {
        val directory = Files.createTempDirectory("cosplay-favorites-backup-test")
        try {
            val storage = FileFavoritesStorage(directory.resolve("favorites.json"))
            storage.write("first backup")
            storage.backup()
            assertEquals("first backup", storage.read())
            storage.write("second backup")
            storage.backup()
            assertEquals("second backup", storage.read())
            val backups = Files.list(directory).use { files -> files.filter { it.fileName.toString().startsWith("favorites-recovery-") }.toList() }
            assertEquals(setOf("first backup", "second backup"), backups.map { Files.readString(it) }.toSet())
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun invalidUtf8IsArchivedAsBytesAndCanBeReset() {
        val directory = Files.createTempDirectory("cosplay-favorites-bytes-test")
        try {
            val path = directory.resolve("favorites.json")
            val original = byteArrayOf(0xC3.toByte(), 0x28, 0xFF.toByte())
            Files.write(path, original)
            val repository = FavoritesRepository(FileFavoritesStorage(path))
            repository.reset()
            assertEquals(emptyList(), repository.load())
            val backup = Files.list(directory).use { files -> files.filter { it.fileName.toString().startsWith("favorites-recovery-") }.findFirst().orElseThrow() }
            assertContentEquals(original, Files.readAllBytes(backup))
        } finally { directory.toFile().deleteRecursively() }
    }
    @Test
    fun storedCardsCanBeReadByANewStorageInstance() {
        val directory = Files.createTempDirectory("cosplay-favorites-test")
        try {
            val path = directory.resolve("data/favorites.json")
            val storage = FileFavoritesStorage(path)
            assertNull(storage.read())
            storage.write("first")
            assertEquals("first", FileFavoritesStorage(path).read())
            storage.write("second")
            assertEquals("second", FileFavoritesStorage(path).read())
            Files.list(path.parent).use { assertEquals(1L, it.count()) }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun readErrorsAreNotTreatedAsAnEmptyCollection() {
        val directory = Files.createTempDirectory("cosplay-favorites-error-test")
        try {
            assertFails { FileFavoritesStorage(directory).read() }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
