package net.harutiro.gitappinstaller.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

class ApkInstaller(private val context: Context) {

    fun canRequestInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * Reads the package name (applicationId) embedded in the APK file, without installing it.
     * Returns null if the file isn't a parseable APK.
     */
    fun readPackageNameFromApk(apkFile: File): String? {
        if (!apkFile.exists()) return null
        return runCatching {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)?.packageName
        }.getOrNull()
    }

    fun openUnknownSourcesSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Installs the given APK file using PackageInstaller. The OS will show a confirmation
     * dialog. Result is delivered via [InstallResultReceiver].
     */
    fun install(apkFile: File, displayName: String? = null) {
        Log.i(TAG, "install() called: file=${apkFile.absolutePath} size=${apkFile.length()} canRequest=${canRequestInstalls()}")
        if (!apkFile.exists() || apkFile.length() == 0L) {
            throw IllegalStateException("APK file missing or empty: ${apkFile.absolutePath}")
        }
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppLabel(displayName ?: apkFile.name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = pi.createSession(params)
        Log.i(TAG, "session created: id=$sessionId")
        val session = pi.openSession(sessionId)
        try {
            FileInputStream(apkFile).use { input ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            Log.i(TAG, "session written, committing")
            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = InstallResultReceiver.ACTION_INSTALL_RESULT
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pendingIntent.intentSender)
            Log.i(TAG, "session committed: id=$sessionId")
        } catch (t: Throwable) {
            Log.e(TAG, "install failed", t)
            runCatching { session.abandon() }
            throw t
        } finally {
            session.close()
        }
    }

    companion object { private const val TAG = "ApkInstaller" }

    /**
     * Launches the installed app identified by [applicationId].
     * Returns false if the package is not installed or has no launcher intent.
     */
    fun launchApp(applicationId: String?): Boolean {
        if (applicationId.isNullOrBlank()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(applicationId) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            Log.w(TAG, "launchApp failed for $applicationId", it)
            false
        }
    }

    /**
     * Fallback installer using ACTION_VIEW + FileProvider. Use only if PackageInstaller path fails.
     */
    fun installViaIntent(apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
