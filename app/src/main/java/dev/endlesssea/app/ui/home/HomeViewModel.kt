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

/**
 * §carte-historique : une entrée « Reprendre la lecture » dessinée comme une
 * carte d'historique (conversation 9) — vignette 16:9, barre de progression,
 * épisode et temps restant, au lieu d'une affiche de série.
 */
data class ContinueItemUi(
    /** Cible du clic : id de fiche, ou « local:<uri> » pour un fichier local. */
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbUrl: String?,
    /** Progression 0f..1f. */
    val progress: Float,
    val remainingLabel: String,
    val watchedLabel: String,
    val isLocal: Boolean = false,
)

data class HomeRowUi(
    val title: String,
    val items: List<SearchItemUi>,
    /** §accueil-multi : extension d'origine de la rangée (filtrage + icône). */
    val sourcePkg: String = "",
    val sourceName: String = "",
    val iconUrl: String? = null,
    /** §tout-voir : clé de catégorie à rouvrir en page complète. */
    val category: String = "main",
)

data class HomeUiState(
    val loading: Boolean = true,
    val continueWatching: List<ContinueItemUi> = emptyList(),
    val recent: List<SearchItemUi> = emptyList(),
    val favorites: List<SearchItemUi> = emptyList(),
    val remoteRows: List<HomeRowUi> = emptyList(),
    val bannerAutoScroll: Boolean = true,
    val extensionCount: Int = 0,
    /** §accueil-multi : sources chargées (pkg, nom, icône) + filtre actif (« ALL »). */
    val sources: List<Triple<String, String, String?>> = emptyList(),
    val sourceFilter: String = "ALL",
) {
    val featured: List<SearchItemUi> get() = sourceFeatured(remoteRows, sourceFilter, recent)
}

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
    private val prefs: dev.endlesssea.app.di.AppPrefs,
    private val downloadsDao: dev.endlesssea.data.db.DownloadsDao,
) : ViewModel() {

    /**
     * §anti-plantage : une extension tierce qui lève une exception (ou un
     * AbstractMethodError sur un vieux .esx) remontait jusqu'au scope du
     * ViewModel et FERMAIT l'application dès l'ouverture de l'accueil.
     * Tous les lancements passent maintenant par ce garde-fou.
     */
    private val safeHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        android.util.Log.w("HomeViewModel", "erreur ignorée sur l'accueil", e)
        _uiState.value = _uiState.value.copy(loading = false)
    }

    private fun safeLaunch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        viewModelScope.launch(safeHandler) { runCatching { block() } }

    /** Même garde-fou, mais le bloc peut utiliser `return@safeBody`. */

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        // §telecharge-visible : alimente le registre global des titres hors ligne
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                dev.endlesssea.app.ui.components.DownloadedRegistry
                    .set(downloadsDao.completedMediaIds())
            }
        }
        observeLocal()
        loadRemote()
        publishSources()
    }

    // ---------- Sources locales (fonctionnent hors-ligne dès le premier usage)
    private fun observeLocal() {
        safeLaunch {
            mediaDao.recent(20).collect { recent ->
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    recent = recent.map { it.toUi() },
                )
            }
        }
        safeLaunch {
            historyDao.observeContinueWatching(12).collect { history ->
                val items = latestContinueEntries(history).mapNotNull { h ->
                    val progress = if (h.durationMs > 0) {
                        (h.positionMs.toFloat() / h.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val remainingMs = (h.durationMs - h.positionMs).coerceAtLeast(0L)
                    val remaining = when {
                        h.durationMs <= 0 -> ""
                        remainingMs < 60_000 -> "moins d'une minute restante"
                        else -> "${remainingMs / 60_000} min restantes"
                    }
                    val watched = humanSince(h.updatedAt)
                    val media = mediaDao.byId(h.mediaId)
                    when {
                        media != null -> ContinueItemUi(
                            id = media.id,
                            title = media.customTitle?.takeIf { it.isNotBlank() } ?: media.title,
                            subtitle = episodeLabel(h.episodeId),
                            thumbUrl = media.customCoverUri ?: media.posterUrl ?: media.bannerUrl,
                            progress = progress,
                            remainingLabel = remaining,
                            watchedLabel = watched,
                        )
                        // §historique-local : un fichier local (pas de fiche en base)
                        // apparaît quand même, sauf si l'utilisateur l'exclut.
                        prefs.localInHistory.value && isLocalRef(h.episodeId) -> {
                            val meta = runCatching { prefs.localFileMeta(h.episodeId) }.getOrNull()
                            ContinueItemUi(
                                id = "local:" + h.episodeId,
                                title = meta?.title ?: prettyLocalName(h.episodeId),
                                // la vignette, c'est la vidéo elle-même (Coil + coil-video)
                                subtitle = "Fichier local",
                                thumbUrl = meta?.coverUri ?: h.episodeId,
                                progress = progress,
                                remainingLabel = remaining,
                                watchedLabel = watched,
                                isLocal = true,
                            )
                        }
                        else -> null
                    }
                }
                _uiState.value = _uiState.value.copy(continueWatching = items)
            }
        }
        safeLaunch {
            libraryDao.observeFavorites().collect { favs ->
                val items = favs.mapNotNull { f -> mediaDao.byId(f.mediaId)?.toUi() }
                _uiState.value = _uiState.value.copy(favorites = items)
            }
        }
    }

    /**
     * §nom-fichier : une URI SAF finit par « primary%3AMovies%2Ffilm.mp4 » —
     * on en extrait le vrai nom de fichier, sans chemin ni extension.
     */
    private fun isLocalRef(id: String): Boolean =
        id.startsWith("content://") || id.startsWith("file://") || id.startsWith("/")

    private fun prettyLocalName(uri: String): String =
        dev.endlesssea.app.local.LocalNames.pretty(uri)

    /** §carte-historique : « S1:E3 » → « S1 · Épisode 3 » (ids d'extension ou noms de fichiers). */
    private fun episodeLabel(episodeId: String): String {
        Regex("(?i):[Ss](\\d+):[Ee](\\d+)").find(episodeId)?.let {
            return "S${it.groupValues[1].trimStart('0').ifBlank { "0" }} · Épisode ${it.groupValues[2].trimStart('0').ifBlank { "0" }}"
        }
        Regex("(?i)[Ss](\\d{1,2})[ ._-]*[Ee](\\d{1,3})").find(episodeId)?.let {
            return "S${it.groupValues[1].trimStart('0').ifBlank { "0" }} · Épisode ${it.groupValues[2].trimStart('0').ifBlank { "0" }}"
        }
        return if (isLocalRef(episodeId)) "Fichier local" else "Épisode"
    }

    /** §carte-historique : « hier », « il y a 2 h » — comme la page Historique. */
    private fun humanSince(at: Long): String {
        val delta = (System.currentTimeMillis() - at).coerceAtLeast(0L)
        return when {
            delta < 60_000L -> "à l'instant"
            delta < 3_600_000L -> "il y a ${delta / 60_000L} min"
            delta < 86_400_000L -> "il y a ${delta / 3_600_000L} h"
            delta < 2L * 86_400_000L -> "hier"
            else -> "il y a ${delta / 86_400_000L} jours"
        }
    }

    // ---------- Contenu réel des extensions (ce qui donne vie à l'accueil)
    fun loadRemote() = safeLaunch {
        val extensions = runCatching { registry.enabledExtensions() }.getOrDefault(emptyList())
        _uiState.value = _uiState.value.copy(extensionCount = extensions.size)
        if (extensions.isEmpty()) return@safeLaunch

        val rows = mutableListOf<HomeRowUi>()
        coroutineScope {
            extensions.map { (pkg, ext) ->
                async {
                  runCatching {
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
                                synchronized(rows) {
                                    rows += HomeRowUi(
                                        label, items,
                                        sourcePkg = ext.info.id,
                                        sourceName = ext.info.name,
                                        iconUrl = ext.info.iconUrl,
                                        category = actualKey,
                                    )
                                }
                            }
                    }
                  }
                }
            }.forEach { runCatching { it.await() } }
        }

        val remote = rows.sortedBy { it.title }
        _uiState.value = _uiState.value.copy(
            loading = false,
            remoteRows = remote,
            // Bannière : les 6 premiers titres avec affiche, priorité au catalogue en ligne
        )
    }

    private fun dev.endlesssea.data.db.MediaEntity.toUi() = SearchItemUi(
        id = id, title = title, posterUrl = posterUrl, bannerUrl = bannerUrl ?: posterUrl,
        subtitle = synopsis?.take(140),
    )

    /** §accueil-multi : publie la liste des sources (après chargement distant). */
    fun publishSources() = safeLaunch {
        val exts = runCatching { registry.enabledExtensions() }.getOrDefault(emptyList())
        _uiState.value = _uiState.value.copy(
            // §catalogue-par-source : les rangées sont indexées par ext.info.id —
            // publier le nom de paquet ici rendait le filtre TOUJOURS vide.
            sources = exts.map { (_, ext) -> Triple(ext.info.id, ext.info.name, ext.info.iconUrl) },
        )
    }

    /** §accueil-multi : ne garder qu'une source sur l'accueil (« ALL » = toutes). */
    fun setSourceFilter(pkg: String) {
        val id = pkg.ifBlank { "ALL" }
        _uiState.value = _uiState.value.copy(sourceFilter = id)
        if (id != "ALL") loadSourceCatalogue(id)
    }

    /**
     * §catalogue-par-source : quand une seule extension est sélectionnée, on va
     * chercher jusqu'à six de ses catégories (au lieu des deux chargées pour la
     * vue « toutes sources »), sinon l'accueil filtré paraissait vide.
     */
    fun loadSourceCatalogue(id: String) = safeLaunch {
        val entry = runCatching { registry.enabledExtensions() }.getOrDefault(emptyList())
            .firstOrNull { (_, ext) -> ext.info.id == id } ?: return@safeLaunch
        val ext = entry.second
        _uiState.value = _uiState.value.copy(loading = true)
        val declared = runCatching { ext.categories() }.getOrDefault(emptyList())
            .filter { it.key.isNotBlank() }
        val cats = declared.ifEmpty {
            listOf(dev.endlesssea.extensions.api.model.HomeCategory("main", ext.info.name))
        }.take(6)
        val fresh = mutableListOf<HomeRowUi>()
        coroutineScope {
            cats.map { cat ->
                async {
                    val key = if (declared.isEmpty()) "main" else cat.key
                    runCatching { ext.getMainPage(MainPageRequest(category = key, page = 1)) }
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
                            synchronized(fresh) {
                                fresh += HomeRowUi(
                                    label, items,
                                    sourcePkg = ext.info.id,
                                    sourceName = ext.info.name,
                                    iconUrl = ext.info.iconUrl,
                                    category = key,
                                )
                            }
                        }
                }
            }.forEach { it.await() }
        }
        val merged = (_uiState.value.remoteRows.filterNot { it.sourcePkg == id } + fresh)
            .sortedBy { it.title }
        _uiState.value = _uiState.value.copy(loading = false, remoteRows = merged)
    }

    fun onAddToLibrary(mediaId: String) = safeLaunch {
        libraryDao.upsert(dev.endlesssea.data.db.LibraryEntity(mediaId = mediaId, category = "ANIME"))
    }
}
