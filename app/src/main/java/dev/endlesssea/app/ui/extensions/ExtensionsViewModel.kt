package dev.endlesssea.app.ui.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.data.db.ExtensionDao
import dev.endlesssea.data.db.RepoDao
import dev.endlesssea.extensions.loader.RepoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepoUi(val url: String, val name: String, val enabled: Boolean)
data class ExtensionUi(
    val pkg: String, val name: String, val versionName: String,
    val enabled: Boolean, val permissions: List<String>, val lastError: String?,
)

data class ExtensionsUiState(
    val repos: List<RepoUi> = emptyList(),
    val extensions: List<ExtensionUi> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val repoDao: RepoDao,
    private val extensionDao: ExtensionDao,
    private val repoManager: RepoManager,
    private val registry: dev.endlesssea.extensions.loader.ExtensionRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState

    init {
        viewModelScope.launch {
            repoDao.observeAll().collect { repos ->
                _uiState.value = _uiState.value.copy(
                    repos = repos.map { RepoUi(it.url, it.name, it.enabled) })
            }
        }
        viewModelScope.launch {
            extensionDao.observeInstalled().collect { exts ->
                _uiState.value = _uiState.value.copy(
                    extensions = exts.map {
                        ExtensionUi(
                            pkg = it.pkg, name = it.name, versionName = it.versionName,
                            enabled = it.status == "ENABLED",
                            permissions = it.permissionsJson
                                .removeSurrounding("[", "]")
                                .split(",")
                                .map { p -> p.trim().removeSurrounding("\"") }
                                .filter { p -> p.isNotBlank() },
                            lastError = it.lastError,
                        )
                    })
            }
        }
    }

    fun addRepo(url: String) = viewModelScope.launch {
        if (url.isBlank()) return@launch
        _uiState.value = _uiState.value.copy(busy = true)
        repoManager.addRepository(url).fold(
            onSuccess = { _uiState.value = _uiState.value.copy(busy = false, message = "Dépôt ajouté : ${it.name}") },
            onFailure = { _uiState.value = _uiState.value.copy(busy = false, message = "Échec : ${it.message}") },
        )
    }

    fun setRepoEnabled(url: String, enabled: Boolean) = viewModelScope.launch {
        repoDao.setEnabled(url, enabled)
    }

    fun setExtensionEnabled(pkg: String, enabled: Boolean) = viewModelScope.launch {
        extensionDao.setStatus(pkg, if (enabled) "ENABLED" else "DISABLED")
        registry.invalidate(pkg)
    }
}
