package net.harutiro.gitappinstaller.data.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(readToken())
    val token: StateFlow<String?> = _token.asStateFlow()

    fun current(): String? = _token.value

    fun save(value: String) {
        val trimmed = value.trim()
        prefs.edit().putString(KEY_TOKEN, trimmed).apply()
        _token.value = trimmed.takeIf { it.isNotBlank() }
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _token.value = null
    }

    private fun readToken(): String? =
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    companion object {
        private const val PREFS = "git_app_installer_auth"
        private const val KEY_TOKEN = "github_token"
    }
}
