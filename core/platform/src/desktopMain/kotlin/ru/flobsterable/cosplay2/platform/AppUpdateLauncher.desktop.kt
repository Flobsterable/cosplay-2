package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ru.flobsterable.cosplay2.model.AppUpdateInfo
import ru.flobsterable.cosplay2.model.AppUpdateInstallState
import java.awt.Desktop
import java.net.URI

@Composable
actual fun rememberAppUpdateLauncher(): AppUpdateLauncher {
    return remember { DesktopAppUpdateLauncher() }
}

private class DesktopAppUpdateLauncher : AppUpdateLauncher {
    override val pendingUpdate: State<AppUpdateInfo?> get() = pendingUpdateState
    override val installState: State<AppUpdateInstallState> get() = installStateState

    private val pendingUpdateState: MutableState<AppUpdateInfo?> = mutableStateOf(null)
    private val installStateState: MutableState<AppUpdateInstallState> =
        mutableStateOf(AppUpdateInstallState.Idle)

    override fun startUpdate(update: AppUpdateInfo) {
        pendingUpdateState.value = update
        val targetUrl = update.apkUrl ?: update.releaseUrl
        kotlin.runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(targetUrl))
            }
        }.onFailure { throwable ->
            installStateState.value = AppUpdateInstallState.Failed(
                versionName = update.versionName,
                message = "Не удалось открыть страницу обновления."
            )
            AppLogger.e("AppUpdate", "Failed to open update url: $targetUrl", throwable)
        }
    }

    override fun installUpdate() {
        pendingUpdateState.value?.let(::startUpdate)
    }

    override fun openInstallPermissionSettings() = Unit

    override fun refreshState() = Unit
}
