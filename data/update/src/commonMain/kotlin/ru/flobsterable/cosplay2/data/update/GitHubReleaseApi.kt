package ru.flobsterable.cosplay2.data.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import ru.flobsterable.cosplay2.model.GitHubReleaseDto

interface GitHubReleaseApi {
    suspend fun latestRelease(owner: String, repo: String): GitHubReleaseDto
}

class GitHubReleaseApiImpl(
    private val client: HttpClient
) : GitHubReleaseApi {
    override suspend fun latestRelease(owner: String, repo: String): GitHubReleaseDto =
        client.get("https://api.github.com/repos/$owner/$repo/releases/latest") {
            headers.append(HttpHeaders.Accept, "application/vnd.github+json")
            headers.append(HttpHeaders.UserAgent, "cosplay2-app-updater")
        }.body()
}
