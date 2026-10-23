package ru.flobsterable.cosplay2.feature.festival

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import ru.flobsterable.cosplay2.feature.festival.ui.catalog.components.FestivalFilters
import ru.flobsterable.cosplay2.feature.festival.ui.catalog.components.FestivalSearchBar
import ru.flobsterable.cosplay2.model.FestivalCatalog
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.platform.matchesMyCity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FestivalsScreen(
    state: UiState<FestivalCatalog>,
    currentYear: Int,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    client: HttpClient,
    topContent: (@Composable () -> Unit)? = null,
    onFestivalSelected: (FestivalSummary) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    favoriteIds: Set<Long>,
    favoritesEnabled: Boolean,
    onToggleFavorite: (FestivalSummary) -> Unit,
    myCity: String,
    onlyMyCity: Boolean,
    onCityFilterSelected: (Boolean) -> Unit,
    cityEnabled: Boolean,
    onChooseCity: (List<String>) -> Unit
) {
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(TypeFilter.All) }
    var selectedMonth by rememberSaveable(selectedYear) { mutableStateOf(MonthFilter.All) }
    val filterCatalog = (state as? UiState.Success)?.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фестивали") },
                actions = {
                    IconButton(onClick = onOpenFavorites) { Icon(Icons.Rounded.Star, contentDescription = "Избранное") }
                    TextButton(onClick = onOpenSettings) { Text("Настройки") }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column {
                        FestivalSearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            filtersExpanded = filtersExpanded,
                            onFiltersExpandedChange = { filtersExpanded = it }
                        )
                        FestivalFilters(
                            expanded = filtersExpanded,
                            festivals = filterCatalog?.festivals.orEmpty(),
                            catalogYear = filterCatalog?.year,
                            currentYear = currentYear,
                            selectedYear = selectedYear,
                            onYearSelected = onYearSelected,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { selectedCategory = it },
                            selectedMonth = selectedMonth,
                            onMonthSelected = { selectedMonth = it },
                            myCity = myCity,
                            onlyMyCity = onlyMyCity,
                            onCityFilterSelected = onCityFilterSelected,
                            cityEnabled = cityEnabled,
                            onChooseCity = onChooseCity
                        )
                    }
                }
                topContent?.let { content ->
                    item { content() }
                }
                when {
                    state is UiState.Loading -> item {
                        LoadingBlock()
                    }

                    state is UiState.Error -> item {
                        ErrorBlock(state.message)
                    }

                    state is UiState.Success -> {
                        val catalog = state.data
                        val filtered = catalog.festivals
                            .filter { festival -> !onlyMyCity || myCity.isBlank() || matchesMyCity(festival.city, myCity) }
                            .filter { festival ->
                                selectedYear != ACTUAL_FILTER_YEAR || festival.status == FestivalStatus.Future
                            }
                            .filter { festival ->
                                selectedCategory.matches(festival.eventTypeId)
                            }
                            .filter { festival ->
                                selectedMonth.matches(festival.startTime)
                            }
                            .filter { festival ->
                                if (query.isBlank()) {
                                    true
                                } else {
                                    val haystack = listOf(
                                        festival.title,
                                        festival.city,
                                        festival.description
                                    ).joinToString(" ").lowercase()
                                    haystack.contains(query.trim().lowercase())
                                }
                            }

                        if (filtered.isEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (onlyMyCity && myCity.isNotBlank()) {
                                        Text("Нет фестивалей в городе «$myCity» с выбранными фильтрами.")
                                        TextButton(onClick = { onCityFilterSelected(false) }) { Text("Показать все города") }
                                    } else {
                                        EmptyBlock()
                                    }
                                }
                            }
                        } else {
                            item {
                                Text(
                                    "Найдено: ${filtered.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(filtered, key = { it.id }) { festival ->
                                FestivalCard(
                                    festival = festival,
                                    eventTypeTitle = selectedCategory.labelFor(
                                        festival.eventTypeId,
                                        catalog.eventTypes[festival.eventTypeId]
                                    ),
                                    client = client,
                                    onClick = { onFestivalSelected(festival) },
                                    isFavorite = festival.id in favoriteIds,
                                    favoritesEnabled = favoritesEnabled,
                                    onToggleFavorite = { onToggleFavorite(festival) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
