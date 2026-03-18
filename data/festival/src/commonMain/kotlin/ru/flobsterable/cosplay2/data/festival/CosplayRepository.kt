package ru.flobsterable.cosplay2.data.festival

import ru.flobsterable.cosplay2.model.FestivalCatalog
import ru.flobsterable.cosplay2.model.FestivalDetail
import ru.flobsterable.cosplay2.model.FestivalSummary

interface CosplayRepository {
    suspend fun getFestivalCatalog(year: Int): FestivalCatalog
    suspend fun getFestivalDetail(festival: FestivalSummary): FestivalDetail
}
