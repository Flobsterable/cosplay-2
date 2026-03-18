package ru.flobsterable.cosplay2.feature.festival

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import ru.flobsterable.cosplay2.model.FestivalDetail

@Composable
fun FestivalDetailScreen(
    state: UiState<FestivalDetail>,
    client: HttpClient,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (state) {
            UiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingBlock()
            }

            is UiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ErrorBlock(state.message)
            }

            is UiState.Success -> {
                val detail = state.data
                val aboutText = buildFestivalAboutText(detail)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = 88.dp,
                        end = 20.dp,
                        bottom = 12.dp
                    ),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        DetailHero(detail = detail, client = client)
                    }
                    item {
                        MetaCard(detail = detail)
                    }
                    item {
                        ExpandableSectionCard(
                            title = "О фестивале",
                            body = aboutText.ifBlank { "Описание на сайте пока не заполнено." },
                            links = detail.pageLinks,
                            onLinkClick = { uriHandler.openUri(it) }
                        )
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                FilledTonalButton(
                                    onClick = { uriHandler.openUri(buildCalendarUrl(detail)) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.EventAvailable, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Добавить в календарь")
                                }
                                Spacer(Modifier.height(10.dp))
                                FilledTonalButton(
                                    onClick = { uriHandler.openUri(detail.summary.url) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Открыть страницу фестиваля")
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        )
                    )
                )
                .statusBarsPadding()
                .height(72.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

internal fun buildFestivalAboutText(detail: FestivalDetail): String {
    val summaryDescription = detail.description.trim()
    val pageContent = detail.pageContent.trim()

    val paragraphs = buildList {
        if (summaryDescription.isNotBlank()) add(summaryDescription)
        if (pageContent.isNotBlank()) add(pageContent)
    }
        .flatMap { text -> text.split(Regex("""\n{2,}""")) }
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val deduplicated = buildList<String> {
        paragraphs.forEach { paragraph ->
            val normalized = paragraph.lowercase().replace(Regex("""\s+"""), " ")
            if (none { existing: String ->
                    existing.lowercase().replace(Regex("""\s+"""), " ") == normalized
                }
            ) {
                add(paragraph)
            }
        }
    }

    return deduplicated.joinToString("\n\n")
}
