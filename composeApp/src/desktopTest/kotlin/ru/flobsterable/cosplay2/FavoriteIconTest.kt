package ru.flobsterable.cosplay2

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import ru.flobsterable.cosplay2.feature.festival.FestivalDetailScreen
import ru.flobsterable.cosplay2.feature.festival.FavoritesScreen
import ru.flobsterable.cosplay2.feature.festival.FestivalsScreen
import ru.flobsterable.cosplay2.feature.festival.UiState
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalCatalog
import ru.flobsterable.cosplay2.network.createHttpClient
import kotlin.test.assertEquals

class FavoriteIconTest {
    @get:Rule val compose = createComposeRule()
    private data class Route(val id: Long)

    @Test
    fun favoriteIconRecomposesWithoutChangingTheDetailRoute() {
        val route = Route(1)
        var savedIds by mutableStateOf(emptySet<Long>())
        val client = createHttpClient()
        try {
            compose.setContent {
                // Match CosplayApp: derive an immutable set in the parent,
                // then capture it in AnimatedContent with an unchanged route.
                val favoriteIds = remember(savedIds) { savedIds.toSet() }
                MaterialTheme {
                    AnimatedContent(targetState = route) { targetRoute ->
                        FestivalDetailScreen(
                            state = UiState.Loading,
                            client = client,
                            onBack = {},
                            festivalTitle = "Фестиваль",
                            isFavorite = targetRoute.id in favoriteIds,
                            favoritesEnabled = true,
                            onToggleFavorite = {
                                savedIds = if (targetRoute.id in favoriteIds) favoriteIds - targetRoute.id
                                    else favoriteIds + targetRoute.id
                            }
                        )
                    }
                }
            }
            compose.onNodeWithContentDescription("Добавить в избранное: Фестиваль").assertIsOff().performClick()
            compose.onNodeWithContentDescription("Удалить из избранного: Фестиваль").assertIsOn().performClick()
            compose.onNodeWithContentDescription("Добавить в избранное: Фестиваль").assertIsOff()
        } finally { client.close() }
    }

    private fun festival() = FestivalSummary(1, "Тестовый фестиваль", "Описание", "Омск", "Россия", 1,
        "2026-11-01", "2026-11-02", "1–2 ноября", FestivalStatus.Future, "https://example.com")

    @Test
    fun pointerClickOnFavoriteButtonDoesNotOpenTheParentCard() {
        val client = createHttpClient()
        var toggles = 0
        var details = 0
        try {
            compose.setContent {
                MaterialTheme {
                    FavoritesScreen(UiState.Success(listOf(festival())), true, client, onBack = {}, onRetry = {},
                        onFestivalSelected = { details++ }, onToggleFavorite = { toggles++ })
                }
            }
            compose.onNodeWithContentDescription("Удалить из избранного: Тестовый фестиваль").performTouchInput { click() }
            compose.runOnIdle { assertEquals(1, toggles); assertEquals(0, details) }
            compose.onNodeWithText("Тестовый фестиваль").performTouchInput { click() }
            compose.runOnIdle { assertEquals(1, details) }
        } finally { client.close() }
    }

    @Test
    fun previousYearSuccessDoesNotRenderCardsForTheNewYear() {
        val client = createHttpClient()
        try {
            compose.setContent {
                MaterialTheme {
                    FestivalsScreen(
                        state = catalogStateForYear(UiState.Success(FestivalCatalog(listOf(festival()), emptyMap(), 2026, false)), 2025),
                        currentYear = 2026,
                        selectedYear = 2025,
                        onYearSelected = {},
                        client = client,
                        onFestivalSelected = {},
                        onOpenSettings = {},
                        onOpenFavorites = {},
                        favoriteIds = emptySet(),
                        favoritesEnabled = true,
                        onToggleFavorite = {}
                    )
                }
            }
            compose.onNodeWithText("Тестовый фестиваль").assertDoesNotExist()
        } finally { client.close() }
    }

    @Test
    fun yearControlsStayAvailableWithoutACatalogDuringLoadingAndError() {
        val client = createHttpClient()
        var state: UiState<FestivalCatalog> by mutableStateOf(UiState.Loading)
        var picked = 2026
        try {
            compose.setContent {
                MaterialTheme {
                    FestivalsScreen(state, 2026, 2026, { picked = it }, client,
                        onFestivalSelected = {}, onOpenSettings = {}, onOpenFavorites = {},
                        favoriteIds = emptySet(), favoritesEnabled = false, onToggleFavorite = {})
                }
            }
            compose.onNodeWithText("2025").performClick()
            compose.runOnIdle {
                assertEquals(2025, picked)
                state = UiState.Error("Не удалось загрузить каталог")
            }
            compose.onNodeWithText("2026").performClick()
            compose.runOnIdle { assertEquals(2026, picked) }
        } finally { client.close() }
    }

    @Test
    fun recoveryRequiresAnExplicitConfirmation() {
        val client = createHttpClient()
        var resets = 0
        try {
            compose.setContent {
                MaterialTheme {
                    FavoritesScreen(UiState.Error("Повреждённый файл"), false, client, onBack = {}, onRetry = {},
                        onFestivalSelected = {}, onToggleFavorite = {}, onReset = { resets++ })
                }
            }
            compose.onNodeWithText("Восстановить избранное").performClick()
            compose.runOnIdle { assertEquals(0, resets) }
            compose.onNodeWithText("Отмена").performClick()
            compose.runOnIdle { assertEquals(0, resets) }
            compose.onNodeWithText("Восстановить избранное").performClick()
            compose.onNodeWithText("Очистить список").performClick()
            compose.runOnIdle { assertEquals(1, resets) }
        } finally { client.close() }
    }
}
