package ru.flobsterable.cosplay2

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import ru.flobsterable.cosplay2.feature.festival.FestivalDetailScreen
import ru.flobsterable.cosplay2.feature.festival.UiState
import ru.flobsterable.cosplay2.network.createHttpClient

class FavoriteIconTest {
    @get:Rule val compose = createComposeRule()
    private data class Route(val id: Long)

    @Test
    fun favoriteIconRecomposesWithoutChangingTheDetailRoute() {
        val route = Route(1)
        var favoriteIds by mutableStateOf(emptySet<Long>())
        val client = createHttpClient()
        try {
            compose.setContent {
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
                                favoriteIds = if (targetRoute.id in favoriteIds) favoriteIds - targetRoute.id
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
}
