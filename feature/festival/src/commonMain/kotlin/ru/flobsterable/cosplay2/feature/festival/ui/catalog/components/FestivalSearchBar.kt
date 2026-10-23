package ru.flobsterable.cosplay2.feature.festival.ui.catalog.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.flobsterable.cosplay2.feature.festival.resources.Res
import ru.flobsterable.cosplay2.feature.festival.resources.catalog_hide_filters
import ru.flobsterable.cosplay2.feature.festival.resources.catalog_search_hint
import ru.flobsterable.cosplay2.feature.festival.resources.catalog_show_filters

@Composable
internal fun FestivalSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    filtersExpanded: Boolean,
    onFiltersExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(Res.string.catalog_search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )
        IconToggleButton(
            checked = filtersExpanded,
            onCheckedChange = onFiltersExpandedChange
        ) {
            Icon(
                imageVector = Icons.Rounded.FilterList,
                contentDescription = stringResource(
                    if (filtersExpanded) Res.string.catalog_hide_filters else Res.string.catalog_show_filters
                )
            )
        }
    }
}
