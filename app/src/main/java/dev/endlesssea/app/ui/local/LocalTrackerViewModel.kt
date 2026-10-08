package dev.endlesssea.app.ui.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.local.LocalMediaIds
import dev.endlesssea.app.tracking.TrackerRepository
import dev.endlesssea.app.tracking.TrackerSearchHit
import dev.endlesssea.data.db.TrackerLinkEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalTrackerViewModel @Inject constructor(private val trackers: TrackerRepository) : ViewModel() {
    val defaultService get() = trackers.defaultService
    val accounts = trackers.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val links = trackers.links.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _hits = MutableStateFlow<List<TrackerSearchHit>>(emptyList())
    val hits: StateFlow<List<TrackerSearchHit>> = _hits
    val busy = MutableStateFlow(false)
    val notice = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null

    fun clearSearch() { searchJob?.cancel(); _hits.value = emptyList(); busy.value = false }

    fun search(service: String, query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            busy.value = true
            _hits.value = trackers.search(service, query.trim())
            notice.value = if (_hits.value.isEmpty()) "Aucun résultat. Vérifie le titre, la connexion et le compte." else null
            busy.value = false
        }
    }

    fun link(folderUri: String, service: String, hit: TrackerSearchHit, autoMatch: Boolean, season: Int?) = viewModelScope.launch {
        val linked = trackers.link(LocalMediaIds.series(folderUri), service, hit, "WATCHING", autoMatch, season)
        if (!linked) { notice.value = "Rattachement annulé : progression distante non vérifiée. Réessayez."; return@launch }
        clearSearch()
        notice.value = "Rattaché : ${hit.title}"
    }

    fun unlink(folderUri: String) = viewModelScope.launch { trackers.unlink(LocalMediaIds.series(folderUri)) }
    fun setMatching(folderUri: String, enabled: Boolean, season: Int?) = viewModelScope.launch {
        trackers.setEpisodeMatching(LocalMediaIds.series(folderUri), enabled, season)
    }
    fun setProgress(link: TrackerLinkEntity, progress: Int) = viewModelScope.launch {
        val sent = trackers.setProgress(link.mediaId, progress)
        notice.value = if (sent) "Progression synchronisée" else "Progression conservée, synchronisation en attente"
    }
    fun next(link: TrackerLinkEntity) = setProgress(link, link.progress + 1)
}
