package net.harutiro.gitappinstaller.data.auth

import android.util.Log
import kotlinx.coroutines.delay
import net.harutiro.gitappinstaller.GitHubOAuthConfig
import net.harutiro.gitappinstaller.data.remote.AccessTokenResponse
import net.harutiro.gitappinstaller.data.remote.DeviceCodeResponse
import net.harutiro.gitappinstaller.data.remote.GitHubAuthApi
import net.harutiro.gitappinstaller.data.remote.GitHubUserApi
import java.io.IOException

sealed interface DeviceLoginEvent {
    data class CodeReady(val userCode: String, val verificationUri: String, val expiresInSeconds: Int) : DeviceLoginEvent
    data class Polling(val attempt: Int) : DeviceLoginEvent
    data class Success(val token: String, val login: String?) : DeviceLoginEvent
    data class Failed(val message: String) : DeviceLoginEvent
    data object Cancelled : DeviceLoginEvent
}

class AuthRepository(
    private val authApi: GitHubAuthApi,
    private val userApi: GitHubUserApi,
    private val tokenStore: TokenStore,
) {
    /**
     * Runs the GitHub Device Flow end-to-end.
     * Emits one [DeviceLoginEvent.CodeReady] first, then a [Success] / [Failed] / [Cancelled].
     */
    suspend fun startDeviceFlow(emit: suspend (DeviceLoginEvent) -> Unit) {
        if (!GitHubOAuthConfig.isConfigured) {
            emit(DeviceLoginEvent.Failed("OAuth Client ID が未設定です。BuildConfigOverrides.kt を確認してください。"))
            return
        }
        val deviceCode = try {
            requestDeviceCode()
        } catch (e: Exception) {
            Log.e(TAG, "device code request failed", e)
            emit(DeviceLoginEvent.Failed(e.message ?: "Device code の取得に失敗しました"))
            return
        }
        emit(DeviceLoginEvent.CodeReady(deviceCode.userCode, deviceCode.verificationUri, deviceCode.expiresInSeconds))

        var interval = deviceCode.pollIntervalSeconds.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + deviceCode.expiresInSeconds * 1000L
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            attempt += 1
            delay(interval * 1000L)
            emit(DeviceLoginEvent.Polling(attempt))
            val poll = try {
                pollAccessToken(deviceCode.deviceCode)
            } catch (e: Exception) {
                Log.w(TAG, "poll exception (continuing)", e)
                continue
            }
            val token = poll.accessToken
            if (!token.isNullOrBlank()) {
                tokenStore.save(token)
                val login = runCatching { userApi.me().body()?.login }.getOrNull()
                emit(DeviceLoginEvent.Success(token, login))
                return
            }
            when (poll.error) {
                "authorization_pending" -> { /* keep polling */ }
                "slow_down" -> {
                    interval = (poll.nextIntervalSeconds ?: (interval + 5)).coerceAtLeast(interval + 5)
                }
                "expired_token" -> {
                    emit(DeviceLoginEvent.Failed("コードの有効期限が切れました。もう一度お試しください。"))
                    return
                }
                "access_denied" -> {
                    emit(DeviceLoginEvent.Cancelled)
                    return
                }
                null -> { /* nothing — keep polling */ }
                else -> {
                    emit(DeviceLoginEvent.Failed(poll.errorDescription ?: poll.error))
                    return
                }
            }
        }
        emit(DeviceLoginEvent.Failed("コードの有効期限が切れました。もう一度お試しください。"))
    }

    suspend fun fetchCurrentUserLogin(): String? = runCatching { userApi.me().body()?.login }.getOrNull()

    fun savePat(token: String) = tokenStore.save(token)
    fun logout() = tokenStore.clear()

    private suspend fun requestDeviceCode(): DeviceCodeResponse {
        val res = authApi.requestDeviceCode(GitHubOAuthConfig.CLIENT_ID, GitHubOAuthConfig.SCOPE)
        if (!res.isSuccessful) throw IOException("HTTP ${res.code()} on /login/device/code")
        return res.body() ?: throw IOException("Empty body on /login/device/code")
    }

    private suspend fun pollAccessToken(deviceCode: String): AccessTokenResponse {
        val res = authApi.pollAccessToken(GitHubOAuthConfig.CLIENT_ID, deviceCode)
        if (!res.isSuccessful) throw IOException("HTTP ${res.code()} on /login/oauth/access_token")
        return res.body() ?: throw IOException("Empty body on /login/oauth/access_token")
    }

    companion object { private const val TAG = "AuthRepository" }
}
