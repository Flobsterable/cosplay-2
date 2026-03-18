package ru.flobsterable.cosplay2

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.client.HttpClient
import ru.flobsterable.cosplay2.data.FestivalActionLink
import ru.flobsterable.cosplay2.data.FestivalDetail
import ru.flobsterable.cosplay2.data.FestivalStatus
import ru.flobsterable.cosplay2.data.FestivalSummary
import ru.flobsterable.cosplay2.image.loadNetworkImage

@Composable
internal fun FestivalCard(
    festival: FestivalSummary,
    eventTypeTitle: String,
    client: HttpClient,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box {
                FestivalPoster(
                    client = client,
                    imageUrl = festival.imageUrl,
                    title = festival.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 8f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x66000000),
                                    Color(0xBB000000)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TypeChip(eventTypeTitle)
                            TypeChip(if (festival.status == FestivalStatus.Future) "Скоро" else "Архив")
                        }
                        Text(
                            text = festival.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetaLine(Icons.Rounded.CalendarMonth, festival.displayTime)
                MetaLine(Icons.Rounded.LocationOn, listOf(festival.city, festival.country).filter { it.isNotBlank() }.joinToString(", "))
                Text(
                    text = festival.description.ifBlank { "Описание фестиваля пока не заполнено." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun DetailHero(detail: FestivalDetail, client: HttpClient) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box {
                FestivalPoster(
                    client = client,
                    imageUrl = detail.imageUrl ?: detail.summary.imageUrl,
                    title = detail.summary.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0x55000000),
                                    Color(0xCC000000)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TypeChip(typeTitle(detail.summary.eventTypeId))
                            TypeChip(if (detail.summary.status == FestivalStatus.Future) "В планах" else "Уже прошёл")
                        }
                        Text(
                            text = detail.summary.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FestivalPoster(
    client: HttpClient,
    imageUrl: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFDED3C4),
                        Color(0xFFBFA68E)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            PosterFallback(title = title)
        } else {
            val imageState by produceState<ImageBitmap?>(initialValue = null, imageUrl) {
                value = loadNetworkImage(client, imageUrl)
            }

            val loadedImage = imageState

            if (loadedImage == null) {
                PosterLoading()
            } else {
                Image(
                    bitmap = loadedImage,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
internal fun PosterLoading() {
    val transition = rememberInfiniteTransition(label = "poster_loading")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "poster_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFD7C8B6),
                        Color(0xFFF2EAE2),
                        Color(0xFFC7B29A)
                    ),
                    start = androidx.compose.ui.geometry.Offset(-400f + 800f * progress, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f + 800f * progress, 400f)
                )
            )
    )
}

@Composable
internal fun PosterFallback(title: String, subtitle: String = "Cosplay2") {
    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = subtitle.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun MetaCard(detail: FestivalDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Дата и время проведения",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            MetaLine(
                icon = Icons.Rounded.CalendarMonth,
                label = "Дата",
                text = formatFestivalDate(detail.startDate, detail.endDate)
            )
            MetaLine(
                icon = Icons.Rounded.LocationOn,
                label = "Город",
                text = listOf(detail.venueCity, detail.summary.country).filter { it.isNotBlank() }.joinToString(", ")
            )
        }
    }
}

@Composable
internal fun ExpandableSectionCard(
    title: String,
    body: String,
    links: List<FestivalActionLink> = emptyList(),
    onLinkClick: (String) -> Unit = {}
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val embeddedUrls = remember(body) { extractEmbeddedLinkUrls(body) }
    val actionLinks = remember(body, links) {
        links.filterNot { link -> link.url in embeddedUrls }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                RichFestivalText(
                    body = body,
                    onLinkClick = onLinkClick
                )
                if (actionLinks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        actionLinks.forEach { link ->
                            FilledTonalButton(
                                onClick = { onLinkClick(link.url) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = link.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RichFestivalText(
    body: String,
    onLinkClick: (String) -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(body, linkColor) { buildFestivalAnnotatedText(body, linkColor) }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        onClick = { offset ->
            annotated
                .getStringAnnotations(tag = "festival_link", start = offset, end = offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        }
    )
}

private fun buildFestivalAnnotatedText(body: String, linkColor: Color): AnnotatedString {
    val regex = Regex("""\[\[(.*?)\|(.*?)]]""")
    val builder = AnnotatedString.Builder()
    var cursor = 0

    regex.findAll(body).forEach { match ->
        val range = match.range
        if (range.first > cursor) {
            builder.append(body.substring(cursor, range.first))
        }

        val label = match.groupValues[1]
        val url = match.groupValues[2]
        builder.pushStringAnnotation(tag = "festival_link", annotation = url)
        builder.pushStyle(
            SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline
            )
        )
        builder.append(label)
        builder.pop()
        builder.pop()
        cursor = range.last + 1
    }

    if (cursor < body.length) {
        builder.append(body.substring(cursor))
    }

    return builder.toAnnotatedString()
}

private fun extractEmbeddedLinkUrls(body: String): Set<String> =
    Regex("""\[\[(.*?)\|(.*?)]]""")
        .findAll(body)
        .map { it.groupValues[2] }
        .filter { it.isNotBlank() }
        .toSet()

@Composable
internal fun InfoSectionCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MetaLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    label: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun FilterChip(title: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(title) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthDropdown(
    selectedMonth: MonthFilter,
    onMonthSelected: (MonthFilter) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedMonth.title,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            label = { Text("Месяц") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(24.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MonthFilter.entries.forEach { month ->
                DropdownMenuItem(
                    text = { Text(month.title) },
                    onClick = {
                        onMonthSelected(month)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
internal fun TypeChip(title: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
internal fun LoadingBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ErrorBlock(message: String) {
    InfoSectionCard(
        title = "Не получилось загрузить данные",
        body = message
    )
}

@Composable
internal fun EmptyBlock() {
    InfoSectionCard(
        title = "Ничего не найдено",
        body = "Попробуйте снять фильтр или сократить поисковый запрос."
    )
}
