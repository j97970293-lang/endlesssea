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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * §carte-historique : une entrée « Reprendre la lecture » dessinée comme une
 * carte d'historique (conversation 9) — vignette 16:9, barre de progression,
 * épisode et temps restant, au lieu d'une affiche de série.
 */
data class ContinueItemUi(
    /** Cible du clic : id de fiche à reprendre, ou « local:<uri> » pour un fichier local. */
    val id: String,
    /** Identité stable de la fiche/dossier pour ouvrir sa page d'affiche. */
    val mediaId: String,
    /** Dernier épisode effectivement lu, utilisé pour reprendre ou supprimer l'entrée. */
    val episodeId: String,
    val title: String,
    val subtitle: String,
    val thumbUrl: String?,
    /** Progression 0f..1f. */
    val progress: Float,
    val remainingLabel: String,
    val watchedLabel: String,
    val isLocal: Boolean = false,
    /** A local file to frame-extract when no user-selected cover exists. */
    val localVideoUri: String? = null,
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
                    val localTarget = localContinueTarget(h.episodeId)
                    val clickTarget = continueClickTarget(h.mediaId, h.episodeId)
                    when {
                        // Les fichiers locaux doivent viser l'URI de l'épisode pour Lire,
                        // jamais l'URI du dossier série — sinon SAF ouvre une collection vide.
                        localTarget != null -> if (prefs.localInHistory.value) {
                            val meta = runCatching { prefs.localFileMeta(h.episodeId) }.getOrNull()
                            val cover = meta?.coverUri?.takeIf { it.isNotBlank() }
                                ?: media?.customCoverUri?.takeIf { it.isNotBlank() }
                                ?: media?.posterUrl?.takeIf { it.isNotBlank() }
                                ?: media?.bannerUrl?.takeIf { it.isNotBlank() }
                            val folder = h.mediaId.removePrefix("local:")
                            ContinueItemUi(
                                id = clickTarget,
                                mediaId = h.mediaId,
                                episodeId = h.episodeId,
                                title = media?.customTitle?.takeIf { it.isNotBlank() }
                                    ?: media?.title?.takeIf { it.isNotBlank() }
                                    ?: dev.endlesssea.app.local.LocalVideos.seriesMeta[folder]?.title
                                    ?: meta?.title?.takeIf { it.isNotBlank() }
                                    ?: prettyLocalName(h.episodeId),
                                subtitle = episodeLabel(h.episodeId),
                                thumbUrl = cover,
                                localVideoUri = h.episodeId.takeIf { cover.isNullOrBlank() },
                                progress = progress,
                                remainingLabel = remaining,
                                watchedLabel = watched,
                                isLocal = true,
                            )
                        } else null
                        media != null -> ContinueItemUi(
                            id = clickTarget,
                            mediaId = media.id,
                            episodeId = h.episodeId,
                            title = media.customTitle?.takeIf { it.isNotBlank() } ?: media.title,
                            subtitle = episodeLabel(h.episodeId),
                            thumbUrl = media.customCoverUri ?: media.posterUrl ?: media.bannerUrl,
                            progress = progress,
                            remainingLabel = remaining,
                            watchedLabel = watched,
                        )
                        else -> null
                    }
                }.distinctBy { it.id }.take(12)
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
    private fun isLocalRef(id: String): Boolean = localContinueTarget(id) != null

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

    /** Supprime uniquement l'entrée d'historique sélectionnée, sans toucher au fichier média. */
    fun deleteHistoryEntry(episodeId: String) {
        viewModelScope.launch { historyDao.deleteEpisode(episodeId) }
    }

    // ---------- Contenu réel des extensions (ce qui donne vie à l'accueil)
    /**
     * Beaucoup d'extensions (AnimeSite compris) font du réseau et du parsing dans
     * le thread appelant. Les lancer toutes en parallèle sur le thread principal
     * fige l'accueil. Ici : file d'attente de 2, délai par source, et publication
     * au fil de l'eau. Au-delà de 6 sources, une seule catégorie pour ne pas
     * noyer le décodeur d'images.
     */
    fun loadRemote() = safeLaunch {
        val extensions = runCatching { registry.enabledExtensions() }.getOrDefault(emptyList())
        _uiState.update { it.copy(extensionCount = extensions.size) }
        if (extensions.isEmpty()) {
            _uiState.update { it.copy(loading = false) }
            return@safeLaunch
        }
        val (categories, itemCap) = homeBudget(extensions.size)
        val gate = Semaphore(2)
        coroutineScope {
            extensions.map { (_, ext) ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val rows = runCatching { rowsFor(ext, categories, itemCap) }.getOrDefault(emptyList())
                        publishRemoteRows(rows)
                    }
                }
            }.forEach { runCatching { it.await() } }
        }
        _uiState.update { it.copy(loading = false) }
    }

    private fun homeBudget(extensionCount: Int): Pair<Int, Int> = when {
        extensionCount >= 12 -> 1 to 8
        extensionCount >= 6 -> 1 to 12
        else -> 2 to 16
    }

    private suspend fun rowsFor(
        ext: dev.endlesssea.extensions.api.EsExtension,
        maxCategories: Int,
        itemCap: Int,
    ): List<HomeRowUi> {
        val declared = withTimeoutOrNull(6_000) {
            runCatching { ext.categories() }.getOrDefault(emptyList())
        }.orEmpty().filter { it.key.isNotBlank() }
        val catList = balancedCategories(
            if (declared.isEmpty()) {
                listOf(dev.endlesssea.extensions.api.model.HomeCategory("main", ext.info.name))
            } else declared,
            limit = maxCategories,
        )
        val rows = mutableListOf<HomeRowUi>()
        for (cat in catList) {
            val key = if (declared.isEmpty()) "main" else cat.key
            val page = withTimeoutOrNull(10_000) {
                runCatching {
                    dev.endlesssea.app.withCaptchaRetry {
                        ext.getMainPage(MainPageRequest(category = key, page = 1))
                    }
                }.getOrNull()
            } ?: continue
            if (page.items.isEmpty()) continue
            val items = page.items.take(itemCap).map {
                SearchItemUi(
                    id = "${ext.info.id}:${it.url}",
                    title = it.title,
                    posterUrl = it.posterUrl,
                    bannerUrl = it.posterUrl,
                    subtitle = it.year?.toString() ?: it.type.name,
                    rating = it.rating,
                    audioLangs = it.audioLangs.map { lang -> lang.name },
                )
            }
            val label = if (declared.isEmpty()) ext.info.name else "${ext.info.name} — ${cat.title}"
            rows += HomeRowUi(
                label, items,
                sourcePkg = ext.info.id,
                sourceName = ext.info.name,
                iconUrl = ext.info.iconUrl,
                category = key,
            )
        }
        return rows
    }

    private fun publishRemoteRows(incoming: List<HomeRowUi>) {
        if (incoming.isEmpty()) return
        _uiState.update { state ->
            val merged = (state.remoteRows.filterNot { old ->
                incoming.any { it.sourcePkg == old.sourcePkg && it.category == old.category }
            } + incoming).sortedBy { it.title }
            state.copy(loading = false, remoteRows = merged)
        }
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
        _uiState.update { it.copy(loading = true, remoteRows = it.remoteRows.filterNot { row -> row.sourcePkg == id }) }
        val rows = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { rowsFor(ext, maxCategories = 6, itemCap = 16) }.getOrDefault(emptyList())
        }
        publishRemoteRows(rows)
        _uiState.update { it.copy(loading = false) }
    }

    fun onAddToLibrary(mediaId: String) = safeLaunch {
        libraryDao.upsert(dev.endlesssea.data.db.LibraryEntity(mediaId = mediaId, category = "ANIME"))
    }
}
