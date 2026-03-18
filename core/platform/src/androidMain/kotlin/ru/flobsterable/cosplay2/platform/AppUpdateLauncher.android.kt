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
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val UPDATE_LOG_TAG = "AppUpdate"

@Composable
actual fun rememberAppUpdateLauncher(): AppUpdateLauncher {
    val context = LocalContext.current
    return remember(context) { AndroidAppUpdateLauncher(context) }
}

private class AndroidAppUpdateLauncher(
    private val context: Context
) : AppUpdateLauncher {

    override fun launchUpdate(
        apkUrl: String?,
        releaseUrl: String,
        versionName: String
    ) {
        if (apkUrl.isNullOrBlank()) {
            openInBrowser(releaseUrl)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                context,
                "Разреши установку из этого источника и повтори обновление",
                Toast.LENGTH_LONG
            ).show()
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return
        }

        enqueueApkDownload(
            apkUrl = apkUrl,
            releaseUrl = releaseUrl,
            versionName = versionName
        )
    }

    private fun enqueueApkDownload(
        apkUrl: String,
        releaseUrl: String,
        versionName: String
    ) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Обновление Cosplay")
            .setDescription("Скачивается версия $versionName")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "cosplay2-$versionName.apk"
            )

        val downloadId = downloadManager.enqueue(request)
        Toast.makeText(
            context,
            "Загрузка обновления началась",
            Toast.LENGTH_SHORT
        ).show()
        AppLogger.d(UPDATE_LOG_TAG, "Enqueued app update download id=$downloadId")

        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != downloadId) return

                kotlin.runCatching {
                    appContext.unregisterReceiver(this)
                }

                handleCompletedDownload(
                    downloadManager = downloadManager,
                    downloadId = downloadId,
                    releaseUrl = releaseUrl
                )
            }
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun handleCompletedDownload(
        downloadManager: DownloadManager,
        downloadId: Long,
        releaseUrl: String
    ) {
        downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                AppLogger.e(UPDATE_LOG_TAG, "Download cursor is empty for id=$downloadId")
                openInBrowser(releaseUrl)
                return
            }

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                AppLogger.e(UPDATE_LOG_TAG, "Download failed with reason=$reason for id=$downloadId")
                Toast.makeText(
                    context,
                    "Не удалось скачать обновление",
                    Toast.LENGTH_LONG
                ).show()
                openInBrowser(releaseUrl)
                return
            }
        }

        val downloadedUri = downloadManager.getUriForDownloadedFile(downloadId)
        if (downloadedUri == null) {
            AppLogger.e(UPDATE_LOG_TAG, "Downloaded apk uri is null for id=$downloadId")
            openInBrowser(releaseUrl)
            return
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(downloadedUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        kotlin.runCatching {
            context.startActivity(installIntent)
        }.onFailure { throwable ->
            AppLogger.e(UPDATE_LOG_TAG, "Failed to launch installer", throwable)
            openInBrowser(releaseUrl)
        }
    }

    private fun openInBrowser(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        kotlin.runCatching {
            context.startActivity(browserIntent)
        }.onFailure { throwable ->
            if (throwable is ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    "Не удалось открыть страницу обновления",
                    Toast.LENGTH_LONG
                ).show()
            }
            AppLogger.e(UPDATE_LOG_TAG, "Failed to open update url: $url", throwable)
        }
    }
}
