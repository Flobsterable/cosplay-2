package ru.flobsterable.cosplay2.feature.festival

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import ru.flobsterable.cosplay2.model.FestivalCatalog
import ru.flobsterable.cosplay2.model.FestivalStatus
import ru.flobsterable.cosplay2.model.FestivalSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FestivalsScreen(
    state: UiState<FestivalCatalog>,
    catalogSnapshot: FestivalCatalog?,
    currentYear: Int,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    isYearLoading: Boolean,
    client: HttpClient,
    onFestivalSelected: (FestivalSummary) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(TypeFilter.All) }
    var selectedMonth by rememberSaveable(selectedYear) { mutableStateOf(MonthFilter.All) }
    val filterCatalog = when (state) {
        is UiState.Success -> state.data
        else -> catalogSnapshot
    }

    Scaffold(
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
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        placeholder = { Text("Поиск по названию, городу или описанию") },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
                item {
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
                }

                if (filterCatalog != null) {
                    val years = buildList {
                        val start = maxOf(2013, currentYear - 6)
                        add(ACTUAL_FILTER_YEAR)
                        for (year in currentYear downTo start) add(year)
                        if (filterCatalog.year !in this) add(1, filterCatalog.year)
                    }.distinct()

                    item {
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
                    }

                    item {
                        MonthDropdown(
                            selectedMonth = selectedMonth,
                            onMonthSelected = { selectedMonth = it }
                        )
                    }
                }

                when {
                    isYearLoading || state is UiState.Loading -> item {
                        LoadingBlock()
                    }

                    state is UiState.Error -> item {
                        ErrorBlock(state.message)
                    }

                    state is UiState.Success -> {
                        val catalog = state.data
                        val filtered = catalog.festivals
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
                                EmptyBlock()
                            }
                        } else {
                            items(filtered, key = { it.id }) { festival ->
                                FestivalCard(
                                    festival = festival,
                                    eventTypeTitle = selectedCategory.labelFor(
                                        festival.eventTypeId,
                                        catalog.eventTypes[festival.eventTypeId]
                                    ),
                                    client = client,
                                    onClick = { onFestivalSelected(festival) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
