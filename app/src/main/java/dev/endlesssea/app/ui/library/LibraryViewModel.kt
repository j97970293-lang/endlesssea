package dev.endlesssea.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.ui.search.SearchItemUi
import dev.endlesssea.data.db.LibraryDao
import dev.endlesssea.data.db.MediaDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val category: String = "FAV",
    val items: List<SearchItemUi> = emptyList(),
    /** Stable workspace navigation; compatible with existing stored category identifiers. */
    val navigation: LibraryNavigation = LibraryNavigation.restore(),
    /** Filtre watchlist §29 : ALL ou un statut de suivi. */
    val filterStatus: String = "ALL",
    /** Vidéos locales scannées sur les dossiers SAF choisis (multi-dossiers). */
    val localFiles: List<LocalVideoUi> = emptyList(),
    val folderMetadata: Map<String, dev.endlesssea.app.local.SeriesMeta> = emptyMap(),
    val localScanning: Boolean = false,
    /** §retour-visuel : texte de progression du scan. */
    val localScanLabel: String = "",
    /** Tic de recomposition quand une métadonnée locale change. */
    val localMetaTick: Int = 0,
    // ---- §telecharges-bibliotheque (conversation 7) : même un épisode isolé
    // téléchargé apparaît, regroupé en « série » virtuelle.
    val downloadedGroups: List<DownloadedGroupUi> = emptyList(),
    val downloadedEpisodes: List<DownloadedEpisodeUi> = emptyList(),
    // ---- §bibliotheque-sections (conversation 6)
    /** « Reprendre la lecture » : épisodes commencés, non terminés. */
    val continueWatching: List<ContinueCardUi> = emptyList(),
    /** « Récemment ajoutés » : derniers titres entrés dans la bibliothèque. */
    val recentlyAdded: List<SearchItemUi> = emptyList(),
    // ---- §multi-sources (conversation 6) : d'où vient le contenu
    /** ALL · DEVICE · SD · DOWNLOADS. */
    val sourceFilter: String = "ALL",
    /** ALL · WATCHING · COMPLETED — état de visionnage (onglets de la grille). */
    val watchFilter: String = "ALL",
) {
    val onDeviceOnly: Boolean get() = navigation.onDeviceOnly
    val localMode: Boolean get() = navigation.area == LibraryArea.FOLDERS
    val downloadsOnly: Boolean get() = navigation.area == LibraryArea.DOWNLOADS
}

/** §multi-sources (conversation 6) : origine d'un contenu de la bibliothèque. */
object LibrarySource {
    const val ALL = "ALL"
    const val DEVICE = "DEVICE"
    const val SD = "SD"
    const val DOWNLOADS = "DOWNLOADS"

    fun label(id: String): String = when (id) {
        DEVICE -> "Mémoire interne"
        SD -> "Carte SD"
        DOWNLOADS -> "Téléchargements"
        else -> "Toutes sources"
    }
}

/**
 * §bibliotheque-sections (conversation 6) — carte « Reprendre » : miniature
 * 16:9, barre de progression, temps restant, horodatage (même langage visuel
 * que l'accueil, conversation 9).
 */
data class ContinueCardUi(
    val episodeId: String,
    val mediaId: String,
    val title: String,
    val thumbUrl: String?,
    val progress: Float,
    val remainingLabel: String,
    val updatedLabel: String,
)


/** Une « série » virtuelle construite à partir des fichiers téléchargés. */
data class DownloadedGroupUi(
    val key: String,
    val title: String,
    val posterUrl: String?,
    val episodeCount: Int,
    val totalBytes: Long,
    val lastAt: Long,
    /** §multi-sources : l'emplacement réel du fichier (carte SD ou interne). */
    val storageKind: String = LibrarySource.DOWNLOADS,
    val mediaId: String? = null,
) {
    val humanSize: String get() = dev.endlesssea.app.local.LocalVideos.humanSize(totalBytes)
    /** Carte de bibliothèque correspondante (clic → fiche du groupe). */
    fun toCard() = SearchItemUi(
        id = mediaId ?: "downloaded:$key",
        title = title,
        posterUrl = posterUrl,
        // §cartes-bibliotheque (conversation 6) : nombre d'épisodes + emplacement
        subtitle = (if (episodeCount > 1) "$episodeCount épisodes" else "1 épisode") +
            " · " + LibrarySource.label(storageKind),
    )
}

/** Un épisode téléchargé (lecture hors-ligne depuis l'appareil). */
data class DownloadedEpisodeUi(
    val groupKey: String,
    val uri: String,
    val fileName: String,
    val displayName: String,
    val episodeNumber: Int?,
    val sizeBytes: Long,
    val quality: String,
    val mediaId: String?,
    val episodeId: String?,
    /** §gestion : identifiant de la tâche — permet de supprimer le fichier. */
    val taskId: String = "",
    val season: Int? = null,
    val exactEpisodeNumber: Float? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
) {
    val humanSize: String get() = dev.endlesssea.app.local.LocalVideos.humanSize(sizeBytes)

    /** §multi-sources : l'épisode est-il sur la carte SD ? */
    val storageKind: String get() = when {
        !uri.startsWith("content://") -> LibrarySource.DEVICE
        uri.contains("primary", ignoreCase = true) -> LibrarySource.DEVICE
        else -> LibrarySource.SD
    }
}

/** Ligne UI d'une vidéo locale scannée (avec métadonnées éditées le cas échéant). */
data class LocalVideoUi(
    val uri: String,
    val name: String,
    /** §scan-par-dossier : dossier SAF contenant le fichier (regroupement Kotatsu/Aniyomi). */
    val parentUri: String = "",
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val customTitle: String? = null,
    val customCoverUri: String? = null,
    val introStartSec: Int? = null,
    val introEndSec: Int? = null,
    val outroStartSec: Int? = null,
) {
    val displayName: String get() = customTitle ?: name

    /** Nom lisible du dossier parent (« Animes/One Piece » → « One Piece »). */
    val folderName: String
        get() = android.net.Uri.decode(parentUri).substringAfterLast(':')
            .trimEnd('/').substringAfterLast('/').ifBlank { "Dossier" }
    val humanSize: String get() = dev.endlesssea.app.local.LocalVideos.humanSize(sizeBytes)
    val humanDuration: String get() = dev.endlesssea.app.local.LocalVideos.humanDuration(durationMs)

    /** §episodes-json : numéro d'épisode déduit du nom de fichier. */
    val episodeNumber: Int? get() = dev.endlesssea.app.local.LocalVideos.episodeNumber(name)

    /** §multi-sources (conversation 6) : interne ou carte SD (déduit du volume SAF). */
    val storageKind: String
        get() = when {
            !uri.startsWith("content://") -> LibrarySource.DEVICE
            uri.contains("primary", ignoreCase = true) -> LibrarySource.DEVICE
            else -> LibrarySource.SD
        }

    /** Titre lisible du fichier (numéro retiré) quand aucun titre perso n'existe. */
    val prettyName: String
        get() = customTitle
            ?: dev.endlesssea.app.local.LocalVideos.episodeTitleFromFileName(name).ifBlank { name }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryDao: LibraryDao,
    private val mediaDao: MediaDao,
    private val episodeDao: dev.endlesssea.data.db.EpisodeDao,
    private val downloadsDao: dev.endlesssea.data.db.DownloadsDao,
    private val historyDao: dev.endlesssea.data.db.WatchHistoryDao,
    private val prefs: dev.endlesssea.app.di.AppPrefs,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
) : ViewModel() {

    val localHistory = historyDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markLocalFolderWatched(folderUri: String, files: List<LocalVideoUi>) = viewModelScope.launch {
        historyDao.upsertAll(files.map { file ->
            val old = historyDao.byEpisode(file.uri)
            dev.endlesssea.data.db.WatchHistoryEntity(
                episodeId = file.uri, mediaId = dev.endlesssea.app.local.LocalMediaIds.series(folderUri),
                positionMs = old?.positionMs ?: 0L, durationMs = file.durationMs ?: old?.durationMs ?: 0L,
                watched = true,
            )
        })
    }

    fun markDownloadedWatched(files: List<DownloadedEpisodeUi>) = viewModelScope.launch {
        historyDao.upsertAll(files.map { file ->
            val id = file.episodeId ?: file.uri
            val old = historyDao.byEpisode(id)
            dev.endlesssea.data.db.WatchHistoryEntity(
                episodeId = id, mediaId = file.mediaId.orEmpty(), positionMs = old?.positionMs ?: 0L,
                durationMs = file.durationMs.takeIf { it > 0L } ?: old?.durationMs ?: 0L, watched = true,
            )
        })
    }

    private val restoredNavigation = LibraryNavigation.restore(
        savedStateHandle["library.destination"], savedStateHandle["library.collection"],
        savedStateHandle["library.deviceOnly"] ?: false,
    )
    private val category = MutableStateFlow(restoredNavigation.collection)
    private val _uiState = MutableStateFlow(LibraryUiState(navigation = restoredNavigation))
    val uiState: StateFlow<LibraryUiState> = _uiState

    /** Dernière liste complète (avant filtrage) pour re-filtrer sans recharger. */
    private var rawItems: List<SearchItemUi> = emptyList()

    /** mediaId → statut watchlist (pour le filtre §29). */
    private var rawStatuses: Map<String, String> = emptyMap()

    /** mediaId → épisodes terminés / entamés (§multi-sources, conversation 6). */
    private var rawStats: Map<String, dev.endlesssea.data.db.MediaWatchStat> = emptyMap()

    init {
        viewModelScope.launch {
            category.flatMapLatest { cat ->
                if (cat == "FAV") libraryDao.observeFavorites() else libraryDao.observeByCategory(cat)
            }.collect { entries ->
                rawStatuses = entries.associate { it.mediaId to it.status }
                // §bibliotheque-sections : « Récemment ajoutés » = ordre d'ajout réel
                // (LibraryEntity.addedAt), pas l'ordre alphabétique de la grille.
                val byAddedAt = entries.sortedByDescending { it.addedAt }
                val recentUi = byAddedAt.mapNotNull { entry ->
                    mediaDao.byId(entry.mediaId)?.let { media ->
                        SearchItemUi(
                            id = media.id,
                            title = media.customTitle ?: media.title,
                            posterUrl = media.customCoverUri ?: media.posterUrl,
                            bannerUrl = media.bannerUrl,
                            subtitle = media.type,
                        )
                    }
                }.take(12)
                _uiState.value = _uiState.value.copy(recentlyAdded = recentUi)
                rawItems = entries.mapNotNull { entry ->
                    mediaDao.byId(entry.mediaId)?.let { media ->
                        SearchItemUi(
                            id = media.id,
                            title = media.customTitle ?: media.title,
                            posterUrl = media.customCoverUri ?: media.posterUrl,
                            bannerUrl = media.bannerUrl,
                            subtitle = media.type,
                        )
                    }
                }
                applyFilter()
            }
        }
        // §telecharges-bibliotheque : chaque fichier terminé rejoint la
        // bibliothèque — seul (« série » d'un épisode) ou rattaché à sa fiche.
        viewModelScope.launch {
            downloadsDao.observeCompleted().collect { tasks -> buildDownloads(tasks) }
        }
        // §multi-sources : statistiques de visionnage (une requête groupée)
        viewModelScope.launch {
            historyDao.observeAll().collect {
                rawStats = runCatching { historyDao.statsAll() }.getOrDefault(emptyList()).associateBy { it.mediaId }
                applyFilter()
            }
        }
        // §bibliotheque-sections : « Reprendre la lecture » (progression réelle,
        // < 95 % — un épisode presque fini n'encombre pas la rangée).
        viewModelScope.launch {
            historyDao.observeContinueWatching(12).collect { entries -> buildContinue(entries) }
        }
    }

    /** §bibliotheque-sections : rangée « Reprendre la lecture ». */
    private suspend fun buildContinue(entries: List<dev.endlesssea.data.db.WatchHistoryEntity>) {
        val cards = entries.mapNotNull { entry ->
            val fraction = if (entry.durationMs > 0) {
                entry.positionMs.toFloat() / entry.durationMs
            } else 0f
            if (fraction >= 0.95f) return@mapNotNull null   // quasi terminé
            val media = mediaDao.byId(entry.mediaId)
            val remainingMs = (entry.durationMs - entry.positionMs).coerceAtLeast(0)
            ContinueCardUi(
                episodeId = entry.episodeId,
                mediaId = entry.mediaId,
                title = media?.customTitle ?: media?.title ?: if (entry.mediaId.startsWith("local:")) {
                    dev.endlesssea.app.local.LocalVideos.seriesMeta[entry.mediaId.removePrefix("local:")]?.title
                        ?: dev.endlesssea.app.local.LocalNames.pretty(entry.mediaId.removePrefix("local:"))
                } else entry.episodeId,
                thumbUrl = media?.customCoverUri ?: media?.bannerUrl ?: media?.posterUrl
                    ?: entry.episodeId.takeIf { it.startsWith("content://") },
                progress = fraction.coerceIn(0f, 1f),
                remainingLabel = dev.endlesssea.app.local.LocalVideos.humanDuration(remainingMs) + " restantes",
                updatedLabel = humanSinceShort(entry.updatedAt),
            )
        }
        _uiState.value = _uiState.value.copy(continueWatching = cards)
    }

    /** Étiquette courte « il y a 2 h / hier / 3 j ». */
    private fun humanSinceShort(at: Long): String {
        val diff = System.currentTimeMillis() - at
        val min = diff / 60_000
        return when {
            min < 60 -> "il y a ${min.coerceAtLeast(1)} min"
            min < 24 * 60 -> "il y a ${min / 60} h"
            min < 48 * 60 -> "hier"
            else -> "il y a ${min / (24 * 60)} j"
        }
    }

    /**
     * Construit les « séries » de téléchargements (conversation 7) :
     *  · un épisode rattaché à une fiche (mediaId) → rejoint cette série ;
     *  · un épisode isolé → devient une série virtuelle d'un seul épisode,
     *    titrée d'après le dossier de téléchargement (`downloads/<Source>/<Série>/`).
     */
    private suspend fun buildDownloads(tasks: List<dev.endlesssea.data.db.DownloadTaskEntity>) {
        val episodes = tasks.mapNotNull { task ->
            val uri = task.targetUri.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val fileName = task.fileName.removeSuffix(".part")
            // Clé de regroupement : la fiche si connue, sinon le dossier parent
            // (displayPath = « Source/Série/fichier » → « Source/Série »).
            val key = task.mediaId?.takeIf { it.isNotBlank() }
                ?: task.displayPath.substringBeforeLast('/', "").ifBlank { "téléchargements" }
            val ep = task.episodeId?.let { episodeDao.byId(it) }
            DownloadedEpisodeUi(
                groupKey = key,
                uri = uri,
                fileName = fileName,
                displayName = ep?.title?.takeIf { it.isNotBlank() }
                    ?: dev.endlesssea.app.local.LocalVideos.episodeTitleFromFileName(fileName)
                        .ifBlank { dev.endlesssea.app.local.LocalNames.pretty(uri) },
                episodeNumber = dev.endlesssea.app.local.LocalVideos.episodeNumber(fileName),
                sizeBytes = task.totalBytes,
                quality = task.quality,
                mediaId = task.mediaId,
                episodeId = task.episodeId,
                taskId = task.id,
                season = ep?.season ?: dev.endlesssea.app.local.LocalVideos.episodeSeason(fileName),
                exactEpisodeNumber = ep?.number ?: dev.endlesssea.app.local.LocalVideos.episodeNumber(fileName)?.toFloat(),
                durationMs = ep?.durationMs ?: 0L, thumbnailUrl = ep?.thumbnailUrl,
            )
        }
        val groups = episodes.groupBy { it.groupKey }.map { (key, list) ->
            val media = list.firstNotNullOfOrNull { it.mediaId }?.let { mediaDao.byId(it) }
            DownloadedGroupUi(
                key = key, mediaId = media?.id,
                title = media?.customTitle?.takeIf { it.isNotBlank() }
                    ?: media?.title
                    ?: key.substringAfterLast('/').ifBlank { "Téléchargements" },
                posterUrl = media?.customCoverUri ?: media?.posterUrl,
                episodeCount = list.size,
                totalBytes = list.sumOf { it.sizeBytes },
                lastAt = tasks.filter { t -> list.any { it.fileName == t.fileName.removeSuffix(".part") } }
                    .maxOfOrNull { it.updatedAt } ?: 0L,
                // §bibliotheque-sources : « Carte SD » si le fichier final n'est pas
                // sur le volume interne (URI SAF non « primary »).
                storageKind = if (tasks.any {
                        list.any { e -> e.uri == it.targetUri } &&
                            it.targetUri.startsWith("content://") &&
                            !it.targetUri.contains("primary", true)
                    }
                ) LibrarySource.SD else LibrarySource.DOWNLOADS,
            )
        }.sortedByDescending { it.lastAt }
        _uiState.value = _uiState.value.copy(
            downloadedEpisodes = episodes,
            downloadedGroups = groups,
        )
    }

    /** One entry point for the new library shell; the DAOs retain their existing identifiers. */
    fun navigate(destination: String) = updateNavigation(_uiState.value.navigation.select(destination))

    fun openArea(area: LibraryArea) = updateNavigation(_uiState.value.navigation.open(area))

    private fun updateNavigation(next: LibraryNavigation) {
        val previous = _uiState.value.navigation
        savedStateHandle["library.destination"] = next.destination
        savedStateHandle["library.collection"] = next.collection
        savedStateHandle["library.deviceOnly"] = next.deviceOnly
        _uiState.value = _uiState.value.copy(navigation = next)
        category.value = next.collection
        viewModelScope.launch { applyFilter() }
        if (next.area == LibraryArea.FOLDERS && previous.destination != next.destination) scanLocal()
    }

    // ------------------------------------------------ §multi-sources (conversation 6)

    // ------------------------------------------------ §netto-automatique (conversation 10)

    /** Délai de purge des téléchargements terminés (jours, 0 = jamais). */
    val autoCleanDays: StateFlow<Int> = prefs.downloadAutoCleanDays
    fun setAutoCleanDays(days: Int) = prefs.setDownloadAutoCleanDays(days)

    /**
     * §nettoyage : supprime tout de suite les fichiers terminés au-delà du délai.
     * Renvoie un compte rendu lisible (« 3 fichiers · 1.2 Go libérés ») ; sans
     * délai configuré, la vérification reste possible (c'est un bouton, pas une
     * suppression surprise).
     */
    fun cleanNow(): String {
        val days = prefs.downloadAutoCleanDays.value
        val cutoff = if (days <= 0) 0L
        else System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        return try {
            val tasks = kotlinx.coroutines.runBlocking {
                downloadsDao.completedOlderThan(if (cutoff == 0L) Long.MAX_VALUE else cutoff)
            }
            var bytes = 0L
            val freed = tasks.also {
                it.forEach { task ->
                    val uri = task.targetUri
                    val ok = runCatching {
                        context.contentResolver.delete(android.net.Uri.parse(uri), null, null) > 0
                    }.getOrDefault(false)
                    if (ok) bytes += task.totalBytes
                }
            }.count()
            if (freed == 0) "aucun fichier à supprimer"
            else "$freed fichier(s) · " + dev.endlesssea.app.local.LocalVideos.humanSize(bytes) + " libérés"
        } catch (e: Exception) {
            "erreur : ${e.message ?: "inconnue"}"
        }
    }

    /**
     * §gestion (conversation 7) : supprime un épisode téléchargé — le fichier
     * (SAF ou file://) ET la ligne en base, pour que l'espace soit réellement
     * récupéré et que la fiche/la bibliothèque se mettent à jour d'elles-mêmes.
     */
    fun deleteDownloadedEpisode(taskId: String, uri: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (uri.startsWith("content://")) {
                    context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
                } else {
                    java.io.File(android.net.Uri.parse(uri).path ?: uri).delete()
                }
            }
            runCatching { downloadsDao.delete(taskId) }
        }
    }

    /** Épisodes d'un groupe (écran « fiche » des téléchargements). */
    fun downloadedEpisodesOf(key: String): List<DownloadedEpisodeUi> =
        _uiState.value.downloadedEpisodes.filter { it.groupKey == key }
            .sortedWith(
                compareBy({ it.season ?: 0 }, { it.exactEpisodeNumber ?: Float.MAX_VALUE }, { it.displayName.lowercase() }),
            )

    /** Re-applique le filtre courant sur les éléments bruts. */
    private suspend fun applyFilter() {
        val onDevice = _uiState.value.onDeviceOnly
        var shown = if (onDevice) {
            val ids = downloadsDao.completedMediaIds().toSet()
            rawItems.filter { it.id in ids }
        } else rawItems
        val st = _uiState.value.filterStatus
        if (st != "ALL") shown = shown.filter { (rawStatuses[it.id] ?: "NONE") == st }
        // §multi-sources (conversation 6) : onglets Tout / En cours / Terminé
        when (_uiState.value.watchFilter) {
            "WATCHING" -> shown = shown.filter { item ->
                val stat = rawStats[item.id] ?: return@filter false
                stat.watchedCount < stat.total || stat.watchedCount == 0
            }
            "COMPLETED" -> shown = shown.filter { item ->
                val stat = rawStats[item.id] ?: return@filter false
                stat.total > 0 && stat.watchedCount >= stat.total && stat.watchedCount > 0
            }
        }
        _uiState.value = _uiState.value.copy(category = category.value, items = shown)
    }

    fun setOnDeviceOnly(v: Boolean) = updateNavigation(_uiState.value.navigation.filterDeviceOnly(v))

    fun setFilterStatus(status: String) {
        _uiState.value = _uiState.value.copy(filterStatus = status)
        viewModelScope.launch { applyFilter() }
    }

    /** §multi-sources : Toutes / Mémoire interne / Carte SD / Téléchargements. */
    fun setSourceFilter(id: String) { _uiState.value = _uiState.value.copy(sourceFilter = id) }

    /** §multi-sources : Tout / En cours / Terminé (état réel de visionnage). */
    fun setWatchFilter(id: String) {
        _uiState.value = _uiState.value.copy(watchFilter = id)
        viewModelScope.launch { applyFilter() }
    }

    /** Sources réellement présentes (pour n'afficher que les puces utiles). */
    fun presentSources(): List<String> {
        val found = linkedSetOf<String>()
        if (_uiState.value.localFiles.isNotEmpty()) {
            _uiState.value.localFiles.forEach { found += it.storageKind }
        }
        if (_uiState.value.downloadedEpisodes.isNotEmpty()) found += LibrarySource.DOWNLOADS
        return found.toList()
    }

    /** §bibliothèque-locale : dossiers SAF choisis (pour chips + scan). */
    val dirs = prefs.localVideoDirs

    // ------------------------------------------------- vidéos locales §bibliothèque-locale

    init {
        viewModelScope.launch {
            prefs.localVideoDirs.collect { if (_uiState.value.localMode) scanLocal() }
        }
        viewModelScope.launch {
            prefs.localMetaTick.collect { tic ->
                _uiState.value = _uiState.value.copy(localMetaTick = tic)
            }
        }
    }

    /** Enregistre un nouvel arbre SAF et relance le scan. */
    fun addLocalDir(treeUri: String) {
        val cur = prefs.localVideoDirs.value
        if (treeUri !in cur) prefs.setLocalVideoDirs(cur + treeUri)
        scanLocal()
    }

    fun removeLocalDir(treeUri: String) {
        prefs.setLocalVideoDirs(prefs.localVideoDirs.value - treeUri)
        scanLocal()
    }

    /** Scan file names and metadata first. Durations are fetched only for visible episode rows. */
    private var scanJob: kotlinx.coroutines.Job? = null
    private val durationGate = kotlinx.coroutines.sync.Semaphore(3)
    private val durationPending = mutableSetOf<String>()

    fun scanLocal(): kotlinx.coroutines.Job {
        scanJob?.cancel()
        return viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        // §stockage-public : le dossier de stockage (téléchargements + localanime,
        // arborescence Aniyomi) est TOUJOURS scanné, en plus des dossiers ajoutés.
        val dirs = (
            prefs.localVideoDirs.value +
                listOfNotNull(prefs.storageRoot.value) +
                // §emplacement : les dossiers de téléchargement PRÉCÉDENTS restent
                // scannés, sinon changer d'emplacement faisait « disparaître »
                // tout ce qui avait déjà été téléchargé.
                prefs.storageHistory.value
            ).distinct()
        _uiState.value = _uiState.value.copy(
            localScanning = true, localScanLabel = "Analyse en cours…",
        )
        val known = _uiState.value.localFiles.associate { it.uri to it.durationMs }
        val metadataByUri = prefs.localFileMetadataSnapshot()
        val files = dirs
            .flatMap {
                dev.endlesssea.app.local.LocalVideos.scanAsync(
                    context, it, includeHidden = prefs.showHiddenFiles.value,
                ) { folders, found, current ->
                    // §retour-visuel : le scan n'est plus muet
                    _uiState.value = _uiState.value.copy(
                        localScanLabel = "$folders dossiers · $found vidéos" +
                            if (current.isNotBlank()) " · $current" else "",
                    )
                }
            }
            .distinctBy { it.uri }
            .sortedBy { it.displayName.lowercase() }
            .map { f ->
                val m = metadataByUri[f.uri] ?: dev.endlesssea.app.di.AppPrefs.LocalFileMeta()
                LocalVideoUi(
                    uri = f.uri,
                    name = dev.endlesssea.app.local.LocalNames.fileName(f.displayName)
                        .ifBlank { dev.endlesssea.app.local.LocalNames.fileName(f.uri) },
                    parentUri = f.parentUri,
                    sizeBytes = f.sizeBytes,
                    durationMs = known[f.uri],
                    customTitle = m.title, customCoverUri = m.coverUri,
                    introStartSec = m.introStartSec, introEndSec = m.introEndSec,
                    outroStartSec = m.outroStartSec,
                )
            }
        val folderMetadata = files.map { it.parentUri }.distinct().mapNotNull { folder ->
            mediaDao.byId(dev.endlesssea.app.local.LocalMediaIds.series(folder))?.let { saved ->
                fun strings(json: String): List<String> = runCatching {
                    val array = org.json.JSONArray(json)
                    (0 until array.length()).map { array.getString(it) }
                }.getOrDefault(emptyList())
                folder to (dev.endlesssea.app.local.LocalVideos.seriesMeta[folder]
                    ?: dev.endlesssea.app.local.SeriesMeta()).copy(title = saved.title, description = saved.synopsis,
                        author = strings(saved.studiosJson).firstOrNull(), genres = strings(saved.genresJson))
            }
        }.toMap()
        _uiState.value = _uiState.value.copy(localFiles = files, folderMetadata = folderMetadata,
            localScanning = false, localScanLabel = "")
        // §fiche-locale : partagé avec l'écran de fiche d'un dossier
        dev.endlesssea.app.local.LocalLibraryCache.publish(files)

        }.also { scanJob = it }
    }

    /** Decode durations only for visible episode rows, never for every file in a 1,000-episode folder. */
    fun loadLocalDuration(uri: String) = viewModelScope.launch {
        val file = (_uiState.value.localFiles + dev.endlesssea.app.local.LocalLibraryCache.files.value)
            .firstOrNull { it.uri == uri } ?: return@launch
        if (file.durationMs != null || !durationPending.add(uri)) return@launch
        try {
            val duration = durationGate.withPermit {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    dev.endlesssea.app.local.LocalVideos.durationMs(context, uri)
                }
            } ?: return@launch
            _uiState.value = _uiState.value.copy(localFiles = _uiState.value.localFiles.map {
                if (it.uri == uri) it.copy(durationMs = duration) else it
            })
            dev.endlesssea.app.local.LocalLibraryCache.publish(dev.endlesssea.app.local.LocalLibraryCache.files.value.map {
                if (it.uri == uri) it.copy(durationMs = duration) else it
            })
        } finally { durationPending.remove(uri) }
    }

    /** Persistance de métadonnées locales éditées (§métadonnées-locales + §marqueurs). */
    fun saveLocalMeta(
        uri: String, title: String?, coverUri: String?,
        introStartSec: Int? = null, introEndSec: Int? = null, outroStartSec: Int? = null,
    ) {
        prefs.setLocalFileMeta(
            uri,
            dev.endlesssea.app.di.AppPrefs.LocalFileMeta(
                title = title, coverUri = coverUri,
                introStartSec = introStartSec, introEndSec = introEndSec,
                outroStartSec = outroStartSec,
            ),
        )
        // met à jour localement sans nouveau scan
        _uiState.value = _uiState.value.copy(
            localFiles = _uiState.value.localFiles.map {
                if (it.uri == uri) {
                    it.copy(
                        customTitle = title, customCoverUri = coverUri,
                        introStartSec = introStartSec, introEndSec = introEndSec,
                        outroStartSec = outroStartSec,
                    )
                } else {
                    it
                }
            },
        )
        dev.endlesssea.app.local.LocalLibraryCache.publish(
            dev.endlesssea.app.local.LocalLibraryCache.files.value.map { file ->
                if (file.uri == uri) file.copy(customTitle = title, customCoverUri = coverUri,
                    introStartSec = introStartSec, introEndSec = introEndSec, outroStartSec = outroStartSec)
                else file
            },
        )
    }

    fun folderCover(folderUri: String): String? = prefs.localFileMeta("folder:$folderUri").coverUri

    fun importFolderCover(folderUri: String, imageUri: String) = viewModelScope.launch {
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val directory = java.io.File(context.filesDir, "local-covers").apply { mkdirs() }
            val target = java.io.File(directory, java.util.UUID.randomUUID().toString() + ".img")
            try {
                context.contentResolver.openInputStream(android.net.Uri.parse(imageUri))?.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 20L * 1024 * 1024) { "Image trop volumineuse (20 Mo maximum)" }
                            output.write(buffer, 0, count)
                        }
                        require(total > 0) { "Image vide" }
                    }
                } ?: error("Image inaccessible")
                Result.success(target.toURI().toString())
            } catch (error: Exception) { target.delete(); Result.failure<String>(error) }
        }
        result.onSuccess { cover ->
            prefs.setLocalFileMeta("folder:$folderUri", null, cover)
            mediaDao.byId(dev.endlesssea.app.local.LocalMediaIds.series(folderUri))?.let { saved ->
                mediaDao.upsertAll(listOf(saved.copy(posterUrl = cover, customCoverUri = cover)))
            }
            _uiState.value = _uiState.value.copy(localMetaTick = _uiState.value.localMetaTick + 1,
                localScanLabel = "Couverture enregistrée dans l'application")
        }.onFailure { error ->
            _uiState.value = _uiState.value.copy(localScanLabel = error.message ?: "Import impossible")
        }
    }

    /**
     * §métadonnées-dossier : applique affiche + marqueurs intro/outro à TOUS les
     * fichiers du dossier (le titre reste propre à chaque fichier).
     */
    fun saveFolderMeta(
        parentUri: String, coverUri: String?,
        introStartSec: Int? = null, introEndSec: Int? = null, outroStartSec: Int? = null,
    ) {
        // §fiche-locale : la fiche d'un dossier possède son propre ViewModel, dont
        // la liste n'est pas encore scannée — on retombe alors sur le cache partagé.
        val target = _uiState.value.localFiles.filter { it.parentUri == parentUri }
            .ifEmpty { dev.endlesssea.app.local.LocalLibraryCache.folder(parentUri) }
        target.forEach { f ->
            saveLocalMeta(f.uri, f.customTitle, coverUri, introStartSec, introEndSec, outroStartSec)
        }
        // le cache reflète immédiatement les nouveaux repères
        dev.endlesssea.app.local.LocalLibraryCache.publish(
            dev.endlesssea.app.local.LocalLibraryCache.files.value.map { f ->
                if (f.parentUri != parentUri) f else f.copy(
                    customCoverUri = coverUri ?: f.customCoverUri,
                    introStartSec = introStartSec, introEndSec = introEndSec,
                    outroStartSec = outroStartSec,
                )
            },
        )
    }

    /**
     * §metadonnees-fichier (conversation 5) : écrit `details.json` DANS le dossier
     * de la série (titre, synopsis, auteur, genres), comme le fait Aniyomi. Le
     * scanner relit ce fichier à chaque scan — les métadonnées survivent donc à
     * une réinstallation et restent lisibles par d'autres applications.
     */
    fun folderMetadata(uri: String): dev.endlesssea.app.local.SeriesMeta =
        _uiState.value.folderMetadata[uri] ?: dev.endlesssea.app.local.LocalVideos.seriesMeta[uri]
        ?: dev.endlesssea.app.local.SeriesMeta()

    fun folderCards(files: List<LocalVideoUi> = _uiState.value.localFiles): List<SearchItemUi> {
        val managed = _uiState.value.downloadedEpisodes.map { it.uri }.toSet()
        val covers = prefs.localFileMetadataSnapshot()
        return indexFolders(files, managed, { it.uri }, { it.parentUri }).map { folder ->
            val meta = folderMetadata(folder.uri)
            SearchItemUi(id = "local-folder:${folder.uri}",
                title = meta.title?.takeIf { it.isNotBlank() } ?: folder.episodes.first().folderName,
                posterUrl = covers["folder:${folder.uri}"]?.coverUri ?: meta.coverUri
                    ?: folder.episodes.firstNotNullOfOrNull { it.customCoverUri } ?: folder.episodes.first().uri,
                subtitle = "${folder.episodes.size} épisodes · Hors ligne")
        }.sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
    }

    fun saveSeriesMeta(
        folderUri: String, title: String?, description: String? = null,
        author: String? = null, genres: List<String> = emptyList(),
    ) = viewModelScope.launch {
        val old = folderMetadata(folderUri)
        val next = old.copy(title = title?.trim()?.takeIf { it.isNotBlank() },
            description = description?.trim()?.takeIf { it.isNotBlank() },
            author = author?.trim()?.takeIf { it.isNotBlank() }, genres = genres)
        val id = dev.endlesssea.app.local.LocalMediaIds.series(folderUri)
        val displayTitle = next.title ?: dev.endlesssea.app.local.LocalNames.pretty(folderUri)
        // Room is authoritative for edits even on read-only SAF folders. No episode title is rewritten.
        mediaDao.upsertAll(listOf(dev.endlesssea.data.db.MediaEntity(
            id = id, extensionId = "local", type = "ANIME", title = displayTitle,
            titleKey = dev.endlesssea.core.util.FileNames.normalizedKey(displayTitle),
            synopsis = next.description, posterUrl = folderCover(folderUri) ?: next.coverUri,
            genresJson = org.json.JSONArray(next.genres).toString(),
            studiosJson = org.json.JSONArray(listOfNotNull(next.author)).toString(),
        )))
        val exported = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dev.endlesssea.app.local.LocalVideos.writeSeriesMeta(context, folderUri, next.title,
                next.description, next.author, next.genres)
        }
        dev.endlesssea.app.local.LocalVideos.seriesMeta[folderUri] = next
        _uiState.value = _uiState.value.copy(folderMetadata = _uiState.value.folderMetadata + (folderUri to next),
            localMetaTick = _uiState.value.localMetaTick + 1,
            localScanLabel = if (exported) "Métadonnées enregistrées dans l'app et details.json"
                else "Métadonnées enregistrées dans l'app ; dossier non modifiable")
    }

    /** §catégories-perso : catégories créées par l'utilisateur. */
    val customCategories: StateFlow<List<String>> = prefs.customCategories
    val categoryItemsTick: StateFlow<Int> = prefs.categoryItemsTick
    fun addCategory(name: String) = prefs.addCustomCategory(name)
    fun removeCategory(name: String) = prefs.removeCustomCategory(name)
    fun categoryItems(name: String): List<String> = prefs.categoryItems(name)

    /** §stockage : dossier racine (style Aniyomi) choisi par l'utilisateur. */
    val storageRoot: StateFlow<String?> = prefs.storageRoot
    fun setStorageRoot(uri: String?) { prefs.setStorageRoot(uri); scanLocal() }

    /** §affichage-dossiers : "folders" ou "flat". */
    val localFolderView: StateFlow<String> = prefs.localFolderView
    fun setLocalFolderView(v: String) { prefs.setLocalFolderView(v); }

    /** §fichiers-caches */
    val showHiddenFiles: StateFlow<Boolean> = prefs.showHiddenFiles
    fun setShowHiddenFiles(v: Boolean) { prefs.setShowHiddenFiles(v); scanLocal() }

    /** §bibliotheque-locale-fusion : afficher les vidéos locales dans la grille. */
    val mergeLocal: StateFlow<Boolean> = prefs.mergeLocalLibrary
    fun setMergeLocal(v: Boolean) {
        prefs.setMergeLocalLibrary(v)
        if (v) scanLocal()
    }
    fun setFolderCategory(name: String, folder: String, included: Boolean) {
        val episodes = (_uiState.value.localFiles + dev.endlesssea.app.local.LocalLibraryCache.folder(folder))
            .filter { it.parentUri == folder }.map { it.uri }.toSet()
        val marker = "folder:$folder"
        val rest = prefs.categoryItems(name).filter { it != marker && it !in episodes }
        prefs.setCategoryItems(name, if (included) rest + marker else rest)
    }

    fun toggleCategoryItem(name: String, item: String) = prefs.toggleCategoryItem(name, item)

    /** §métadonnées-éditées : titre/affiche perso sur une source (téléchargée ou non). */
    fun saveCustomMediaMeta(mediaId: String, title: String?, coverUri: String?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            mediaDao.setCustomMeta(
                mediaId,
                title?.takeIf { it.isNotBlank() },
                coverUri?.takeIf { it.isNotBlank() },
            )
            rawItems = rawItems.map {
                if (it.id == mediaId) {
                    it.copy(
                        title = title?.takeIf { t -> t.isNotBlank() } ?: it.title,
                        posterUrl = coverUri?.takeIf { c -> c.isNotBlank() } ?: it.posterUrl,
                    )
                } else {
                    it
                }
            }
            applyFilter()
        }
    }
}
