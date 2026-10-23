package ru.flobsterable.cosplay2.data.update

import kotlinx.coroutines.runBlocking
import ru.flobsterable.cosplay2.model.GitHubReleaseAssetDto
import ru.flobsterable.cosplay2.model.GitHubReleaseDto
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class GitHubUpdateRepositoryTest {
    @Test
    fun releaseApkWinsOverDebugAndUnsignedAssets() = runBlocking {
        val update = repository(
            asset("app-debug.apk", apkMime),
            asset("app-release-unsigned.apk", apkMime),
            asset("app-release.APK"),
            asset("checksums.txt")
        ).getLatestUpdate()

        assertEquals("https://example.test/app-release.APK", update?.apkUrl)
        assertEquals("v2.1.0", update?.versionTag)
        assertEquals("2.1.0", update?.versionName)
        assertEquals("Исправления", update?.notes)
    }

    @Test
    fun apkMimeWinsWhenNeitherFilenameContainsRelease() = runBlocking {
        val update = repository(asset("first.apk"), asset("second.apk", apkMime)).getLatestUpdate()
        assertEquals("https://example.test/second.apk", update?.apkUrl)
    }

    @Test
    fun noInstallableAssetKeepsReleasePageAndDoesNotOfferUnsignedApk() = runBlocking {
        val update = repository(asset("app-UNSIGNED.apk"), asset("app.zip")).getLatestUpdate()
        assertNull(update?.apkUrl)
        assertEquals("https://example.test/releases/v2.1.0", update?.releaseUrl)
    }

    @Test
    fun emptyAssetsStillProduceReleaseInformation() = runBlocking {
        val update = repository().getLatestUpdate()
        assertNull(update?.apkUrl)
        assertEquals("2.1.0", update?.versionName)
    }

    @Test
    fun requestUsesConfiguredRepository() = runBlocking {
        var requested: Pair<String, String>? = null
        val api = object : GitHubReleaseApi {
            override suspend fun latestRelease(owner: String, repo: String): GitHubReleaseDto {
                requested = owner to repo
                return release(emptyList())
            }
        }
        GitHubUpdateRepository(api, "test-owner", "test-repo").getLatestUpdate()
        assertEquals("test-owner" to "test-repo", requested)
    }

    @Test
    fun apiErrorIsPropagatedForCallerToHandle() = runBlocking {
        val error = IllegalStateException("Test API unavailable")
        val actual = assertFailsWith<IllegalStateException> {
            failingRepository(error).getLatestUpdate()
        }
        assertSame(error, actual)
    }

    @Test
    fun cancellationIsNotConvertedToMissingUpdate() = runBlocking {
        val cancelled = CancellationException("Test cancellation")
        val actual = assertFailsWith<CancellationException> {
            failingRepository(cancelled).getLatestUpdate()
        }
        assertSame(cancelled, actual)
    }

    private fun repository(vararg assets: GitHubReleaseAssetDto): GitHubUpdateRepository {
        val api = object : GitHubReleaseApi {
            override suspend fun latestRelease(owner: String, repo: String) = release(assets.toList())
        }
        return GitHubUpdateRepository(api, "owner", "repo")
    }

    private fun failingRepository(error: Exception): GitHubUpdateRepository {
        val api = object : GitHubReleaseApi {
            override suspend fun latestRelease(owner: String, repo: String): GitHubReleaseDto = throw error
        }
        return GitHubUpdateRepository(api, "owner", "repo")
    }

    private fun release(assets: List<GitHubReleaseAssetDto>) = GitHubReleaseDto(
        tagName = "v2.1.0",
        body = "  Исправления\n",
        htmlUrl = "https://example.test/releases/v2.1.0",
        assets = assets
    )

    private fun asset(name: String, contentType: String? = null) = GitHubReleaseAssetDto(
        name = name,
        browserDownloadUrl = "https://example.test/$name",
        contentType = contentType
    )

    private val apkMime = "application/vnd.android.package-archive"
}
