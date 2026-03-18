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
import ru.flobsterable.cosplay2.data.festival.CosplayApiImpl
import ru.flobsterable.cosplay2.data.festival.CosplayRepositoryImpl
import ru.flobsterable.cosplay2.data.update.GitHubReleaseApiImpl
import ru.flobsterable.cosplay2.data.update.GitHubUpdateRepository
import ru.flobsterable.cosplay2.feature.festival.ACTUAL_FILTER_YEAR
import ru.flobsterable.cosplay2.feature.festival.FestivalDetailScreen
import ru.flobsterable.cosplay2.feature.festival.FestivalsScreen
import ru.flobsterable.cosplay2.feature.festival.UiState
import ru.flobsterable.cosplay2.feature.update.UpdateBanner
import ru.flobsterable.cosplay2.feature.update.UpdateScreen
import ru.flobsterable.cosplay2.feature.update.updateBannerTitle
import ru.flobsterable.cosplay2.feature.update.isNewerVersion
import ru.flobsterable.cosplay2.model.AppUpdateInfo
import ru.flobsterable.cosplay2.model.FestivalCatalog
import ru.flobsterable.cosplay2.model.FestivalDetail
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.network.createHttpClient
import ru.flobsterable.cosplay2.platform.AppUpdateLauncher
import ru.flobsterable.cosplay2.platform.AppVersion
import ru.flobsterable.cosplay2.platform.rememberAppUpdateLauncher
import ru.flobsterable.cosplay2.platform.SystemBackHandler

private sealed interface AppRoute {
    data object Festivals : AppRoute
    data class FestivalDetails(val festival: FestivalSummary) : AppRoute
    data object Update : AppRoute
}

@Composable
fun CosplayApp() {
    CosplayTheme {
        val client = remember { createHttpClient() }
        val repository = remember(client) { CosplayRepositoryImpl(CosplayApiImpl(client)) }
        val updateRepository = remember(client) {
            GitHubUpdateRepository(
                api = GitHubReleaseApiImpl(client),
                owner = "Flobsterable",
                repo = "cosplay-2"
            )
        }
        val appUpdateLauncher = rememberAppUpdateLauncher()
        var route by remember { mutableStateOf<AppRoute>(AppRoute.Festivals) }

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
                enabled = route != AppRoute.Festivals,
                onBack = { route = AppRoute.Festivals }
            )
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(450)) + slideInVertically(
                        initialOffsetY = { it / 10 },
                        animationSpec = tween(450, easing = FastOutSlowInEasing)
                    )).togetherWith(fadeOut(animationSpec = tween(250)))
                },
                label = "festival_navigation"
            ) { targetRoute ->
                when (targetRoute) {
                    AppRoute.Festivals -> FestivalsRoute(
                        repository = repository,
                        updateRepository = updateRepository,
                        client = client,
                        appUpdateLauncher = appUpdateLauncher,
                        onOpenUpdate = { route = AppRoute.Update },
                        onFestivalSelected = { route = AppRoute.FestivalDetails(it) }
                    )
                    is AppRoute.FestivalDetails -> FestivalDetailRoute(
                        repository = repository,
                        client = client,
                        festival = targetRoute.festival,
                        onBack = { route = AppRoute.Festivals }
                    )
                    AppRoute.Update -> UpdateRoute(
                        updateRepository = updateRepository,
                        appUpdateLauncher = appUpdateLauncher,
                        onBack = { route = AppRoute.Festivals }
                    )
                }
            }
        }
    }
}

@Composable
private fun FestivalsRoute(
    repository: CosplayRepositoryImpl,
    updateRepository: GitHubUpdateRepository,
    client: HttpClient,
    appUpdateLauncher: AppUpdateLauncher,
    onOpenUpdate: () -> Unit,
    onFestivalSelected: (FestivalSummary) -> Unit
) {
    val currentYear = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()).year }
    val updateState by produceState<AppUpdateInfo?>(initialValue = null) {
        value = runCatching { updateRepository.getLatestUpdate() }.getOrNull()
    }
    val pendingUpdate by appUpdateLauncher.pendingUpdate
    val installState by appUpdateLauncher.installState
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
        topContent = {
            val latestUpdate = updateState
            val update = when {
                pendingUpdate != null -> pendingUpdate
                latestUpdate != null && isNewerVersion(AppVersion.versionName, latestUpdate.versionName) -> latestUpdate
                else -> null
            }

            if (update != null) {
                UpdateBanner(
                    title = updateBannerTitle(update, installState),
                    onOpenUpdate = onOpenUpdate
                )
            }
        },
        onFestivalSelected = onFestivalSelected
    )
}

@Composable
private fun UpdateRoute(
    updateRepository: GitHubUpdateRepository,
    appUpdateLauncher: AppUpdateLauncher,
    onBack: () -> Unit
) {
    DisposableEffect(Unit) {
        appUpdateLauncher.refreshState()
        onDispose { }
    }
    val latestUpdate by produceState<AppUpdateInfo?>(initialValue = null) {
        value = runCatching { updateRepository.getLatestUpdate() }.getOrNull()
    }
    val pendingUpdate by appUpdateLauncher.pendingUpdate
    val installState by appUpdateLauncher.installState
    val update = pendingUpdate ?: latestUpdate

    UpdateScreen(
        update = update,
        installState = installState,
        onBack = onBack,
        onDownloadClick = {
            update?.let(appUpdateLauncher::startUpdate)
        },
        onInstallClick = { appUpdateLauncher.installUpdate() },
        onOpenInstallSettingsClick = { appUpdateLauncher.openInstallPermissionSettings() },
        onRefreshClick = { appUpdateLauncher.refreshState() }
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
