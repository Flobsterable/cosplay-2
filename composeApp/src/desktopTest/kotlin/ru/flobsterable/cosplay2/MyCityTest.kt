package ru.flobsterable.cosplay2

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.flobsterable.cosplay2.platform.FavoritesStorage
import ru.flobsterable.cosplay2.platform.matchesMyCity

class MyCityTest {
    private class Storage : FavoritesStorage {
        var content: String? = null
        var failWrite = false
        override fun read() = content
        override fun write(content: String) {
            check(!failWrite)
            this.content = content
        }
        override fun backup() = Unit
    }

    @Test
    fun citySurvivesStateRecreationAndCanBeCleared() = runBlocking {
        val storage = Storage()
        val first = MyCityState(storage)
        first.load()
        assertEquals("", first.city)
        first.save("  Нижний   Новгород  ")
        val reopened = MyCityState(storage)
        reopened.load()
        assertEquals("Нижний Новгород", reopened.city)
        reopened.save("")
        val cleared = MyCityState(storage)
        cleared.load()
        assertEquals("", cleared.city)
    }

    @Test
    fun failedSaveKeepsSavedCityAndAllowsRetry() = runBlocking {
        val storage = Storage()
        val state = MyCityState(storage)
        state.load()
        state.save("Омск")
        storage.failWrite = true
        assertFailsWith<IllegalStateException> { state.save("Москва") }
        assertEquals("Омск", state.city)
        assertEquals("Омск", storage.content)
        assertFalse(state.isSaving)
        storage.failWrite = false
        state.save("Москва")
        assertEquals("Москва", state.city)
    }

    @Test
    fun cityMatchingIgnoresCaseAndWhitespaceButDoesNotMatchPartOfAnotherCity() {
        assertTrue(matchesMyCity(" нижний   НОВГОРОД ", "Нижний Новгород"))
        assertFalse(matchesMyCity("Нижний Новгород", "Новгород"))
        assertFalse(matchesMyCity("", ""))
        assertFalse(matchesMyCity("Омск", "Москва"))
    }
}
