package ru.flobsterable.cosplay2

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.flobsterable.cosplay2.model.FestivalCatalog

// Keep the active year's catalog across screen navigation; selecting another
// year explicitly invalidates it so the year filter always reloads server data.
internal class FestivalCatalogCache(private val load: suspend (Int) -> FestivalCatalog) {
    private val catalogs = mutableMapOf<Int, FestivalCatalog>()
    private val mutex = Mutex()
    private var selectedYear: Int? = null

    fun selectYear(year: Int) {
        if (selectedYear != null && selectedYear != year) catalogs.clear()
        selectedYear = year
    }

    fun peek(year: Int): FestivalCatalog? = catalogs[year]

    suspend fun get(year: Int): FestivalCatalog = mutex.withLock {
        catalogs[year] ?: load(year).let { catalog ->
            catalog.copy(festivals = catalog.festivals.sortedBy { it.startTime }).also {
                if (!it.isFallback) catalogs[year] = it
            }
        }
    }

}
