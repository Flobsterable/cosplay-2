package ru.flobsterable.cosplay2.feature.festival.ui.catalog.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.flobsterable.cosplay2.feature.festival.ACTUAL_FILTER_YEAR
import ru.flobsterable.cosplay2.feature.festival.FilterChip
import ru.flobsterable.cosplay2.feature.festival.MonthDropdown
import ru.flobsterable.cosplay2.feature.festival.MonthFilter
import ru.flobsterable.cosplay2.feature.festival.TypeFilter
import ru.flobsterable.cosplay2.feature.festival.resources.Res
import ru.flobsterable.cosplay2.feature.festival.resources.catalog_actual_year
import ru.flobsterable.cosplay2.feature.festival.resources.catalog_all_cities
import ru.flobsterable.cosplay2.feature.festival.resources.catalog_change_city
import ru.flobsterable.cosplay2.feature.festival.resources.catalog_my_city
import ru.flobsterable.cosplay2.feature.festival.resources.catalog_selected_city
import ru.flobsterable.cosplay2.model.FestivalSummary
import ru.flobsterable.cosplay2.platform.normalizeCity

@Composable
internal fun FestivalFilters(
    expanded: Boolean,
    festivals: List<FestivalSummary>,
    catalogYear: Int?,
    currentYear: Int,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    selectedCategory: TypeFilter,
    onCategorySelected: (TypeFilter) -> Unit,
    selectedMonth: MonthFilter,
    onMonthSelected: (MonthFilter) -> Unit,
    myCity: String,
    onlyMyCity: Boolean,
    onCityFilterSelected: (Boolean) -> Unit,
    cityEnabled: Boolean,
    onChooseCity: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier.fillMaxWidth(),
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
                    title = stringResource(Res.string.catalog_all_cities),
                    selected = !onlyMyCity || myCity.isBlank(),
                    onClick = { onCityFilterSelected(false) }
                )
                FilterChip(
                    title = if (myCity.isBlank()) {
                        stringResource(Res.string.catalog_my_city)
                    } else {
                        stringResource(Res.string.catalog_selected_city, myCity)
                    },
                    selected = onlyMyCity && myCity.isNotBlank(),
                    onClick = {
                        if (myCity.isNotBlank()) onCityFilterSelected(true)
                        else if (cityEnabled) onChooseCity(festivals
                            .map { normalizeCity(it.city) }.filter { it.isNotBlank() })
                    }
                )
                if (myCity.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onChooseCity(festivals
                                .map { normalizeCity(it.city) }.filter { it.isNotBlank() })
                        },
                        enabled = cityEnabled
                    ) {
                        Text(stringResource(Res.string.catalog_change_city))
                    }
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
                        onClick = { onCategorySelected(filter) }
                    )
                }
            }
            val years = buildList {
                val start = maxOf(2013, currentYear - 6)
                add(ACTUAL_FILTER_YEAR)
                for (year in currentYear downTo start) add(year)
                catalogYear?.let { if (it !in this) add(1, it) }
                if (selectedYear !in this) add(1, selectedYear)
            }.distinct()

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                years.forEach { year ->
                    FilterChip(
                        title = if (year == ACTUAL_FILTER_YEAR) {
                            stringResource(Res.string.catalog_actual_year)
                        } else {
                            year.toString()
                        },
                        selected = selectedYear == year,
                        onClick = { onYearSelected(year) }
                    )
                }
            }
            MonthDropdown(selectedMonth = selectedMonth, onMonthSelected = onMonthSelected)
        }
    }
}
