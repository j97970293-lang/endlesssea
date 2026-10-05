package dev.endlesssea.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.ui.search.SearchItemUi
import dev.endlesssea.data.db.LibraryDao
import dev.endlesssea.data.db.MediaDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val category: String = "FAV",
    val items: List<SearchItemUi> = emptyList(),
    /** Filtre « Sur l'appareil » : ne garder que les titres avec un fichier téléchargé. */
    val onDeviceOnly: Boolean = false,
    /** Filtre watchlist §29 : ALL ou un statut de suivi. */
    val filterStatus: String = "ALL",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryDao: LibraryDao,
    private val mediaDao: MediaDao,
    private val downloadsDao: dev.endlesssea.data.db.DownloadsDao,
) : ViewModel() {

    private val category = MutableStateFlow("FAV")
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    /** Dernière liste complète (avant filtrage) pour re-filtrer sans recharger. */
    private var rawItems: List<SearchItemUi> = emptyList()

    /** mediaId → statut watchlist (pour le filtre §29). */
    private var rawStatuses: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            category.flatMapLatest { cat ->
                if (cat == "FAV") libraryDao.observeFavorites() else libraryDao.observeByCategory(cat)
            }.collect { entries ->
                rawStatuses = entries.associate { it.mediaId to it.status }
                rawItems = entries.mapNotNull { entry ->
                    mediaDao.byId(entry.mediaId)?.let { media ->
                        SearchItemUi(
                            id = media.id, title = media.title,
                            posterUrl = media.posterUrl, bannerUrl = media.bannerUrl,
                            subtitle = media.type,
                        )
                    }
                }
                applyFilter()
            }
        }
    }

    /** Re-applique le filtre courant sur les éléments bruts. */
    private suspend fun applyFilter() {
        val onDevice = _uiState.value.onDeviceOnly
        var shown = if (onDevice) {
            val ids = downloadsDao.completedMediaIds().toSet()
            rawItems.filter { it.id in ids }
        } else rawItems
        val st = _uiState.value.filterStatus
        if (st != "ALL") shown = shown.filter { (rawStatuses[it.id] ?: "NONE") == st }
        _uiState.value = _uiState.value.copy(category = category.value, items = shown)
    }

    fun onCategory(cat: String) { category.value = cat.uppercase() }

    fun setOnDeviceOnly(v: Boolean) {
        _uiState.value = _uiState.value.copy(onDeviceOnly = v)
        viewModelScope.launch { applyFilter() }
    }

    fun setFilterStatus(status: String) {
        _uiState.value = _uiState.value.copy(filterStatus = status)
        viewModelScope.launch { applyFilter() }
    }
}
