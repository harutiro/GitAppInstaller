package net.harutiro.gitappinstaller.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.harutiro.gitappinstaller.AppContainerHolder
import net.harutiro.gitappinstaller.GitHubOAuthConfig
import net.harutiro.gitappinstaller.data.auth.AuthRepository
import net.harutiro.gitappinstaller.data.auth.DeviceLoginEvent
import net.harutiro.gitappinstaller.data.auth.TokenStore

data class SettingsState(
    val loggedIn: Boolean = false,
    val login: String? = null,
    val oauthConfigured: Boolean = GitHubOAuthConfig.isConfigured,
    val isLoggingIn: Boolean = false,
    val deviceCode: String? = null,
    val verificationUri: String? = null,
    val pollingMessage: String? = null,
    val errorMessage: String? = null,
    val patInput: String = "",
)

class SettingsViewModel(
    application: Application,
    private val tokenStore: TokenStore,
    private val authRepository: AuthRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SettingsState(loggedIn = !tokenStore.current().isNullOrBlank()))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var loginJob: Job? = null

    init {
        if (_state.value.loggedIn) {
            viewModelScope.launch {
                val login = authRepository.fetchCurrentUserLogin()
                _state.update { it.copy(login = login) }
            }
        }
    }

    fun onPatChange(value: String) = _state.update { it.copy(patInput = value, errorMessage = null) }

    fun savePat() {
        val pat = _state.value.patInput.trim()
        if (pat.isBlank()) {
            _state.update { it.copy(errorMessage = "Token を入力してください") }
            return
        }
        authRepository.savePat(pat)
        _state.update { it.copy(loggedIn = true, patInput = "", errorMessage = null) }
        viewModelScope.launch {
            val login = authRepository.fetchCurrentUserLogin()
            _state.update { it.copy(login = login) }
        }
    }

    fun startDeviceLogin() {
        if (loginJob?.isActive == true) return
        if (!GitHubOAuthConfig.isConfigured) {
            _state.update { it.copy(errorMessage = "OAuth Client ID が未設定です。BuildConfigOverrides.kt を確認してください。") }
            return
        }
        _state.update {
            it.copy(
                isLoggingIn = true,
                errorMessage = null,
                deviceCode = null,
                verificationUri = null,
                pollingMessage = "コードを取得中…",
            )
        }
        loginJob = viewModelScope.launch {
            authRepository.startDeviceFlow { event ->
                when (event) {
                    is DeviceLoginEvent.CodeReady -> _state.update {
                        it.copy(
                            deviceCode = event.userCode,
                            verificationUri = event.verificationUri,
                            pollingMessage = "ブラウザで認証してください",
                        )
                    }
                    is DeviceLoginEvent.Polling -> _state.update {
                        it.copy(pollingMessage = "認証完了を待機中… (#${event.attempt})")
                    }
                    is DeviceLoginEvent.Success -> _state.update {
                        it.copy(
                            isLoggingIn = false,
                            loggedIn = true,
                            login = event.login,
                            deviceCode = null,
                            verificationUri = null,
                            pollingMessage = null,
                        )
                    }
                    is DeviceLoginEvent.Failed -> {
                        Log.w(TAG, "device login failed: ${event.message}")
                        _state.update {
                            it.copy(
                                isLoggingIn = false,
                                deviceCode = null,
                                verificationUri = null,
                                pollingMessage = null,
                                errorMessage = event.message,
                            )
                        }
                    }
                    DeviceLoginEvent.Cancelled -> _state.update {
                        it.copy(
                            isLoggingIn = false,
                            deviceCode = null,
                            verificationUri = null,
                            pollingMessage = null,
                            errorMessage = "ユーザーがキャンセルしました",
                        )
                    }
                }
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        _state.update {
            it.copy(
                isLoggingIn = false,
                deviceCode = null,
                verificationUri = null,
                pollingMessage = null,
            )
        }
    }

    fun logout() {
        loginJob?.cancel()
        authRepository.logout()
        _state.update {
            SettingsState(loggedIn = false, oauthConfigured = GitHubOAuthConfig.isConfigured)
        }
    }

    companion object {
        private const val TAG = "SettingsVM"

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = AppContainerHolder.get(application)
                SettingsViewModel(application, container.tokenStore, container.authRepository)
            }
        }
    }
}
