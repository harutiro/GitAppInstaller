package net.harutiro.gitappinstaller.domain

data class ApkAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val publishedAtEpochMillis: Long,
    val notes: String?,
    val apkAsset: ApkAsset?,
)

enum class UpdateState { UNKNOWN, UP_TO_DATE, UPDATE_AVAILABLE, NOT_INSTALLED, NO_APK }
