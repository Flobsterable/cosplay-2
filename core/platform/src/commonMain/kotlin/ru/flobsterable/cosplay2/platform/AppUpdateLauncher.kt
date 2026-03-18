package ru.flobsterable.cosplay2.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import ru.flobsterable.cosplay2.model.AppUpdateInfo
import ru.flobsterable.cosplay2.model.AppUpdateInstallState

interface AppUpdateLauncher {
    val pendingUpdate: State<AppUpdateInfo?>
    val installState: State<AppUpdateInstallState>

    fun startUpdate(update: AppUpdateInfo)

    fun installUpdate()

    fun openInstallPermissionSettings()

    fun refreshState()
}

@Composable
expect fun rememberAppUpdateLauncher(): AppUpdateLauncher
