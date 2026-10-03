package dev.endlesssea.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.ui.search.SearchItemUi
import dev.endlesssea.data.db.LibraryDao
import dev.endlesssea.data.db.MediaDao
import dev.endlesssea.data.db.WatchHistoryDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val featured: List<SearchItemUi> = emptyList(),
    val continueWatching: List<SearchItemUi> = emptyList(),
    val recent: List<SearchItemUi> = emptyList(),
    val favorites: List<SearchItemUi> = emptyList(),
    val bannerAutoScroll: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val libraryDao: LibraryDao,
    private val historyDao: WatchHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        observeHome()
    }

    private fun observeHome() = viewModelScope.launch {
        mediaDao.recent(20).collect { recent ->
            _uiState.value = _uiState.value.copy(
                loading = false,
                featured = recent.take(6).map { it.toUi() },
                recent = recent.map { it.toUi() },
            )
        }
    }

    private fun dev.endlesssea.data.db.MediaEntity.toUi() = SearchItemUi(
        id = id, title = title, posterUrl = posterUrl, bannerUrl = bannerUrl,
        subtitle = synopsis?.take(140),
    )

    fun onPlay(mediaId: String) { /* résolution des liens → lecteur (feuille de route §14) */ }
    fun onAddToLibrary(mediaId: String) = viewModelScope.launch {
        libraryDao.upsert(dev.endlesssea.data.db.LibraryEntity(mediaId = mediaId, category = "ANIME"))
    }
    fun onDownload(mediaId: String) { /* feuille « Télécharger » : serveur × qualité (doc 06 §9) */ }
}
