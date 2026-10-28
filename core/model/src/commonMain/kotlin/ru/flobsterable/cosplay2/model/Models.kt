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

@Serializable
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
    val imageUrl: String? = null,
    val eventTypeTitle: String? = null
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
    val leftVersion = left.removePrefix("v").substringBefore('+')
    val rightVersion = right.removePrefix("v").substringBefore('+')
    val leftParts = leftVersion.substringBefore('-').split(".", "_")
    val rightParts = rightVersion.substringBefore('-').split(".", "_")
    val maxSize = maxOf(leftParts.size, rightParts.size)

    for (index in 0 until maxSize) {
        val comparison = compareVersionIdentifiers(
            leftParts.getOrElse(index) { "0" },
            rightParts.getOrElse(index) { "0" }
        )
        if (comparison != 0) return comparison
    }

    val leftPrerelease = leftVersion.substringAfter('-', "")
    val rightPrerelease = rightVersion.substringAfter('-', "")
    if (leftPrerelease.isEmpty() && rightPrerelease.isEmpty()) return 0
    if (leftPrerelease.isEmpty()) return 1
    if (rightPrerelease.isEmpty()) return -1

    val leftIdentifiers = leftPrerelease.split('.')
    val rightIdentifiers = rightPrerelease.split('.')
    for (index in 0 until minOf(leftIdentifiers.size, rightIdentifiers.size)) {
        val comparison = compareVersionIdentifiers(leftIdentifiers[index], rightIdentifiers[index])
        if (comparison != 0) return comparison
    }

    return leftIdentifiers.size.compareTo(rightIdentifiers.size)
}

private fun compareVersionIdentifiers(left: String, right: String): Int {
    val leftIsNumeric = left.isNotEmpty() && left.all { it in '0'..'9' }
    val rightIsNumeric = right.isNotEmpty() && right.all { it in '0'..'9' }
    if (leftIsNumeric && rightIsNumeric) {
        val leftNumber = left.trimStart('0').ifEmpty { "0" }
        val rightNumber = right.trimStart('0').ifEmpty { "0" }
        val lengthComparison = leftNumber.length.compareTo(rightNumber.length)
        return if (lengthComparison != 0) lengthComparison else leftNumber.compareTo(rightNumber)
    }
    if (leftIsNumeric != rightIsNumeric) return if (leftIsNumeric) -1 else 1
    return left.compareTo(right)
}
