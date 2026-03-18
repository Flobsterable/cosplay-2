package ru.flobsterable.cosplay2.platform

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ru.flobsterable.cosplay2.model.AppUpdateInfo
import ru.flobsterable.cosplay2.model.AppUpdateInstallState
import ru.flobsterable.cosplay2.model.compareAppVersions
import java.io.File

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val UPDATE_LOG_TAG = "AppUpdate"
private const val PREFS_NAME = "app_update_state"
private const val KEY_VERSION_NAME = "version_name"
private const val KEY_VERSION_TAG = "version_tag"
private const val KEY_NOTES = "notes"
private const val KEY_RELEASE_URL = "release_url"
private const val KEY_APK_URL = "apk_url"
private const val KEY_DOWNLOAD_ID = "download_id"

@Composable
actual fun rememberAppUpdateLauncher(): AppUpdateLauncher {
    val context = LocalContext.current
    return remember(context) { AndroidAppUpdateLauncher(context.applicationContext) }
}

private class AndroidAppUpdateLauncher(
    private val context: Context
) : AppUpdateLauncher {

    override val pendingUpdate: State<AppUpdateInfo?> get() = pendingUpdateState
    override val installState: State<AppUpdateInstallState> get() = installStateState

    private val pendingUpdateState: MutableState<AppUpdateInfo?> = mutableStateOf(null)
    private val installStateState: MutableState<AppUpdateInstallState> =
        mutableStateOf(AppUpdateInstallState.Idle)

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val handler = Handler(Looper.getMainLooper())
    private var activeDownloadId: Long? = null
    private var receiverRegistered = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            val downloadId = activeDownloadId ?: return
            when (val progressState = queryDownloadState(downloadId)) {
                is AppUpdateInstallState.Downloading -> {
                    installStateState.value = progressState
                    handler.postDelayed(this, 500L)
                }
                is AppUpdateInstallState.Downloaded -> {
                    installStateState.value = progressState
                    clearDownloadId()
                }
                is AppUpdateInstallState.Failed -> {
                    installStateState.value = progressState
                    clearDownloadId()
                }
                else -> {
                    installStateState.value = progressState
                }
            }
        }
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val expectedId = activeDownloadId ?: return
            if (completedId != expectedId) return

            handler.removeCallbacks(progressRunnable)
            installStateState.value = queryDownloadState(expectedId)
            if (installStateState.value !is AppUpdateInstallState.Downloading) {
                clearDownloadId()
            }
        }
    }

    init {
        registerReceiverIfNeeded()
        restoreState()
    }

    override fun startUpdate(update: AppUpdateInfo) {
        persistPendingUpdate(update)
        pendingUpdateState.value = update

        val apkUrl = update.apkUrl
        if (apkUrl.isNullOrBlank()) {
            openInBrowser(update.releaseUrl)
            return
        }

        val apkFile = apkFileFor(update.versionName)
        if (apkFile.exists()) {
            installStateState.value = AppUpdateInstallState.Downloaded(update.versionName)
            return
        }

        beginDownload(update)
    }

    override fun installUpdate() {
        val update = pendingUpdateState.value ?: return
        val apkFile = apkFileFor(update.versionName)
        if (!apkFile.exists()) {
            installStateState.value = AppUpdateInstallState.Failed(
                versionName = update.versionName,
                message = "APK обновления не найден. Попробуй скачать его снова."
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            installStateState.value = AppUpdateInstallState.RequiresInstallPermission(update.versionName)
            return
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        kotlin.runCatching {
            context.startActivity(installIntent)
            installStateState.value = AppUpdateInstallState.Downloaded(update.versionName)
        }.onFailure { throwable ->
            AppLogger.e(UPDATE_LOG_TAG, "Failed to launch installer", throwable)
            installStateState.value = AppUpdateInstallState.Failed(
                versionName = update.versionName,
                message = "Не удалось открыть установщик обновления."
            )
        }
    }

    override fun openInstallPermissionSettings() {
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        kotlin.runCatching {
            context.startActivity(settingsIntent)
        }.onFailure { throwable ->
            AppLogger.e(UPDATE_LOG_TAG, "Failed to open install permission settings", throwable)
            installStateState.value = AppUpdateInstallState.Failed(
                versionName = pendingUpdateState.value?.versionName,
                message = "Не удалось открыть настройки разрешения на установку."
            )
        }
    }

    override fun refreshState() {
        cleanupIfInstalled()

        val update = pendingUpdateState.value
        val downloadId = activeDownloadId
        when {
            update == null -> {
                installStateState.value = AppUpdateInstallState.Idle
            }
            downloadId != null -> {
                installStateState.value = queryDownloadState(downloadId)
                if (installStateState.value is AppUpdateInstallState.Downloading) {
                    startProgressPolling()
                }
            }
            apkFileFor(update.versionName).exists() -> {
                installStateState.value = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    AppUpdateInstallState.RequiresInstallPermission(update.versionName)
                } else {
                    AppUpdateInstallState.Downloaded(update.versionName)
                }
            }
            else -> {
                clearPendingUpdate()
                installStateState.value = AppUpdateInstallState.Idle
            }
        }
    }

    private fun beginDownload(update: AppUpdateInfo) {
        val apkUrl = update.apkUrl ?: return
        val targetFile = apkFileFor(update.versionName)
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Обновление Cosplay")
            .setDescription("Скачивается версия ${update.versionName}")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationUri(Uri.fromFile(targetFile))

        val downloadId = downloadManager.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, downloadId).apply()
        activeDownloadId = downloadId
        installStateState.value = AppUpdateInstallState.Downloading(
            versionName = update.versionName,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = 0L
        )
        AppLogger.d(UPDATE_LOG_TAG, "Enqueued app update download id=$downloadId")
        startProgressPolling()
    }

    private fun startProgressPolling() {
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    private fun queryDownloadState(downloadId: Long): AppUpdateInstallState {
        val update = pendingUpdateState.value
        val versionName = update?.versionName
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                return AppUpdateInstallState.Failed(
                    versionName = versionName,
                    message = "Не удалось получить состояние загрузки обновления."
                )
            }

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )

            return when (status) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_RUNNING -> AppUpdateInstallState.Downloading(
                    versionName = versionName.orEmpty(),
                    progress = if (total > 0L) downloaded.toFloat() / total.toFloat() else null,
                    downloadedBytes = downloaded,
                    totalBytes = total.coerceAtLeast(0L)
                )

                DownloadManager.STATUS_SUCCESSFUL -> {
                    val resolvedVersionName = versionName.orEmpty()
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        !context.packageManager.canRequestPackageInstalls()
                    ) {
                        AppUpdateInstallState.RequiresInstallPermission(resolvedVersionName)
                    } else {
                        AppUpdateInstallState.Downloaded(resolvedVersionName)
                    }
                }

                else -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    AppLogger.e(UPDATE_LOG_TAG, "Download failed with reason=$reason for id=$downloadId")
                    AppUpdateInstallState.Failed(
                        versionName = versionName,
                        message = "Загрузка обновления прервалась. Код ошибки: $reason."
                    )
                }
            }
        }
    }

    private fun restoreState() {
        pendingUpdateState.value = loadPendingUpdate()
        activeDownloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L).takeIf { it > 0L }
        refreshState()
    }

    private fun cleanupIfInstalled() {
        val update = loadPendingUpdate() ?: return
        if (compareAppVersions(AppVersion.versionName, update.versionName) >= 0) {
            apkFileFor(update.versionName).delete()
            clearPendingUpdate()
            clearDownloadId()
            pendingUpdateState.value = null
            installStateState.value = AppUpdateInstallState.Idle
        }
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun persistPendingUpdate(update: AppUpdateInfo) {
        prefs.edit()
            .putString(KEY_VERSION_NAME, update.versionName)
            .putString(KEY_VERSION_TAG, update.versionTag)
            .putString(KEY_NOTES, update.notes)
            .putString(KEY_RELEASE_URL, update.releaseUrl)
            .putString(KEY_APK_URL, update.apkUrl)
            .apply()
    }

    private fun loadPendingUpdate(): AppUpdateInfo? {
        val versionName = prefs.getString(KEY_VERSION_NAME, null) ?: return null
        return AppUpdateInfo(
            versionTag = prefs.getString(KEY_VERSION_TAG, null) ?: versionName,
            versionName = versionName,
            notes = prefs.getString(KEY_NOTES, null).orEmpty(),
            releaseUrl = prefs.getString(KEY_RELEASE_URL, null).orEmpty(),
            apkUrl = prefs.getString(KEY_APK_URL, null)
        )
    }

    private fun clearPendingUpdate() {
        prefs.edit()
            .remove(KEY_VERSION_NAME)
            .remove(KEY_VERSION_TAG)
            .remove(KEY_NOTES)
            .remove(KEY_RELEASE_URL)
            .remove(KEY_APK_URL)
            .apply()
    }

    private fun clearDownloadId() {
        activeDownloadId = null
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        handler.removeCallbacks(progressRunnable)
    }

    private fun apkFileFor(versionName: String): File {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        return File(downloadsDir, "cosplay2-$versionName.apk")
    }

    private fun openInBrowser(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        kotlin.runCatching {
            context.startActivity(browserIntent)
        }.onFailure { throwable ->
            if (throwable is ActivityNotFoundException) {
                installStateState.value = AppUpdateInstallState.Failed(
                    versionName = pendingUpdateState.value?.versionName,
                    message = "Не удалось открыть страницу обновления."
                )
            }
            AppLogger.e(UPDATE_LOG_TAG, "Failed to open update url: $url", throwable)
        }
    }
}
