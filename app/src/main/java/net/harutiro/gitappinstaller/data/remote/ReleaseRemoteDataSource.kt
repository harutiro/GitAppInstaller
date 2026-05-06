package net.harutiro.gitappinstaller.data.remote

import net.harutiro.gitappinstaller.domain.ApkAsset
import net.harutiro.gitappinstaller.domain.ReleaseInfo
import java.io.IOException
import java.time.Instant

class RepoNotFoundException(message: String) : IOException(message)
class RateLimitedException(message: String) : IOException(message)
class NoReleaseException(message: String) : IOException(message)

class ReleaseRemoteDataSource(private val api: GitHubApi = GitHubServiceFactory.api) {

    suspend fun fetchLatestRelease(owner: String, repo: String): ReleaseInfo {
        val response = api.getLatestRelease(owner, repo)
        if (!response.isSuccessful) {
            when (response.code()) {
                404 -> throw RepoNotFoundException("Repository or release not found: $owner/$repo")
                403 -> throw RateLimitedException("GitHub rate limit reached. Try again later.")
                else -> throw IOException("HTTP ${response.code()} when fetching release for $owner/$repo")
            }
        }
        val dto = response.body() ?: throw NoReleaseException("Empty response body for $owner/$repo")
        return dto.toReleaseInfo()
    }

    private fun GitHubReleaseDto.toReleaseInfo(): ReleaseInfo {
        val versionName = tagName.removePrefix("v").removePrefix("V")
        val publishedAtMillis = runCatching {
            publishedAt?.let { Instant.parse(it).toEpochMilli() }
        }.getOrNull() ?: 0L
        val asset = pickApkAsset(assets)
        return ReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            publishedAtEpochMillis = publishedAtMillis,
            notes = body,
            apkAsset = asset,
        )
    }

    private fun pickApkAsset(assets: List<GitHubAssetDto>): ApkAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null
        val preferred = apks.firstOrNull { it.name.contains("arm64", ignoreCase = true) || it.name.contains("v8a", ignoreCase = true) }
            ?: apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: apks.first()
        return ApkAsset(
            name = preferred.name,
            downloadUrl = preferred.browserDownloadUrl,
            sizeBytes = preferred.size,
        )
    }
}
