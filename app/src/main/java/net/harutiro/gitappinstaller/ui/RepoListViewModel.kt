package net.harutiro.gitappinstaller.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.harutiro.gitappinstaller.AppContainerHolder
import net.harutiro.gitappinstaller.data.remote.NoReleaseException
import net.harutiro.gitappinstaller.data.remote.RateLimitedException
import net.harutiro.gitappinstaller.data.remote.ReleaseRemoteDataSource
import net.harutiro.gitappinstaller.data.remote.RepoNotFoundException
import net.harutiro.gitappinstaller.data.repository.RepoRepository
import net.harutiro.gitappinstaller.domain.UpdateState
import net.harutiro.gitappinstaller.domain.VersionComparator
import net.harutiro.gitappinstaller.installer.ApkDownloader
import net.harutiro.gitappinstaller.installer.ApkInstaller
import net.harutiro.gitappinstaller.installer.DownloadEvent
import net.harutiro.gitappinstaller.installer.InstallResult
import net.harutiro.gitappinstaller.installer.InstallResultReceiver
import net.harutiro.gitappinstaller.installer.InstalledPackageInfo
import java.io.IOException

class RepoListViewModel(
    private val app: Application,
    private val repoRepository: RepoRepository,
    private val releaseDataSource: ReleaseRemoteDataSource,
    private val apkDownloader: ApkDownloader,
    private val apkInstaller: ApkInstaller,
) : AndroidViewModel(app) {

    private val _items = MutableStateFlow<List<RepoUiState>>(emptyList())
    val items: StateFlow<List<RepoUiState>> = _items.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repoRepository.items.collectLatest { repos ->
                val previous = _items.value.associateBy { it.repo.id }
                val next = repos.map { tr ->
                    previous[tr.id]?.copy(repo = tr) ?: RepoUiState(repo = tr).withInstalledVersion()
                }
                _items.value = next
                // Auto-refresh anything that hasn't been checked yet — including the initial
                // load on app start and any newly-added repo from the add screen.
                next.filter { it.state == UpdateState.UNKNOWN && !it.isChecking }
                    .forEach { refresh(it.repo.id) }
            }
        }
        viewModelScope.launch {
            InstallResultReceiver.events.collect { result ->
                when (result) {
                    is InstallResult.Success -> updateAll { it.copy(isInstalling = false, errorMessage = null) }
                    is InstallResult.Failure -> updateAll { it.copy(isInstalling = false, errorMessage = result.message) }
                    is InstallResult.PendingUserAction -> { /* OS dialog will appear */ }
                }
                refreshAll()
            }
        }
    }

    private fun RepoUiState.withInstalledVersion(): RepoUiState =
        copy(installedVersion = InstalledPackageInfo.installedVersionName(app, repo.applicationId))

    fun refreshAll() {
        _items.value.forEach { refresh(it.repo.id) }
    }

    fun refresh(id: Long) {
        val target = _items.value.firstOrNull { it.repo.id == id } ?: return
        viewModelScope.launch {
            mutate(id) { it.copy(isChecking = true, errorMessage = null) }
            try {
                val release = releaseDataSource.fetchLatestRelease(target.repo.owner, target.repo.repo)
                val installed = InstalledPackageInfo.installedVersionName(app, target.repo.applicationId)
                val state = computeState(release.apkAsset != null, target.repo.applicationId, installed, release.versionName)
                Log.i(TAG, "refresh($id) appId=${target.repo.applicationId} installed=$installed latest=${release.versionName} -> $state")
                mutate(id) { it.copy(release = release, installedVersion = installed, state = state, isChecking = false) }
            } catch (e: RepoNotFoundException) {
                mutate(id) { it.copy(isChecking = false, errorMessage = "Repository not found (Public only)") }
            } catch (e: RateLimitedException) {
                mutate(id) { it.copy(isChecking = false, errorMessage = "GitHub rate limit reached") }
            } catch (e: NoReleaseException) {
                mutate(id) { it.copy(isChecking = false, errorMessage = "No release published yet") }
            } catch (e: IOException) {
                mutate(id) { it.copy(isChecking = false, errorMessage = e.message ?: "Network error") }
            } catch (e: Exception) {
                mutate(id) { it.copy(isChecking = false, errorMessage = e.message ?: "Unexpected error") }
            }
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repoRepository.remove(id) }
    }

    private val installJobs = mutableMapOf<Long, Job>()

    fun install(id: Long) {
        Log.i(TAG, "install($id) tapped")
        val target = _items.value.firstOrNull { it.repo.id == id }
        if (target == null) {
            Log.w(TAG, "install: no repo with id=$id")
            return
        }
        val asset = target.release?.apkAsset
        if (asset == null) {
            Log.w(TAG, "install: no apk asset; release=${target.release}")
            mutate(id) { it.copy(errorMessage = "No APK asset on the latest release. Tap Refresh first.") }
            return
        }
        if (!apkInstaller.canRequestInstalls()) {
            Log.w(TAG, "install: REQUEST_INSTALL_PACKAGES not granted; opening settings")
            viewModelScope.launch { _events.emit(UiEvent.RequestInstallPermission) }
            mutate(id) {
                it.copy(errorMessage = "「不明なアプリのインストール」を有効にしてから、もう一度Installを押してください")
            }
            return
        }
        installJobs[id]?.cancel()
        installJobs[id] = viewModelScope.launch {
            mutate(id) { it.copy(isInstalling = true, errorMessage = null) }
            Log.i(TAG, "install: downloading ${asset.downloadUrl}")
            apkDownloader.download(asset.downloadUrl, asset.name).collect { ev ->
                when (ev) {
                    is DownloadEvent.Progress -> { /* could surface progress in UI later */ }
                    is DownloadEvent.Completed -> {
                        Log.i(TAG, "install: download complete, starting installer")
                        // Always trust the APK's actual packageName, since the user-supplied
                        // applicationId could be missing or wrong.
                        val pkg = apkInstaller.readPackageNameFromApk(ev.file)
                        if (!pkg.isNullOrBlank() && pkg != target.repo.applicationId) {
                            Log.i(TAG, "install: applicationId ${target.repo.applicationId} -> $pkg")
                            runCatching { repoRepository.update(target.repo.copy(applicationId = pkg)) }
                        }
                        try {
                            apkInstaller.install(ev.file, target.repo.displayName)
                        } catch (t: Throwable) {
                            Log.e(TAG, "install: ApkInstaller.install threw", t)
                            mutate(id) { it.copy(isInstalling = false, errorMessage = t.message ?: "Install failed") }
                        }
                    }
                    is DownloadEvent.Failed -> {
                        Log.w(TAG, "install: download failed: ${ev.reason}")
                        mutate(id) { it.copy(isInstalling = false, errorMessage = "Download failed: ${ev.reason}") }
                    }
                }
            }
        }
    }

    fun openInstallPermissionSettings() {
        apkInstaller.openUnknownSourcesSettings()
    }

    fun openApp(id: Long) {
        val target = _items.value.firstOrNull { it.repo.id == id } ?: return
        val launched = apkInstaller.launchApp(target.repo.applicationId)
        if (!launched) {
            mutate(id) { it.copy(errorMessage = "このアプリを起動できませんでした（applicationIdを確認してください）") }
        }
    }

    private fun computeState(hasApk: Boolean, applicationId: String?, installed: String?, latest: String): UpdateState {
        if (!hasApk) return UpdateState.NO_APK
        if (applicationId.isNullOrBlank() || installed == null) return UpdateState.NOT_INSTALLED
        return if (VersionComparator.isNewer(latest, installed)) UpdateState.UPDATE_AVAILABLE else UpdateState.UP_TO_DATE
    }

    private fun mutate(id: Long, transform: (RepoUiState) -> RepoUiState) {
        _items.update { list -> list.map { if (it.repo.id == id) transform(it) else it } }
    }

    private fun updateAll(transform: (RepoUiState) -> RepoUiState) {
        _items.update { list -> list.map(transform) }
    }

    sealed interface UiEvent {
        data object RequestInstallPermission : UiEvent
    }

    companion object {
        private const val TAG = "RepoListVM"
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = AppContainerHolder.get(application)
                RepoListViewModel(
                    application,
                    container.repoRepository,
                    container.releaseDataSource,
                    container.apkDownloader,
                    container.apkInstaller,
                )
            }
        }
    }
}
