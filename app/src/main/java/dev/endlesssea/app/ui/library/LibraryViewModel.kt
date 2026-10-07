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
    /** §bibliothèque-locale : mode « fichiers vidéo locaux » actif (chip en haut). */
    val localMode: Boolean = false,
    /** Vidéos locales scannées sur les dossiers SAF choisis (multi-dossiers). */
    val localFiles: List<LocalVideoUi> = emptyList(),
    val localScanning: Boolean = false,
    /** §retour-visuel : texte de progression du scan. */
    val localScanLabel: String = "",
    /** Tic de recomposition quand une métadonnée locale change. */
    val localMetaTick: Int = 0,
    // ---- §telecharges-bibliotheque (conversation 7) : même un épisode isolé
    // téléchargé apparaît, regroupé en « série » virtuelle.
    val downloadedGroups: List<DownloadedGroupUi> = emptyList(),
    val downloadedEpisodes: List<DownloadedEpisodeUi> = emptyList(),
    /** Afficher UNIQUEMENT les téléchargements (chip « Téléchargés »). */
    val downloadsOnly: Boolean = false,
    // ---- §bibliotheque-sources (conversation 6) : filtre par emplacement.
    /** ALL · INTERNAL · SD · DOWNLOADS. */
    val sourceFilter: String = "ALL",
    /** ALL · WATCHING · COMPLETED — onglets de progression (conversation 6). */
    val statusTab: String = "ALL",
)

/** Emplacement de stockage d'un élément (conversation 6 : pastille de source). */
object StorageKind {
    const val INTERNAL = "INTERNAL"
    const val SD = "SD"
    const val DOWNLOADS = "DOWNLOADS"
    const val ALL = "ALL"

    fun ofUri(uri: String): String = when {
        uri.startsWith("content://") ->
            // SAF : « primary: » = mémoire interne, sinon volume externe (carte SD)
            if (uri.contains("primary", ignoreCase = true)) INTERNAL else SD
        else -> INTERNAL
    }

    fun label(kind: String): String = when (kind) {
        INTERNAL -> "Interne"
        SD -> "Carte SD"
        DOWNLOADS -> "Téléchargements"
        else -> "Toutes"
    }
}

/** Une « série » virtuelle construite à partir des fichiers téléchargés. */
data class DownloadedGroupUi(
    val key: String,
    val title: String,
    val posterUrl: String?,
    val episodeCount: Int,
    val totalBytes: Long,
    val lastAt: Long,
    /** §bibliotheque-sources : l'emplacement réel du fichier (carte SD ou interne). */
    val storageKind: String = StorageKind.DOWNLOADS,
) {
    val humanSize: String get() = dev.endlesssea.app.local.LocalVideos.humanSize(totalBytes)
    /** Carte de bibliothèque correspondante (clic → fiche du groupe). */
    fun toCard() = SearchItemUi(
        id = "downloaded:$key",
        title = title,
        posterUrl = posterUrl,
        // §cartes-bibliotheque (conversation 6) : nombre d'épisodes + emplacement
        subtitle = (if (episodeCount > 1) "$episodeCount épisodes" else "1 épisode") +
            " · " + StorageKind.label(storageKind),
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
) {
    val humanSize: String get() = dev.endlesssea.app.local.LocalVideos.humanSize(sizeBytes)
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

    /** §bibliotheque-sources : mémoire interne ou carte SD (déduit de l'URI SAF). */
    val storageKind: String get() = StorageKind.ofUri(uri)

    /** §episodes-json : numéro d'épisode déduit du nom de fichier. */
    val episodeNumber: Int? get() = dev.endlesssea.app.local.LocalVideos.episodeNumber(name)

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
    private val downloadsDao: dev.endlesssea.data.db.DownloadsDao,
    private val historyDao: dev.endlesssea.data.db.WatchHistoryDao,
    private val prefs: dev.endlesssea.app.di.AppPrefs,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
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
            DownloadedEpisodeUi(
                groupKey = key,
                uri = uri,
                fileName = fileName,
                displayName = dev.endlesssea.app.local.LocalVideos.episodeTitleFromFileName(fileName)
                    .ifBlank { dev.endlesssea.app.local.LocalNames.pretty(uri) },
                episodeNumber = dev.endlesssea.app.local.LocalVideos.episodeNumber(fileName),
                sizeBytes = task.totalBytes,
                quality = task.quality,
                mediaId = task.mediaId,
                episodeId = task.episodeId,
            )
        }
        val groups = episodes.groupBy { it.groupKey }.map { (key, list) ->
            val media = list.firstNotNullOfOrNull { it.mediaId }?.let { mediaDao.byId(it) }
            DownloadedGroupUi(
                key = key,
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
                ) StorageKind.SD else StorageKind.DOWNLOADS,
            )
        }.sortedByDescending { it.lastAt }
        _uiState.value = _uiState.value.copy(
            downloadedEpisodes = episodes,
            downloadedGroups = groups,
        )
    }

    /** Afficher uniquement les téléchargements (ou revenir à la bibliothèque). */
    fun setDownloadsOnly(v: Boolean) {
        _uiState.value = _uiState.value.copy(downloadsOnly = v)
    }

    // ------------------------------------------------ §bibliotheque-sources / statuts

    /** Filtre d'emplacement : ALL · INTERNAL · SD · DOWNLOADS (conversation 6). */
    fun setSourceFilter(kind: String) {
        _uiState.value = _uiState.value.copy(sourceFilter = kind)
        viewModelScope.launch { applyFilter() }
    }

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

    /** Onglets de progression : ALL · WATCHING · COMPLETED (conversation 6). */
    fun setStatusTab(tab: String) {
        _uiState.value = _uiState.value.copy(statusTab = tab)
        viewModelScope.launch { applyFilter() }
    }

    /** Types d'emplacement réellement présents (pour n'afficher que les chips utiles). */
    fun presentStorageKinds(): List<String> {
        val kinds = linkedSetOf<String>()
        _uiState.value.localFiles.forEach { kinds += it.storageKind }
        if (_uiState.value.downloadedGroups.isNotEmpty()) kinds += StorageKind.DOWNLOADS
        return kinds.toList()
    }

    /** Épisodes d'un groupe (écran « fiche » des téléchargements). */
    fun downloadedEpisodesOf(key: String): List<DownloadedEpisodeUi> =
        _uiState.value.downloadedEpisodes.filter { it.groupKey == key }
            .sortedWith(
                compareBy({ it.episodeNumber ?: Int.MAX_VALUE }, { it.displayName.lowercase() }),
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
        // §bibliotheque-statuts (conversation 6) : onglets Tout / En cours / Terminé,
        // calculés en UNE requête groupée sur l'historique de visionnage.
        when (_uiState.value.statusTab) {
            "WATCHING" -> {
                val stats = runCatching { historyDao.statsAll() }.getOrDefault(emptyList())
                    .associateBy { it.mediaId }
                shown = shown.filter { item ->
                    val s = stats[item.id] ?: return@filter false
                    s.watchedCount < s.total || s.total == 0 && s.watchedCount > 0
                }
            }
            "COMPLETED" -> {
                val stats = runCatching { historyDao.statsAll() }.getOrDefault(emptyList())
                    .associateBy { it.mediaId }
                shown = shown.filter { item ->
                    val s = stats[item.id] ?: return@filter false
                    s.total > 0 && s.watchedCount == s.total
                }
            }
        }
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

    /** Bascule affichage fichiers locaux ↔ bibliothèque de la fiche. */
    fun setLocalMode(mode: Boolean) {
        _uiState.value = _uiState.value.copy(localMode = mode)
        if (mode) scanLocal()
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

    /**
     * §scan-rapide : scan multi-dossiers SAF.
     *
     * Deux étapes : (1) la liste des fichiers s'affiche TOUT DE SUITE (un curseur
     * par dossier, cf. LocalVideos.scanAsync) ; (2) les durées, qui exigent
     * d'ouvrir chaque fichier avec MediaMetadataRetriever (très lent : c'était la
     * cause du scan interminable), sont calculées ensuite en tâche de fond, 4 à la
     * fois, et viennent enrichir la liste au fil de l'eau.
     */
    private var durationJob: kotlinx.coroutines.Job? = null

    fun scanLocal() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
                val m = prefs.localFileMeta(f.uri)
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
        _uiState.value = _uiState.value.copy(localFiles = files, localScanning = false, localScanLabel = "")
        // §fiche-locale : partagé avec l'écran de fiche d'un dossier
        dev.endlesssea.app.local.LocalLibraryCache.publish(files)

        durationJob?.cancel()
        durationJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val gate = kotlinx.coroutines.sync.Semaphore(4)
            val todo = files.filter { it.durationMs == null }
            val found = java.util.Collections.synchronizedMap(mutableMapOf<String, Long>())
            kotlinx.coroutines.coroutineScope {
                todo.chunked(25).forEach { chunk ->
                    chunk.map { f ->
                        async {
                            gate.withPermit {
                                dev.endlesssea.app.local.LocalVideos.durationMs(context, f.uri)
                                    ?.let { found[f.uri] = it }
                            }
                        }
                    }.forEach { it.await() }
                    // publication par paquets : la liste se complète sous les yeux
                    val snap = found.toMap()
                    _uiState.value = _uiState.value.copy(
                        localFiles = _uiState.value.localFiles.map { v ->
                            snap[v.uri]?.let { d -> v.copy(durationMs = d) } ?: v
                        },
                    )
                }
            }
        }
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
    fun saveSeriesMeta(
        folderUri: String,
        title: String?,
        description: String? = null,
        author: String? = null,
        genres: List<String> = emptyList(),
    ) {
        val ok = dev.endlesssea.app.local.LocalVideos.writeSeriesMeta(
            context = context,
            folderUriString = folderUri,
            title = title,
            description = description,
            author = author,
            genres = genres,
        )
        // Cache mémoire : la fiche affiche la modification sans attendre un scan.
        dev.endlesssea.app.local.LocalVideos.seriesMeta.compute(folderUri) { _, old ->
            (old ?: dev.endlesssea.app.local.SeriesMeta()).copy(
                title = title?.takeIf { it.isNotBlank() } ?: old?.title,
                description = description?.takeIf { it.isNotBlank() } ?: old?.description,
                author = author?.takeIf { it.isNotBlank() } ?: old?.author,
                genres = genres.ifEmpty { old?.genres ?: emptyList() },
            )
        }
        _uiState.value = _uiState.value.copy(
            localMetaTick = _uiState.value.localMetaTick + 1,
            localScanLabel = if (ok) "details.json enregistré dans le dossier"
            else "Métadonnées gardées dans l'app (dossier non modifiable)",
        )
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
