package net.harutiro.gitappinstaller.installer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface InstallResult {
    data object Success : InstallResult
    data class Failure(val message: String) : InstallResult
    data class PendingUserAction(val intent: Intent) : InstallResult
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive action=${intent.action} status=${intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)} msg=${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        if (intent.action != ACTION_INSTALL_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        val result: InstallResult = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    Log.i(TAG, "launching system confirm activity: $confirmIntent")
                    runCatching { context.startActivity(confirmIntent) }
                        .onFailure { Log.e(TAG, "startActivity for confirm failed", it) }
                    InstallResult.PendingUserAction(confirmIntent)
                } else {
                    Log.w(TAG, "PENDING_USER_ACTION but EXTRA_INTENT was null")
                    InstallResult.Failure("Pending user action but no intent provided")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "install success")
                InstallResult.Success
            }
            else -> {
                Log.w(TAG, "install failed status=$status msg=$message")
                InstallResult.Failure("Install failed (status=$status): $message")
            }
        }
        scope.launch { _events.emit(result) }
    }

    companion object {
        private const val TAG = "InstallResultReceiver"
        const val ACTION_INSTALL_RESULT = "net.harutiro.gitappinstaller.INSTALL_RESULT"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val _events = MutableSharedFlow<InstallResult>(extraBufferCapacity = 8)
        val events: SharedFlow<InstallResult> = _events.asSharedFlow()
    }
}
