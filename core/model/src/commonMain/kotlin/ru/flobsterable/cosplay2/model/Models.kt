package ru.flobsterable.cosplay2.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FestivalListResponse(
    val events: List<FestivalSummaryDto>
)

@Serializable
data class FestivalSummaryDto(
    val id: Long,
    @SerialName("event_type_id") val eventTypeId: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val title: String,
    val description: String = "",
    val city: String = "",
    val country: String = "",
    val href: String,
    val time: String,
    val timestatus: String
)

@Serializable
data class EventTypeDto(
    val id: Int,
    val title: String,
    @SerialName("title_one") val titleOne: String
)

data class FestivalSummary(
    val id: Long,
    val title: String,
    val description: String,
    val city: String,
    val country: String,
    val eventTypeId: Int,
    val startTime: String,
    val endTime: String,
    val displayTime: String,
    val status: FestivalStatus,
    val url: String,
    val imageUrl: String? = null
)

data class FestivalCatalog(
    val festivals: List<FestivalSummary>,
    val eventTypes: Map<Int, String>,
    val year: Int,
    val isFallback: Boolean
)

data class FestivalDetail(
    val summary: FestivalSummary,
    val description: String,
    val pageContent: String,
    val pageLinks: List<FestivalActionLink>,
    val venueCity: String,
    val venueCountryCode: String,
    val startDate: String,
    val endDate: String,
    val imageUrl: String?
)

data class FestivalActionLink(
    val title: String,
    val url: String
)

enum class FestivalStatus {
    Future,
    Past;

    companion object {
        fun fromRaw(raw: String): FestivalStatus = when (raw.lowercase()) {
            "future" -> Future
            else -> Past
        }
    }
}

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubReleaseAssetDto> = emptyList()
)

@Serializable
data class GitHubReleaseAssetDto(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String? = null
)

data class AppUpdateInfo(
    val versionTag: String,
    val versionName: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?
)

sealed interface AppUpdateInstallState {
    data object Idle : AppUpdateInstallState

    data class Downloading(
        val versionName: String,
        val progress: Float?,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateInstallState

    data class Downloaded(
        val versionName: String
    ) : AppUpdateInstallState

    data class RequiresInstallPermission(
        val versionName: String
    ) : AppUpdateInstallState

    data class Failed(
        val versionName: String?,
        val message: String
    ) : AppUpdateInstallState
}

fun compareAppVersions(left: String, right: String): Int {
    fun normalize(value: String): List<Int> = value
        .removePrefix("v")
        .split(".", "-", "_")
        .mapNotNull { it.toIntOrNull() }

    val leftParts = normalize(left)
    val rightParts = normalize(right)
    val maxSize = maxOf(leftParts.size, rightParts.size)

    for (index in 0 until maxSize) {
        val leftPart = leftParts.getOrElse(index) { 0 }
        val rightPart = rightParts.getOrElse(index) { 0 }
        if (leftPart != rightPart) {
            return leftPart.compareTo(rightPart)
        }
    }

    return 0
}
