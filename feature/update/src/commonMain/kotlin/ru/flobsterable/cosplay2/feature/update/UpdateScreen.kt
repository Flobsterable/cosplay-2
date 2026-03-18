package ru.flobsterable.cosplay2.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.flobsterable.cosplay2.model.AppUpdateInfo
import ru.flobsterable.cosplay2.model.AppUpdateInstallState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    update: AppUpdateInfo?,
    installState: AppUpdateInstallState,
    onBack: () -> Unit,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onOpenInstallSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обновление приложения") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        when {
            update == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    UpdateHeroCard(update = update)
                }
                item {
                    when (installState) {
                        is AppUpdateInstallState.Downloading -> DownloadingBlock(installState)
                        is AppUpdateInstallState.Downloaded -> DownloadedBlock(
                            versionName = installState.versionName,
                            onInstallClick = onInstallClick
                        )
                        is AppUpdateInstallState.RequiresInstallPermission -> PermissionBlock(
                            versionName = installState.versionName,
                            onOpenInstallSettingsClick = onOpenInstallSettingsClick,
                            onRefreshClick = onRefreshClick
                        )
                        is AppUpdateInstallState.Failed -> FailedBlock(
                            message = installState.message,
                            onRetryClick = onDownloadClick
                        )
                        AppUpdateInstallState.Idle -> ReadyToDownloadBlock(
                            update = update,
                            onDownloadClick = onDownloadClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateHeroCard(update: AppUpdateInfo) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Доступна версия ${update.versionName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Новая версия приложения загружается прямо из GitHub Release. После загрузки ты сможешь сразу перейти к установке.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (update.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Что нового",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = update.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ReadyToDownloadBlock(
    update: AppUpdateInfo,
    onDownloadClick: () -> Unit
) {
    StatusCard(
        title = "Обновление готово к загрузке",
        body = "Версия ${update.versionName} уже найдена. Нажми кнопку ниже, чтобы начать загрузку APK.",
        action = {
            FilledTonalButton(
                onClick = onDownloadClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Загрузить обновление")
            }
        }
    )
}

@Composable
private fun DownloadingBlock(
    state: AppUpdateInstallState.Downloading
) {
    val progress = state.progress
    StatusCard(
        title = "Обновление загружается",
        body = buildString {
            append("Сейчас скачивается версия ")
            append(state.versionName)
            append(". ")
            append(
                if (state.totalBytes > 0L) {
                    "${formatBytes(state.downloadedBytes)} из ${formatBytes(state.totalBytes)}"
                } else {
                    "Подготовка загрузки"
                }
            )
        },
        action = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    )
}

@Composable
private fun DownloadedBlock(
    versionName: String,
    onInstallClick: () -> Unit
) {
    StatusCard(
        title = "Обновление уже загружено",
        body = "Версия $versionName уже находится на устройстве. Можно перейти к установке приложения.",
        action = {
            FilledTonalButton(
                onClick = onInstallClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Установить обновление")
            }
        }
    )
}

@Composable
private fun PermissionBlock(
    versionName: String,
    onOpenInstallSettingsClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    StatusCard(
        title = "Нужно разрешение на установку",
        body = "Обновление $versionName уже загружено, но Android не даст установить APK, пока ты не разрешишь установку из этого источника.",
        action = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenInstallSettingsClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Открыть настройки разрешения")
                }
                FilledTonalButton(
                    onClick = onRefreshClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Проверить снова")
                }
            }
        }
    )
}

@Composable
private fun FailedBlock(
    message: String,
    onRetryClick: () -> Unit
) {
    StatusCard(
        title = "Не удалось обновить приложение",
        body = message,
        action = {
            FilledTonalButton(
                onClick = onRetryClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Попробовать снова")
            }
        }
    )
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    action: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            action()
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    val formatted = if (value >= 10 || unitIndex == 0) {
        value.toInt().toString()
    } else {
        "%.1f".format(value)
    }
    return "$formatted ${units[unitIndex]}"
}
