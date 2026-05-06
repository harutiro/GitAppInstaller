package net.harutiro.gitappinstaller.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("assets") val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
data class GitHubAssetDto(
    @SerialName("id") val id: Long,
    @SerialName("url") val apiUrl: String,
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("size") val size: Long,
    @SerialName("content_type") val contentType: String? = null,
)
