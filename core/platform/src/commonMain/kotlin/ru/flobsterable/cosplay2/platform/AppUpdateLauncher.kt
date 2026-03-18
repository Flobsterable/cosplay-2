package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable

interface AppUpdateLauncher {
    fun launchUpdate(
        apkUrl: String?,
        releaseUrl: String,
        versionName: String
    )
}

@Composable
expect fun rememberAppUpdateLauncher(): AppUpdateLauncher
