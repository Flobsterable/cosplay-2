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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import ru.flobsterable.cosplay2.model.FestivalSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: UiState<List<FestivalSummary>>,
    favoritesEnabled: Boolean,
    client: HttpClient,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFestivalSelected: (FestivalSummary) -> Unit,
    onToggleFavorite: (FestivalSummary) -> Unit,
    onReset: () -> Unit = {},
    resetEnabled: Boolean = true
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Восстановить избранное?") },
        text = { Text("Существующий файл будет сохранён в резервной копии на устройстве, затем список избранного будет очищен.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }, enabled = resetEnabled) { Text("Очистить список") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } }
    )
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Избранное") }, navigationIcon = {
                TextButton(onClick = onBack) { Text("Назад") }
            })
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        when {
            state is UiState.Success && state.data.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(state.data.sortedBy { it.startTime }, key = { it.id }) { festival ->
                    FestivalCard(
                        festival = festival,
                        eventTypeTitle = TypeFilter.All.labelFor(festival.eventTypeId, festival.eventTypeTitle),
                        client = client,
                        onClick = { onFestivalSelected(festival) },
                        isFavorite = true,
                        favoritesEnabled = favoritesEnabled,
                        onToggleFavorite = { onToggleFavorite(festival) }
                    )
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    UiState.Loading -> LoadingBlock()
                    is UiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ErrorBlock(state.message)
                        TextButton(onClick = onRetry) { Text("Попробовать снова") }
                        TextButton(onClick = { confirmReset = true }, enabled = resetEnabled) { Text("Восстановить избранное") }
                    }
                    is UiState.Success -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Добавьте фестивали в избранное", style = MaterialTheme.typography.titleLarge)
                        Text("Нажмите ★ на карточке фестиваля или в его деталях.")
                        TextButton(onClick = onBack) { Text("К фестивалям") }
                    }
                }
            }
        }
    }
}
