package dev.endlesssea.app.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.app.tracking.TrackerAccount
import dev.endlesssea.app.tracking.TrackerCredentials
import dev.endlesssea.app.tracking.TrackerId
import dev.endlesssea.app.tracking.TrackerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État de l'écran « Comptes & suivi ». */
data class TrackersUiState(
    val accounts: Map<TrackerId, TrackerAccount> = emptyMap(),
    val status: String = "",
    val busy: TrackerId? = null,
    /** Champs saisis par service (jamais persistés avant validation). */
    val drafts: Map<TrackerId, TrackerCredentials> = emptyMap(),
    val autoMark: Boolean = true,
    val enrichTmdb: Boolean = true,
)

@HiltViewModel
class TrackersViewModel @Inject constructor(
    private val repo: TrackerRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackersUiState())
    val uiState: StateFlow<TrackersUiState> = _uiState

    init {
        viewModelScope.launch {
            repo.accounts.collect { acc -> _uiState.value = _uiState.value.copy(accounts = acc) }
        }
        viewModelScope.launch {
            repo.status.collect { s -> _uiState.value = _uiState.value.copy(status = s) }
        }
        viewModelScope.launch {
            prefs.autoMarkWatched.collect { v -> _uiState.value = _uiState.value.copy(autoMark = v) }
        }
        viewModelScope.launch {
            prefs.enrichWithTmdb.collect { v -> _uiState.value = _uiState.value.copy(enrichTmdb = v) }
        }
        // pré-remplit les champs avec ce qui est déjà enregistré (jeton masqué)
        _uiState.value = _uiState.value.copy(
            drafts = TrackerId.entries.associateWith { repo.credentials(it) },
        )
    }

    fun setAutoMark(v: Boolean) = prefs.setAutoMarkWatched(v)
    fun setEnrichTmdb(v: Boolean) = prefs.setEnrichWithTmdb(v)
    fun clearStatus() { /* le statut disparaît au prochain message */ }

    fun draft(id: TrackerId): TrackerCredentials = _uiState.value.drafts[id] ?: TrackerCredentials()

    /** Met à jour un champ du brouillon (token, clé, identifiant client…). */
    fun updateDraft(id: TrackerId, transform: (TrackerCredentials) -> TrackerCredentials) {
        val next = _uiState.value.drafts.toMutableMap()
        next[id] = transform(next[id] ?: TrackerCredentials())
        _uiState.value = _uiState.value.copy(drafts = next)
    }

    /** Enregistre puis vérifie les identifiants saisis (bouton « Connecter »). */
    fun connect(id: TrackerId) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = id)
            val creds = draft(id)
            repo.saveCredentials(id, creds, "")
            repo.verify(id, creds)
            _uiState.value = _uiState.value.copy(
                busy = null, drafts = _uiState.value.drafts + (id to repo.credentials(id)),
            )
        }
    }

    /**
     * Ouvre la page d'autorisation du service (OAuth). L'appelant lance
     * l'intent ; on prépare les identifiants nécessaires (dont le PKCE MAL).
     */
    fun authorizationUrl(id: TrackerId): String {
        val d = draft(id)
        return repo.authorizeUrl(id, d.clientId.trim(), d.clientSecret.trim())
    }

    /** Échange le code d'autorisation collé par l'utilisateur contre un jeton. */
    fun completeAuthorization(id: TrackerId, code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = id)
            repo.completeAuthorization(id, code)
            _uiState.value = _uiState.value.copy(
                busy = null, drafts = _uiState.value.drafts + (id to repo.credentials(id)),
            )
        }
    }

    fun disconnect(id: TrackerId) {
        repo.disconnect(id)
        updateDraft(id) { TrackerCredentials() }
    }
}
