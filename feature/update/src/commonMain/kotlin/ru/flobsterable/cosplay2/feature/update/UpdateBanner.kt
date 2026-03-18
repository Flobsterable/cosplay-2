package ru.flobsterable.cosplay2.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import ru.flobsterable.cosplay2.model.AppUpdateInfo
import ru.flobsterable.cosplay2.model.AppUpdateInstallState
import ru.flobsterable.cosplay2.model.compareAppVersions

@Composable
fun UpdateBanner(
    title: String,
    onOpenUpdate: () -> Unit
) {
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            FilledTonalButton(
                onClick = onOpenUpdate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть обновление")
            }
        }
    }
}

fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
    return compareAppVersions(latestVersion, currentVersion) > 0
}

fun updateBannerTitle(
    update: AppUpdateInfo,
    installState: AppUpdateInstallState
): String = when (installState) {
    is AppUpdateInstallState.Downloading -> "Обновление ${update.versionName} загружается"
    is AppUpdateInstallState.Downloaded -> "Обновление ${update.versionName} уже загружено"
    is AppUpdateInstallState.RequiresInstallPermission -> "Нужно разрешение на установку обновления"
    is AppUpdateInstallState.Failed -> "Не удалось загрузить обновление"
    AppUpdateInstallState.Idle -> "Доступно обновление ${update.versionName}"
}
