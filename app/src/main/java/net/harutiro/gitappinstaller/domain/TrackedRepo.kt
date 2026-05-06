package net.harutiro.gitappinstaller.domain

import kotlinx.serialization.Serializable

@Serializable
data class TrackedRepo(
    val id: Long,
    val host: GitHost,
    val owner: String,
    val repo: String,
    val applicationId: String?,
    val displayName: String,
) {
    val fullName: String get() = "$owner/$repo"
}
