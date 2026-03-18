package ru.flobsterable.cosplay2.data.update

import ru.flobsterable.cosplay2.model.AppUpdateInfo

interface UpdateRepository {
    suspend fun getLatestUpdate(): AppUpdateInfo?
}

class GitHubUpdateRepository(
    private val api: GitHubReleaseApi,
    private val owner: String,
    private val repo: String
) : UpdateRepository {
    override suspend fun getLatestUpdate(): AppUpdateInfo? {
        val release = api.latestRelease(owner, repo)
        val apkAsset = release.assets
            .asSequence()
            .filter { asset -> asset.name.endsWith(".apk", ignoreCase = true) }
            .filterNot { asset -> asset.name.contains("unsigned", ignoreCase = true) }
            .sortedByDescending { asset ->
                when {
                    asset.name.contains("release", ignoreCase = true) -> 2
                    asset.contentType.equals("application/vnd.android.package-archive", ignoreCase = true) -> 1
                    else -> 0
                }
            }
            .firstOrNull()

        return AppUpdateInfo(
            versionTag = release.tagName,
            versionName = release.tagName.removePrefix("v"),
            notes = release.body.trim(),
            releaseUrl = release.htmlUrl,
            apkUrl = apkAsset?.browserDownloadUrl
        )
    }
}
