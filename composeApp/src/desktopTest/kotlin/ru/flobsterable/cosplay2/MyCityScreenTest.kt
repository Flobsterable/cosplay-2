package ru.flobsterable.cosplay2

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import ru.flobsterable.cosplay2.feature.festival.FestivalsScreen
import ru.flobsterable.cosplay2.feature.festival.UiState
import ru.flobsterable.cosplay2.model.FestivalCatalog
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.network.createHttpClient

class MyCityScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun festival(id: Long, city: String) = FestivalSummary(
        id, "Фестиваль $city", "", city, "Россия", 1,
        "2026-11-01", "2026-11-02", "1 ноября", FestivalStatus.Future, "https://example.com/$id"
    )

    @Test
    fun savedCityFiltersCatalogAndAllCitiesRestoresOtherEvents() {
        val client = createHttpClient()
        var onlyMyCity by mutableStateOf(true)
        try {
            compose.setContent {
                MaterialTheme {
                    FestivalsScreen(
                        UiState.Success(FestivalCatalog(listOf(festival(1, "Омск"), festival(2, "Москва")), emptyMap(), 2026, false)),
                        2026, 2026, {}, client,
                        onFestivalSelected = {}, onOpenSettings = {}, onOpenFavorites = {},
                        favoriteIds = emptySet(), favoritesEnabled = true, onToggleFavorite = {},
                        myCity = "Омск", onlyMyCity = onlyMyCity, onCityFilterSelected = { onlyMyCity = it },
                        cityEnabled = true, onChooseCity = {}
                    )
                }
            }
            compose.onNodeWithText("Фестиваль Москва").assertDoesNotExist()
            compose.onNodeWithText("Найдено: 1").assertExists()
            compose.onNodeWithText("Фестиваль Омск").performScrollTo().assertExists()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Все города"))
            compose.onNodeWithText("Все города").performScrollTo().performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Фестиваль Москва"))
            compose.onNodeWithText("Фестиваль Москва").assertExists()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Мой город: Омск"))
            compose.onNodeWithText("Мой город: Омск").performScrollTo().performClick()
            compose.onNodeWithText("Фестиваль Москва").assertDoesNotExist()
        } finally {
            client.close()
        }
    }
}
