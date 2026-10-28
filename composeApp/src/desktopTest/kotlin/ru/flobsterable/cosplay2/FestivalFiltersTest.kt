package ru.flobsterable.cosplay2

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue
import ru.flobsterable.cosplay2.feature.festival.FestivalsScreen
import ru.flobsterable.cosplay2.feature.festival.UiState
import ru.flobsterable.cosplay2.model.FestivalCatalog
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.network.createHttpClient

class FestivalFiltersTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun filterButtonIsRightOfSearchAndCollapsingKeepsQueryAndCategory() {
        val client = createHttpClient()
        try {
            compose.setContent {
                MaterialTheme {
                    FestivalsScreen(
                        state = UiState.Success(FestivalCatalog(
                            listOf(festival(1, 1), festival(2, 2)), emptyMap(), 2026, false
                        )),
                        currentYear = 2026, selectedYear = 2026, onYearSelected = {}, client = client,
                        onFestivalSelected = {}, onOpenSettings = {}, onOpenFavorites = {},
                        favoriteIds = emptySet(), favoritesEnabled = true, onToggleFavorite = {},
                        myCity = "", onlyMyCity = false, onCityFilterSelected = {},
                        cityEnabled = true, onChooseCity = {}
                    )
                }
            }
            val search = compose.onNode(hasSetTextAction())
            val button = compose.onNodeWithContentDescription("Показать фильтры")
            assertTrue(button.fetchSemanticsNode().boundsInRoot.left >= search.fetchSemanticsNode().boundsInRoot.right)
            compose.onNodeWithText("Все города").assertDoesNotExist()
            compose.onNodeWithText("Месяц").assertDoesNotExist()
            search.performTextInput("Омск")
            button.performClick()
            compose.onNode(
                hasText("Косплей") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
            ).performClick()
            compose.onNodeWithContentDescription("Скрыть фильтры").performClick()
            compose.onNodeWithText("Все города").assertDoesNotExist()
            compose.onNodeWithText("Найдено: 1").assertExists()
            compose.onNodeWithText("Омск").assertIsDisplayed()
            compose.onNodeWithContentDescription("Показать фильтры").performClick()
            compose.onNodeWithText("Найдено: 1").assertExists()
        } finally {
            client.close()
        }
    }

    @Test
    fun changingYearResetsMonthAfterCollapsingAndReopening() {
        val client = createHttpClient()
        var selectedYear by mutableStateOf(2026)
        try {
            compose.setContent {
                MaterialTheme {
                    FestivalsScreen(
                        state = UiState.Success(FestivalCatalog(emptyList(), emptyMap(), selectedYear, false)),
                        currentYear = 2026, selectedYear = selectedYear,
                        onYearSelected = { selectedYear = it }, client = client,
                        onFestivalSelected = {}, onOpenSettings = {}, onOpenFavorites = {},
                        favoriteIds = emptySet(), favoritesEnabled = true, onToggleFavorite = {},
                        myCity = "", onlyMyCity = false, onCityFilterSelected = {},
                        cityEnabled = true, onChooseCity = {}
                    )
                }
            }
            compose.onNodeWithContentDescription("Показать фильтры").performClick()
            compose.onNodeWithText("Все месяцы").performScrollTo().performClick()
            compose.onNodeWithText("Январь").performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            compose.onNodeWithContentDescription("Скрыть фильтры").performClick()
            compose.onNodeWithContentDescription("Показать фильтры").performClick()
            compose.onNodeWithText("Январь").assertExists()
            compose.onNodeWithText("2025").performClick()
            compose.onNodeWithText("Все месяцы").assertExists()
        } finally {
            client.close()
        }
    }

    private fun festival(id: Long, type: Int) = FestivalSummary(
        id, "Событие $id", "", "Омск", "Россия", type,
        "2026-11-01", "2026-11-02", "1 ноября", FestivalStatus.Future, "https://example.com/$id"
    )
}
