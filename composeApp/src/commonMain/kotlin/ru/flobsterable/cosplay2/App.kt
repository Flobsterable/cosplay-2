package ru.flobsterable.cosplay2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.ktor.client.HttpClient
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import ru.flobsterable.cosplay2.data.CosplayRepositoryImpl
import ru.flobsterable.cosplay2.data.FestivalCatalog
import ru.flobsterable.cosplay2.data.FestivalDetail
import ru.flobsterable.cosplay2.data.FestivalSummary
import ru.flobsterable.cosplay2.navigation.SystemBackHandler
import ru.flobsterable.cosplay2.network.createHttpClient

@Composable
fun CosplayApp() {
    CosplayTheme {
        val client = remember { createHttpClient() }
        val repository = remember(client) { CosplayRepositoryImpl(client) }
        var selectedFestival by remember { mutableStateOf<FestivalSummary?>(null) }

        DisposableEffect(Unit) {
            onDispose {
                client.close()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            SystemBackHandler(
                enabled = selectedFestival != null,
                onBack = { selectedFestival = null }
            )
            AnimatedContent(
                targetState = selectedFestival,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(450)) + slideInVertically(
                        initialOffsetY = { it / 10 },
                        animationSpec = tween(450, easing = FastOutSlowInEasing)
                    )).togetherWith(fadeOut(animationSpec = tween(250)))
                },
                label = "festival_navigation"
            ) { festival ->
                if (festival == null) {
                    FestivalsRoute(
                        repository = repository,
                        client = client,
                        onFestivalSelected = { selectedFestival = it }
                    )
                } else {
                    FestivalDetailRoute(
                        repository = repository,
                        client = client,
                        festival = festival,
                        onBack = { selectedFestival = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun FestivalsRoute(
    repository: CosplayRepositoryImpl,
    client: HttpClient,
    onFestivalSelected: (FestivalSummary) -> Unit
) {
    val currentYear = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()).year }
    var selectedYear by rememberSaveable { mutableStateOf(ACTUAL_FILTER_YEAR) }
    var lastSuccessfulCatalog by remember { mutableStateOf<FestivalCatalog?>(null) }
    val requestedYear = if (selectedYear == ACTUAL_FILTER_YEAR) currentYear else selectedYear
    val state by produceState<UiState<FestivalCatalog>>(initialValue = UiState.Loading, selectedYear) {
        value = UiState.Loading
        value = runCatching { repository.getFestivalCatalog(requestedYear) }
            .fold(
                onSuccess = { UiState.Success(it.copy(festivals = it.festivals.sortedBy { festival -> festival.startTime })) },
                onFailure = { UiState.Error(it.message ?: "Не удалось загрузить фестивали") }
            )
    }

    val currentState = state

    if (currentState is UiState.Success) {
        lastSuccessfulCatalog = currentState.data
    }

    val isYearLoading = currentState is UiState.Loading && lastSuccessfulCatalog != null

    FestivalsScreen(
        state = currentState,
        catalogSnapshot = lastSuccessfulCatalog,
        currentYear = currentYear,
        selectedYear = selectedYear,
        onYearSelected = { selectedYear = it },
        isYearLoading = isYearLoading,
        client = client,
        onFestivalSelected = onFestivalSelected
    )
}

@Composable
private fun FestivalDetailRoute(
    repository: CosplayRepositoryImpl,
    client: HttpClient,
    festival: FestivalSummary,
    onBack: () -> Unit
) {
    val state by produceState<UiState<FestivalDetail>>(initialValue = UiState.Loading, festival.id) {
        value = runCatching { repository.getFestivalDetail(festival) }
            .fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Не удалось открыть карточку фестиваля") }
            )
    }

    FestivalDetailScreen(
        state = state,
        client = client,
        onBack = onBack
    )
}
