package ru.flobsterable.cosplay2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
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
import ru.flobsterable.cosplay2.feature.festival.FavoritesScreen
import ru.flobsterable.cosplay2.feature.festival.favoriteFestivalSummary
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
import ru.flobsterable.cosplay2.platform.FavoritesRepository
import ru.flobsterable.cosplay2.platform.rememberFavoritesStorage

import ru.flobsterable.cosplay2.feature.settings.SettingsScreen
import ru.flobsterable.cosplay2.feature.settings.SupportChatState
import ru.flobsterable.cosplay2.feature.settings.SupportScreen

private sealed interface AppRoute {
    data object Festivals : AppRoute
    data class FestivalDetails(val festival: FestivalSummary, val fromFavorites: Boolean = false) : AppRoute
    data object Favorites : AppRoute
    data object Update : AppRoute
    data object Settings : AppRoute
    data object Support : AppRoute
}

@Composable
fun CosplayApp() {
    CosplayTheme {
        val client = remember { createHttpClient() }
        val repository = remember(client) { CosplayRepositoryImpl(CosplayApiImpl(client)) }
        val catalogCache = remember(repository) { FestivalCatalogCache(repository::getFestivalCatalog) }
        val updateRepository = remember(client) {
            GitHubUpdateRepository(
                api = GitHubReleaseApiImpl(client),
                owner = "Flobsterable",
                repo = "cosplay-2"
            )
        }
        val appUpdateLauncher = rememberAppUpdateLauncher()
        val screenState = rememberSaveableStateHolder()
        val supportChatState = remember { SupportChatState() }
        var route by remember { mutableStateOf<AppRoute>(AppRoute.Festivals) }
        val favoritesStorage = rememberFavoritesStorage()
        val favorites = remember(favoritesStorage) { FavoritesState(FavoritesRepository(favoritesStorage)) }
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val favoriteIds = remember(favorites.state) {
            (favorites.state as? UiState.Success)?.data.orEmpty().map { it.id }.toSet()
        }
        val favoritesEnabled = favorites.canModify
        val toggleFavorite: (FestivalSummary) -> Unit = { festival -> scope.launch { favorites.toggle(festival) } }
        val goBack: () -> Unit = {
            route = when {
                route == AppRoute.Support -> AppRoute.Settings
                (route as? AppRoute.FestivalDetails)?.fromFavorites == true -> AppRoute.Favorites
                else -> AppRoute.Festivals
            }
        }
        LaunchedEffect(favorites) { favorites.load() }
        LaunchedEffect(favorites) {
            favorites.errors.collect { error ->
                val result = snackbar.showSnackbar(error.message, actionLabel = if (error.retryLoad) "Повторить" else null)
                if (result == SnackbarResult.ActionPerformed && error.retryLoad) favorites.load()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                client.close()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
                SystemBackHandler(
                    enabled = route != AppRoute.Festivals,
                    onBack = goBack
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
                    val stateKey = when (targetRoute) {
                        AppRoute.Festivals -> "festivals"
                        AppRoute.Favorites -> "favorites"
                        is AppRoute.FestivalDetails -> "details-${targetRoute.festival.id}"
                        AppRoute.Update -> "update"
                        AppRoute.Settings -> "settings"
                        AppRoute.Support -> "support"
                    }
                    val screenContent: @Composable () -> Unit = {
                        when (targetRoute) {
                            AppRoute.Festivals -> FestivalsRoute(
                                catalogCache = catalogCache,
                                updateRepository = updateRepository,
                                client = client,
                                appUpdateLauncher = appUpdateLauncher,
                                onOpenUpdate = { route = AppRoute.Update },
                                onOpenSettings = { route = AppRoute.Settings },
                                onOpenFavorites = { route = AppRoute.Favorites },
                                favoriteIds = favoriteIds,
                                favoritesEnabled = favoritesEnabled,
                                onToggleFavorite = toggleFavorite,
                                onFestivalSelected = { route = AppRoute.FestivalDetails(it) }
                            )
                            is AppRoute.FestivalDetails -> FestivalDetailRoute(
                                repository = repository,
                                client = client,
                                festival = targetRoute.festival,
                                onBack = goBack,
                                isFavorite = targetRoute.festival.id in favoriteIds,
                                favoritesEnabled = favoritesEnabled,
                                onToggleFavorite = toggleFavorite
                            )
                            AppRoute.Favorites -> FavoritesScreen(
                                state = favorites.state,
                                favoritesEnabled = favoritesEnabled,
                                client = client,
                                onBack = goBack,
                                onRetry = { scope.launch { favorites.load() } },
                                onFestivalSelected = { route = AppRoute.FestivalDetails(it, fromFavorites = true) },
                                onToggleFavorite = toggleFavorite
                            )
                            AppRoute.Settings -> SettingsScreen(
                                versionName = AppVersion.versionName,
                                onBack = { route = AppRoute.Festivals },
                                onOpenSupport = { route = AppRoute.Support }
                            )
                            AppRoute.Support -> SupportScreen(
                                state = supportChatState,
                                onSendMessage = { /* Connect the support service here when it is available. */ },
                                onBack = { route = AppRoute.Settings }
                            )
                            AppRoute.Update -> UpdateRoute(
                                updateRepository = updateRepository,
                                appUpdateLauncher = appUpdateLauncher,
                                onBack = { route = AppRoute.Festivals }
                            )
                        }
                    }
                    if (targetRoute is AppRoute.FestivalDetails) {
                        screenContent()
                    } else {
                        screenState.SaveableStateProvider(stateKey) { screenContent() }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp))
            }
        }
    }
}

@Composable
private fun FestivalsRoute(
    catalogCache: FestivalCatalogCache,
    updateRepository: GitHubUpdateRepository,
    client: HttpClient,
    appUpdateLauncher: AppUpdateLauncher,
    onOpenUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    favoriteIds: Set<Long>,
    favoritesEnabled: Boolean,
    onToggleFavorite: (FestivalSummary) -> Unit,
    onFestivalSelected: (FestivalSummary) -> Unit
) {
    val currentYear = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()).year }
    val updateState by produceState<AppUpdateInfo?>(initialValue = null) {
        value = runCatching { updateRepository.getLatestUpdate() }.getOrNull()
    }
    val pendingUpdate by appUpdateLauncher.pendingUpdate
    val installState by appUpdateLauncher.installState
    var selectedYear by rememberSaveable { mutableStateOf(ACTUAL_FILTER_YEAR) }
    val requestedYear = if (selectedYear == ACTUAL_FILTER_YEAR) currentYear else selectedYear
    // Navigation reuses this year's data; changing the year invalidates that cache.
    var lastSuccessfulCatalog by remember(selectedYear) { mutableStateOf(catalogCache.peek(requestedYear)) }
    val state by produceState<UiState<FestivalCatalog>>(
        initialValue = catalogCache.peek(requestedYear)?.let { UiState.Success(it) } ?: UiState.Loading,
        selectedYear
    ) {
        catalogCache.selectYear(selectedYear)
        value = catalogCache.peek(requestedYear)?.let { UiState.Success(it) } ?: UiState.Loading
        try {
            value = UiState.Success(catalogCache.get(requestedYear))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            value = UiState.Error(error.message ?: "Не удалось загрузить фестивали")
        }
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
        onFestivalSelected = onFestivalSelected,
        onOpenSettings = onOpenSettings,
        onOpenFavorites = onOpenFavorites,
        favoriteIds = favoriteIds,
        favoritesEnabled = favoritesEnabled,
        onToggleFavorite = onToggleFavorite
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
    onBack: () -> Unit,
    isFavorite: Boolean,
    favoritesEnabled: Boolean,
    onToggleFavorite: (FestivalSummary) -> Unit
) {
    val state by produceState<UiState<FestivalDetail>>(initialValue = UiState.Loading, festival.id) {
        try {
            value = UiState.Success(repository.getFestivalDetail(festival))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            value = UiState.Error(error.message ?: "Не удалось открыть карточку фестиваля")
        }
    }

    FestivalDetailScreen(
        state = state,
        client = client,
        onBack = onBack,
        festivalTitle = (state as? UiState.Success)?.data?.summary?.title ?: festival.title,
        isFavorite = isFavorite,
        favoritesEnabled = favoritesEnabled && state is UiState.Success,
        onToggleFavorite = {
            val detail = (state as? UiState.Success)?.data
            onToggleFavorite(detail?.let(::favoriteFestivalSummary) ?: festival)
        }
    )
}
