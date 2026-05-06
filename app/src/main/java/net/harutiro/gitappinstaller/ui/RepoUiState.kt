package net.harutiro.gitappinstaller.ui

import net.harutiro.gitappinstaller.domain.ReleaseInfo
import net.harutiro.gitappinstaller.domain.TrackedRepo
import net.harutiro.gitappinstaller.domain.UpdateState

data class RepoUiState(
    val repo: TrackedRepo,
    val release: ReleaseInfo? = null,
    val installedVersion: String? = null,
    val state: UpdateState = UpdateState.UNKNOWN,
    val isChecking: Boolean = false,
    val isInstalling: Boolean = false,
    val errorMessage: String? = null,
)
