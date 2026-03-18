package ru.flobsterable.cosplay2.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.flobsterable.cosplay2.model.AppUpdateInfo

@Composable
fun UpdateBanner(
    update: AppUpdateInfo,
    onUpdateClick: () -> Unit
) {
    val buttonTitle = if (update.apkUrl.isNullOrBlank()) {
        "Открыть релиз"
    } else {
        "Скачать и установить"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Доступно обновление ${update.versionName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (update.notes.isNotBlank()) {
                Text(
                    text = update.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(
                onClick = onUpdateClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonTitle)
            }
        }
    }
}

fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
    fun normalize(value: String): List<Int> = value
        .removePrefix("v")
        .split(".", "-", "_")
        .mapNotNull { it.toIntOrNull() }

    val current = normalize(currentVersion)
    val latest = normalize(latestVersion)
    val maxSize = maxOf(current.size, latest.size)

    for (index in 0 until maxSize) {
        val currentPart = current.getOrElse(index) { 0 }
        val latestPart = latest.getOrElse(index) { 0 }
        if (latestPart > currentPart) return true
        if (latestPart < currentPart) return false
    }

    return false
}
