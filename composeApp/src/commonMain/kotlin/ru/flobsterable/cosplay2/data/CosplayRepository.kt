package ru.flobsterable.cosplay2.data

interface CosplayRepository {
    suspend fun getFestivalCatalog(year: Int): FestivalCatalog
    suspend fun getFestivalDetail(festival: FestivalSummary): FestivalDetail
}
