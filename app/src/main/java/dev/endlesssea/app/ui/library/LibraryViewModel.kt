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
    /** §bibliothèque-locale : mode « fichiers vidéo locaux » actif (chip en haut). */
    val localMode: Boolean = false,
    /** Vidéos locales scannées sur les dossiers SAF choisis (multi-dossiers). */
    val localFiles: List<LocalVideoUi> = emptyList(),
    val localScanning: Boolean = false,
    /** Tic de recomposition quand une métadonnée locale change. */
    val localMetaTick: Int = 0,
)

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
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryDao: LibraryDao,
    private val mediaDao: MediaDao,
    private val downloadsDao: dev.endlesssea.data.db.DownloadsDao,
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

    /** Scan multi-dossiers SAF — jamais sur le thread UI (DocumentFile est lent). */
    fun scanLocal() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val dirs = prefs.localVideoDirs.value
        _uiState.value = _uiState.value.copy(localScanning = true)
        val files = dirs.flatMap { dev.endlesssea.app.local.LocalVideos.scan(context, it) }
            .distinctBy { it.uri }
            .sortedBy { it.displayName.lowercase() }
            .map { f ->
                val m = prefs.localFileMeta(f.uri)
                LocalVideoUi(
                    uri = f.uri, name = f.displayName, parentUri = f.parentUri,
                    sizeBytes = f.sizeBytes,
                    durationMs = dev.endlesssea.app.local.LocalVideos.durationMs(context, f.uri),
                    customTitle = m.title, customCoverUri = m.coverUri,
                    introStartSec = m.introStartSec, introEndSec = m.introEndSec,
                    outroStartSec = m.outroStartSec,
                )
            }
        _uiState.value = _uiState.value.copy(localFiles = files, localScanning = false)
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
