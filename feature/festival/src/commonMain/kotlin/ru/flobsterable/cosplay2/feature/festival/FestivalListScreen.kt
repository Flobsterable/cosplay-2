package ru.flobsterable.cosplay2.feature.festival

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import ru.flobsterable.cosplay2.model.FestivalCatalog
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.platform.matchesMyCity
import ru.flobsterable.cosplay2.platform.normalizeCity

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.weight(1f),
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                placeholder = { Text("Поиск по названию, городу или описанию") },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp)
                            )
                            IconToggleButton(
                                checked = filtersExpanded,
                                onCheckedChange = { filtersExpanded = it }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FilterList,
                                    contentDescription = if (filtersExpanded) "Скрыть фильтры" else "Показать фильтры"
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = filtersExpanded,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        title = "Все города",
                                        selected = !onlyMyCity || myCity.isBlank(),
                                        onClick = { onCityFilterSelected(false) }
                                    )
                                    FilterChip(
                                        title = if (myCity.isBlank()) "Мой город" else "Мой город: $myCity",
                                        selected = onlyMyCity && myCity.isNotBlank(),
                                        onClick = {
                                            if (myCity.isNotBlank()) onCityFilterSelected(true)
                                            else if (cityEnabled) onChooseCity(filterCatalog?.festivals.orEmpty()
                                                .map { normalizeCity(it.city) }.filter { it.isNotBlank() })
                                        }
                                    )
                                    if (myCity.isNotBlank()) {
                                        TextButton(onClick = {
                                            onChooseCity(filterCatalog?.festivals.orEmpty()
                                                .map { normalizeCity(it.city) }.filter { it.isNotBlank() })
                                        }, enabled = cityEnabled) { Text("Изменить") }
                                    }
                                }
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TypeFilter.entries.forEach { filter ->
                                        FilterChip(
                                            title = filter.title,
                                            selected = selectedCategory == filter,
                                            onClick = { selectedCategory = filter }
                                        )
                                    }
                                }

                                val years = buildList {
                                    val start = maxOf(2013, currentYear - 6)
                                    add(ACTUAL_FILTER_YEAR)
                                    for (year in currentYear downTo start) add(year)
                                    filterCatalog?.year?.let { if (it !in this) add(1, it) }
                                    if (selectedYear !in this) add(1, selectedYear)
                                }.distinct()

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    years.forEach { year ->
                                        FilterChip(
                                            title = if (year == ACTUAL_FILTER_YEAR) "Актуальные" else year.toString(),
                                            selected = selectedYear == year,
                                            onClick = { onYearSelected(year) }
                                        )
                                    }
                                }
                                MonthDropdown(selectedMonth = selectedMonth, onMonthSelected = { selectedMonth = it })
                            }
                        }
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
