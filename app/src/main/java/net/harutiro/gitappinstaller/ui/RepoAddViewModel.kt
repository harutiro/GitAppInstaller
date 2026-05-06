package net.harutiro.gitappinstaller.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.harutiro.gitappinstaller.AppContainerHolder
import net.harutiro.gitappinstaller.data.repository.RepoRepository
import net.harutiro.gitappinstaller.domain.RepoUrlParser

data class AddFormState(
    val url: String = "",
    val applicationId: String = "",
    val displayName: String = "",
    val urlError: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

class RepoAddViewModel(
    application: Application,
    private val repoRepository: RepoRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AddFormState())
    val state: StateFlow<AddFormState> = _state.asStateFlow()

    fun onUrlChange(v: String) = _state.update { it.copy(url = v, urlError = null, saved = false) }
    fun onAppIdChange(v: String) = _state.update { it.copy(applicationId = v, saved = false) }
    fun onDisplayNameChange(v: String) = _state.update { it.copy(displayName = v, saved = false) }

    fun submit() {
        val current = _state.value
        val parsed = RepoUrlParser.parse(current.url)
        if (parsed == null) {
            _state.update { it.copy(urlError = "Enter a public GitHub URL like https://github.com/owner/repo") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            repoRepository.add(
                host = parsed.host,
                owner = parsed.owner,
                repo = parsed.repo,
                applicationId = current.applicationId.ifBlank { null },
                displayName = current.displayName.ifBlank { null },
            )
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RepoAddViewModel(application, AppContainerHolder.get(application).repoRepository)
            }
        }
    }
}
