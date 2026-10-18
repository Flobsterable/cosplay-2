package ru.flobsterable.cosplay2.platform

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class FileFavoritesStorageTest {
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
