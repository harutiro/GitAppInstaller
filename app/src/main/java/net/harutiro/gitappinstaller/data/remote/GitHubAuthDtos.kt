package net.harutiro.gitappinstaller.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresInSeconds: Int,
    @SerialName("interval") val pollIntervalSeconds: Int,
)

@Serializable
data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("scope") val scope: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("interval") val nextIntervalSeconds: Int? = null,
)

@Serializable
data class GitHubUserDto(
    @SerialName("login") val login: String,
    @SerialName("name") val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
