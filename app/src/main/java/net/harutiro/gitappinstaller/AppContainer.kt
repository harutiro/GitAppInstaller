package net.harutiro.gitappinstaller

import android.content.Context
import net.harutiro.gitappinstaller.data.auth.AuthRepository
import net.harutiro.gitappinstaller.data.auth.TokenStore
import net.harutiro.gitappinstaller.data.remote.GitHubServiceFactory
import net.harutiro.gitappinstaller.data.remote.ReleaseRemoteDataSource
import net.harutiro.gitappinstaller.data.repository.RepoRepository
import net.harutiro.gitappinstaller.data.repository.TrackedRepoStore
import net.harutiro.gitappinstaller.installer.ApkDownloader
import net.harutiro.gitappinstaller.installer.ApkInstaller

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val tokenStore: TokenStore by lazy { TokenStore(appContext) }
    val gitHubFactory: GitHubServiceFactory by lazy { GitHubServiceFactory(tokenStore) }
    val repoRepository: RepoRepository by lazy { RepoRepository(TrackedRepoStore(appContext)) }
    val releaseDataSource: ReleaseRemoteDataSource by lazy { ReleaseRemoteDataSource(gitHubFactory.api) }
    val authRepository: AuthRepository by lazy {
        AuthRepository(gitHubFactory.authApi, gitHubFactory.userApi, tokenStore)
    }
    val apkDownloader: ApkDownloader by lazy { ApkDownloader(appContext) }
    val apkInstaller: ApkInstaller by lazy { ApkInstaller(appContext) }
}

object AppContainerHolder {
    @Volatile private var instance: AppContainer? = null
    fun get(context: Context): AppContainer {
        return instance ?: synchronized(this) {
            instance ?: AppContainer(context).also { instance = it }
        }
    }
}
