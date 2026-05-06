package net.harutiro.gitappinstaller.installer

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

data class InstalledInfo(val installed: Boolean, val versionName: String?)

object InstalledPackageInfo {
    private const val TAG = "InstalledPackageInfo"

    fun lookup(context: Context, applicationId: String?): InstalledInfo {
        if (applicationId.isNullOrBlank()) return InstalledInfo(false, null)
        return try {
            val info = context.packageManager.getPackageInfo(applicationId, 0)
            Log.i(TAG, "lookup($applicationId) -> installed=true versionName=${info.versionName}")
            InstalledInfo(true, info.versionName)
        } catch (_: PackageManager.NameNotFoundException) {
            Log.w(TAG, "lookup($applicationId) -> NameNotFoundException")
            InstalledInfo(false, null)
        }
    }

    /** Backward-compatible helper. Returns null if the app isn't installed OR if it has no versionName. */
    fun installedVersionName(context: Context, applicationId: String?): String? =
        lookup(context, applicationId).versionName
}
