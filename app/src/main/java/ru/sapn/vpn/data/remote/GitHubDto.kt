package ru.sapn.vpn.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- GitHub Releases API (публичный, без авторизации) ----

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
data class GitHubAssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)
