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

    /** Dernière liste complète (avant filtre « sur l'appareil ») pour re-filtrer sans recharger. */
    private var rawItems: List<SearchItemUi> = emptyList()

    init {
        viewModelScope.launch {
            category.flatMapLatest { cat ->
                if (cat == "FAV") libraryDao.observeFavorites() else libraryDao.observeByCategory(cat)
            }.collect { entries ->
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
        val shown = if (onDevice) {
            val ids = downloadsDao.completedMediaIds().toSet()
            rawItems.filter { it.id in ids }
        } else rawItems
        _uiState.value = _uiState.value.copy(category = category.value, items = shown)
    }

    fun onCategory(cat: String) { category.value = cat.uppercase() }

    fun setOnDeviceOnly(v: Boolean) {
        _uiState.value = _uiState.value.copy(onDeviceOnly = v)
        viewModelScope.launch { applyFilter() }
    }
}
