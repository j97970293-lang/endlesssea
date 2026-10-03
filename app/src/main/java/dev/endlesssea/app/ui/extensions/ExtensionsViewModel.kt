package dev.endlesssea.app.ui.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.data.db.ExtensionDao
import dev.endlesssea.data.db.ExtensionEntity
import dev.endlesssea.data.db.RepoDao
import dev.endlesssea.extensions.api.manifest.ExtensionManifest
import dev.endlesssea.extensions.api.manifest.RepoExtensionEntry
import dev.endlesssea.extensions.api.manifest.RepositoryIndex
import dev.endlesssea.extensions.loader.ExtensionLoader
import dev.endlesssea.extensions.loader.RepoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

data class RepoUi(val url: String, val name: String, val enabled: Boolean)
data class ExtensionUi(
    val pkg: String, val name: String, val version: Int, val versionName: String,
    val enabled: Boolean, val permissions: List<String>, val lastError: String?,
)
data class RepoEntryUi(val entry: RepoExtensionEntry, val repoUrl: String, val installedVersion: Int)

data class ExtensionsUiState(
    val repos: List<RepoUi> = emptyList(),
    val extensions: List<ExtensionUi> = emptyList(),
    val repoEntries: List<RepoEntryUi> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val repoDao: RepoDao,
    private val extensionDao: ExtensionDao,
    private val repoManager: RepoManager,
    private val loader: ExtensionLoader,
    private val registry: dev.endlesssea.extensions.loader.ExtensionRegistry,
) : ViewModel() {

    companion object {
        /** Dépôt de démonstration servi publiquement par le dépôt GitHub Endless Sea. */
        const val DEMO_REPO_URL =
            "https://raw.githubusercontent.com/j97970293-lang/endlesssea/main/demo-repo/index.json"
    }

    private val _uiState = MutableStateFlow(ExtensionsUiState())
    val uiState: StateFlow<ExtensionsUiState> = _uiState

    /** Derniers index synchronisés (mémoire — re-synchronisation manuelle possible). */
    private val cachedIndexes = linkedMapOf<String, RepositoryIndex>()

    init {
        viewModelScope.launch {
            repoDao.observeAll().collect { repos ->
                _uiState.value = _uiState.value.copy(
                    repos = repos.map { RepoUi(it.url, it.name, it.enabled) },
                )
            }
        }
        viewModelScope.launch {
            extensionDao.observeInstalled().collect { exts ->
                _uiState.value = _uiState.value.copy(
                    extensions = exts.map { it.toUi() },
                    repoEntries = buildEntries(exts),
                )
            }
        }
    }

    private fun ExtensionEntity.toUi() = ExtensionUi(
        pkg = pkg, name = name, version = version, versionName = versionName,
        enabled = status == "ENABLED",
        permissions = permissionsJson.removeSurrounding("[", "]").split(",")
            .map { p -> p.trim().removeSurrounding("\"") }.filter { it.isNotBlank() },
        lastError = lastError,
    )

    private fun buildEntries(installed: List<ExtensionEntity>): List<RepoEntryUi> {
        val versions = installed.associate { it.pkg to it.version }
        return cachedIndexes.entries.flatMap { (repoUrl, index) ->
            index.extensions.map { RepoEntryUi(it, repoUrl, versions[it.id] ?: 0) }
        }.sortedBy { it.entry.name }
    }

    // ------------------------------------------------------------------ dépôts

    fun addRepo(url: String) = viewModelScope.launch {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Collez l'adresse d'un index JSON (https://…/index.json)")
            return@launch
        }
        if (!trimmed.startsWith("http")) {
            _uiState.value = _uiState.value.copy(message = "Adresse invalide : elle doit commencer par http:// ou https://")
            return@launch
        }
        _uiState.value = _uiState.value.copy(busy = true)
        repoManager.addRepository(trimmed).fold(
            onSuccess = { index ->
                cachedIndexes[trimmed] = index
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    message = "Dépôt ajouté : ${index.name} (${index.extensions.size} extension(s))",
                    repoEntries = buildEntries(_uiState.value.extensions.toEntities()) ,
                )
            },
            onFailure = {
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    message = "Impossible d'ajouter ce dépôt : ${it.message ?: "erreur inconnue"}",
                )
            },
        )
    }

    /** L'écran garde la version « entité » actualisée pour recap simple. */
    private fun List<ExtensionUi>.toEntities() = map {
        ExtensionEntity(
            pkg = it.pkg, name = it.name, version = it.version, versionName = it.versionName,
            apiVersion = 1, status = if (it.enabled) "ENABLED" else "DISABLED",
        )
    }

    fun syncAll() = viewModelScope.launch {
        val repos = repoDao.enabled()
        if (repos.isEmpty()) {
            _uiState.value = _uiState.value.copy(message = "Aucun dépôt activé à synchroniser")
            return@launch
        }
        _uiState.value = _uiState.value.copy(busy = true)
        var ok = 0
        var failed = 0
        repos.forEach { repo ->
            when (val result = repoManager.sync(repo.url)) {
                is RepoManager.SyncResult.Fresh -> { cachedIndexes[repo.url] = result.index; ok++ }
                is RepoManager.SyncResult.NotModified -> ok++
                is RepoManager.SyncResult.Failed -> failed++
            }
        }
        _uiState.value = _uiState.value.copy(
            busy = false,
            repoEntries = buildEntries(_uiState.value.extensions.toEntities()),
            message = if (failed == 0) "Synchronisation terminée ($ok dépôt(s))" else "$ok synchronisé(s), $failed en échec (réseau ou URL invalide)",
        )
    }

    fun setRepoEnabled(url: String, enabled: Boolean) = viewModelScope.launch {
        repoDao.setEnabled(url, enabled)
        _uiState.value = _uiState.value.copy(message = if (enabled) "Dépôt activé" else "Dépôt désactivé")
    }

    fun removeRepo(url: String) = viewModelScope.launch {
        repoManager.removeRepository(url)
        cachedIndexes.remove(url)
        _uiState.value = _uiState.value.copy(message = "Dépôt supprimé")
    }

    // ------------------------------------------------------------------ install

    fun installEntry(entry: RepoExtensionEntry) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(busy = true)
        val pinned = extensionDao.byId(entry.id)?.certSha256
        val result = loader.install(entry.apkUrl, entry.sha256, pinned, locale())
        handleInstallResult(result)
    }

    fun installFromFile(src: File) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(busy = true)
        val result = loader.installLocal(src, locale())
        handleInstallResult(result)
    }

    private suspend fun handleInstallResult(result: ExtensionLoader.InstallResult) = when (result) {
        is ExtensionLoader.InstallResult.Rejected ->
            _uiState.value = _uiState.value.copy(
                busy = false,
                message = "Installation refusée : ${result.reason}",
            )
        is ExtensionLoader.InstallResult.Success -> {
            persistExtension(result.manifest)
            registry.invalidate(result.manifest.id)
            _uiState.value = _uiState.value.copy(
                busy = false,
                message = "« ${result.manifest.name} » v${result.manifest.versionName} installé — activez-le puis explorez !",
            )
        }
    }

    private suspend fun persistExtension(manifest: ExtensionManifest) {
        val escape: (String) -> String = { s -> s.replace("\"", "'") }
        val jsonList: (List<String>) -> String = { parts ->
            parts.joinToString(",", "[", "]") { p -> "\"${escape(p)}\"" }
        }
        val previous = extensionDao.byId(manifest.id)
        val description = manifest.description["fr"] ?: manifest.description.values.firstOrNull() ?: ""
        extensionDao.upsert(
            ExtensionEntity(
                pkg = manifest.id, name = manifest.name,
                version = manifest.version, versionName = manifest.versionName,
                apiVersion = manifest.apiVersion,
                author = manifest.author.name,
                descriptionJson = "{\"fr\":\"${escape(description)}\"}",
                languagesJson = jsonList(manifest.languages),
                typesJson = jsonList(manifest.types),
                capabilitiesJson = "{\"search\":${manifest.capabilities.search},\"servers\":${manifest.capabilities.servers}}",
                repoUrl = null,
                status = previous?.status ?: "ENABLED",
                trust = "USER_INSTALLED",
                permissionsJson = jsonList(manifest.permissions),
                certSha256 = previous?.certSha256,
                packageSha256 = "",
                lastError = null,
            ),
        )
    }

    fun setExtensionEnabled(pkg: String, enabled: Boolean) = viewModelScope.launch {
        extensionDao.setStatus(pkg, if (enabled) "ENABLED" else "DISABLED")
        registry.invalidate(pkg)
    }

    fun uninstall(pkg: String) = viewModelScope.launch {
        extensionDao.delete(pkg)
        registry.invalidate(pkg)
        _uiState.value = _uiState.value.copy(message = "Extension désinstallée")
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }

    private fun locale(): String = Locale.getDefault().language
}
