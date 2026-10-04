package dev.endlesssea.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.ui.search.SearchItemUi
import dev.endlesssea.data.db.LibraryDao
import dev.endlesssea.data.db.MediaDao
import dev.endlesssea.data.db.WatchHistoryDao
import dev.endlesssea.extensions.api.model.MainPageRequest
import dev.endlesssea.extensions.loader.ExtensionRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeRowUi(val title: String, val items: List<SearchItemUi>)

data class HomeUiState(
    val loading: Boolean = true,
    val featured: List<SearchItemUi> = emptyList(),
    val continueWatching: List<SearchItemUi> = emptyList(),
    val recent: List<SearchItemUi> = emptyList(),
    val favorites: List<SearchItemUi> = emptyList(),
    val remoteRows: List<HomeRowUi> = emptyList(),
    val bannerAutoScroll: Boolean = true,
    val extensionCount: Int = 0,
)

/**
 * Accueil (spec §10) : bannière « à la une » + rangées.
 * Contenu réel = catalogues des extensions activées (getMainPage) +
 * sections locales (reprise, favoris, cache récent).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val libraryDao: LibraryDao,
    private val historyDao: WatchHistoryDao,
    private val registry: ExtensionRegistry,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        observeLocal()
        loadRemote()
    }

    // ---------- Sources locales (fonctionnent hors-ligne dès le premier usage)
    private fun observeLocal() {
        viewModelScope.launch {
            mediaDao.recent(20).collect { recent ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    recent = recent.map { it.toUi() },
                    featured = _uiState.value.featured.ifEmpty { recent.take(6).map { it.toUi() } },
                )
            }
        }
        viewModelScope.launch {
            historyDao.observeContinueWatching(12).collect { history ->
                val items = history.mapNotNull { h ->
                    mediaDao.byId(h.mediaId)?.let { media ->
                        val pct = if (h.durationMs > 0) (h.positionMs * 100 / h.durationMs).toInt() else 0
                        media.toUi().copy(subtitle = "Reprise à $pct %")
                    }
                }
                _uiState.value = _uiState.value.copy(continueWatching = items)
            }
        }
        viewModelScope.launch {
            libraryDao.observeFavorites().collect { favs ->
                val items = favs.mapNotNull { f -> mediaDao.byId(f.mediaId)?.toUi() }
                _uiState.value = _uiState.value.copy(favorites = items)
            }
        }
    }

    // ---------- Contenu réel des extensions (ce qui donne vie à l'accueil)
    fun loadRemote() = viewModelScope.launch {
        val extensions = registry.enabledExtensions()
        _uiState.value = _uiState.value.copy(extensionCount = extensions.size)
        if (extensions.isEmpty()) return@launch

        val rows = mutableListOf<HomeRowUi>()
        coroutineScope {
            extensions.map { (pkg, ext) ->
                async {
                    // ---- Catalogues par genre : 2 premières rangées déclarées (ou « main »)
                    val declared = runCatching { ext.categories() }.getOrDefault(emptyList())
                        .filter { it.key.isNotBlank() }
                    val catList = declared.ifEmpty {
                        listOf(dev.endlesssea.extensions.api.model.HomeCategory("main", ext.info.name))
                    }.take(2)
                    catList.forEach { cat ->
                        val actualKey = if (declared.isEmpty()) "main" else cat.key
                        runCatching { ext.getMainPage(MainPageRequest(category = actualKey, page = 1)) }
                            .onSuccess { page ->
                                if (page.items.isEmpty()) return@onSuccess
                                val items = page.items.take(20).map {
                                    SearchItemUi(
                                        id = "${ext.info.id}:${it.url}",
                                        title = it.title,
                                        posterUrl = it.posterUrl,
                                        bannerUrl = it.posterUrl,
                                        subtitle = it.year?.toString() ?: it.type.name,
                                        rating = it.rating,
                                        audioLangs = it.audioLangs.map { l -> l.name },
                                    )
                                }
                                val label = if (declared.isEmpty()) ext.info.name
                                else "${ext.info.name} — ${cat.title}"
                                synchronized(rows) { rows += HomeRowUi(label, items) }
                            }
                    }
                }
            }.forEach { it.await() }
        }

        val remote = rows.sortedBy { it.title }
        _uiState.value = _uiState.value.copy(
            loading = false,
            remoteRows = remote,
            // Bannière : les 6 premiers titres avec affiche, priorité au catalogue en ligne
            featured = remote.flatMap { it.items }.take(6).ifEmpty { _uiState.value.featured },
        )
    }

    private fun dev.endlesssea.data.db.MediaEntity.toUi() = SearchItemUi(
        id = id, title = title, posterUrl = posterUrl, bannerUrl = bannerUrl ?: posterUrl,
        subtitle = synopsis?.take(140),
    )

    fun onAddToLibrary(mediaId: String) = viewModelScope.launch {
        libraryDao.upsert(dev.endlesssea.data.db.LibraryEntity(mediaId = mediaId, category = "ANIME"))
    }
}
