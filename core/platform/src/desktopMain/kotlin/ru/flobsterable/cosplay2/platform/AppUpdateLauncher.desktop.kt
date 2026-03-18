package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.net.URI

@Composable
actual fun rememberAppUpdateLauncher(): AppUpdateLauncher {
    return remember { DesktopAppUpdateLauncher() }
}

private class DesktopAppUpdateLauncher : AppUpdateLauncher {
    override fun launchUpdate(
        apkUrl: String?,
        releaseUrl: String,
        versionName: String
    ) {
        val targetUrl = apkUrl ?: releaseUrl
        kotlin.runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(targetUrl))
            }
        }.onFailure { throwable ->
            AppLogger.e("AppUpdate", "Failed to open update url: $targetUrl", throwable)
        }
    }
}
