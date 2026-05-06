package net.harutiro.gitappinstaller.installer

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object InstalledPackageInfo {
    private const val TAG = "InstalledPackageInfo"

    fun installedVersionName(context: Context, applicationId: String?): String? {
        if (applicationId.isNullOrBlank()) return null
        return try {
            val info = context.packageManager.getPackageInfo(applicationId, 0)
            Log.i(TAG, "lookup($applicationId) -> versionName=${info.versionName}")
            info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "lookup($applicationId) -> NameNotFoundException")
            null
        }
    }
}
