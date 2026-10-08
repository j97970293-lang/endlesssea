package dev.endlesssea.app.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.app.tracking.TrackerRegistry
import dev.endlesssea.app.tracking.TrackerRepository
import dev.endlesssea.data.db.TrackerAccountEntity
import dev.endlesssea.data.db.TrackerLinkEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackersUiState(
    val accounts: List<TrackerAccountEntity> = emptyList(),
    val links: List<TrackerLinkEntity> = emptyList(),
    /** Service dont le formulaire est ouvert (null = aucun). */
    val editing: String? = null,
    /** Message d'état (succès ou erreur) affiché en haut de l'écran. */
    val notice: String? = null,
    val busy: Boolean = false,
    val autoMark: Boolean = true,
    val enrichTmdb: Boolean = true,
)

/**
 * §suivi (conversation 11) — écran « Comptes & suivi » : brancher AniList,
 * MyAnimeList, Shikimori et TMDB, voir les fiches rattachées et rejouer les
 * mises à jour en attente.
 */
@HiltViewModel
class TrackersViewModel @Inject constructor(
    private val repo: TrackerRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackersUiState())
    val uiState: StateFlow<TrackersUiState> = _uiState

    init {
        viewModelScope.launch {
            repo.accounts.collect { list ->
                _uiState.value = _uiState.value.copy(accounts = list)
            }
        }
        viewModelScope.launch {
            repo.links.collect { list -> _uiState.value = _uiState.value.copy(links = list) }
        }
        viewModelScope.launch {
            prefs.autoMarkWatched.collect { v -> _uiState.value = _uiState.value.copy(autoMark = v) }
        }
        viewModelScope.launch {
            prefs.enrichWithTmdb.collect { v -> _uiState.value = _uiState.value.copy(enrichTmdb = v) }
        }
        // Une mise à jour en attente (épisode vu hors ligne) part dès l'ouverture.
        viewModelScope.launch {
            val count = runCatching { repo.syncPending() }.getOrDefault(0)
            if (count > 0) {
                _uiState.value = _uiState.value.copy(
                    notice = "$count mise(s) à jour synchronisée(s) avec les services.",
                )
            }
        }
    }

    val defaultService get() = repo.defaultService
    fun setDefaultService(service: String) = viewModelScope.launch { repo.setDefaultService(service) }

    val services: List<String> = TrackerRegistry.LABELS.keys.toList()

    fun label(id: String): String = TrackerRegistry.LABELS[id] ?: id
    fun hint(id: String): String = TrackerRegistry.HINTS[id].orEmpty()

    fun account(id: String): TrackerAccountEntity? =
        _uiState.value.accounts.firstOrNull { it.service == id }

    fun linksFor(id: String): List<TrackerLinkEntity> =
        _uiState.value.links.filter { it.service == id }

    fun openForm(id: String) { _uiState.value = _uiState.value.copy(editing = id, notice = null) }
    fun closeForm() { _uiState.value = _uiState.value.copy(editing = null) }

    fun setAutoMark(v: Boolean) = prefs.setAutoMarkWatched(v)
    fun setEnrichTmdb(v: Boolean) = prefs.setEnrichWithTmdb(v)
    fun clearNotice() { _uiState.value = _uiState.value.copy(notice = null) }

    /** Connexion : le jeton (ou la clé TMDB) est vérifié avant d'être conservé. */
    fun connect(id: String, token: String, apiKey: String, clientId: String, refreshToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, notice = "Vérification…")
            val name = repo.connect(
                id = id,
                token = token.ifBlank { null },
                apiKey = apiKey.ifBlank { null },
                clientId = clientId.ifBlank { null },
                refreshToken = refreshToken.ifBlank { null },
            )
            _uiState.value = _uiState.value.copy(
                busy = false,
                editing = if (name == null) id else null,
                notice = if (name != null) {
                    "${label(id)} connecté en tant que $name."
                } else {
                    "${label(id)} : identifiants refusés ou service injoignable."
                },
            )
            if (name != null) repo.syncPending()
        }
    }

    fun disconnect(id: String) {
        viewModelScope.launch {
            repo.disconnect(id)
            _uiState.value = _uiState.value.copy(notice = "${label(id)} déconnecté.")
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repo.setEnabled(id, enabled) }
    }

    fun unlink(mediaId: String) {
        viewModelScope.launch {
            repo.unlink(mediaId)
            _uiState.value = _uiState.value.copy(notice = "Rattachement supprimé.")
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, notice = "Synchronisation…")
            val count = runCatching { repo.syncPending() }.getOrDefault(0)
            _uiState.value = _uiState.value.copy(
                busy = false,
                notice = if (count == 0) "Tout est à jour." else "$count mise(s) à jour envoyée(s).",
            )
        }
    }
}
