package net.harutiro.gitappinstaller.data.repository

import kotlinx.coroutines.flow.StateFlow
import net.harutiro.gitappinstaller.domain.GitHost
import net.harutiro.gitappinstaller.domain.TrackedRepo

class RepoRepository(private val store: TrackedRepoStore) {
    val items: StateFlow<List<TrackedRepo>> get() = store.items

    suspend fun reload() = store.reload()

    suspend fun add(host: GitHost, owner: String, repo: String, applicationId: String?, displayName: String?): TrackedRepo {
        val tracked = TrackedRepo(
            id = 0L,
            host = host,
            owner = owner,
            repo = repo,
            applicationId = applicationId?.takeIf { it.isNotBlank() },
            displayName = displayName?.takeIf { it.isNotBlank() } ?: "$owner/$repo",
        )
        store.add(tracked)
        return tracked
    }

    suspend fun update(repo: TrackedRepo) = store.update(repo)
    suspend fun remove(id: Long) = store.remove(id)
}
