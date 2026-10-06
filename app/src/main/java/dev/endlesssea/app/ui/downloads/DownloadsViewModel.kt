package dev.endlesssea.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.core.model.DownloadStatus
import dev.endlesssea.data.db.DownloadsDao
import dev.endlesssea.downloader.DownloadEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class DownloadFilter(val label: String) {
    TOUS("Tous"),
    EN_COURS("En cours"),
    PAUSE("En pause"),
    ECHEC("Échoués"),
    TERMINE("Terminés"),
}

enum class DownloadSort(val label: String) {
    DATE("Date"),
    TAILLE("Taille"),
    NOM("Nom"),
}

data class DownloadsUiState(
    /** Rangées déjà filtrées + triées pour l'affichage. */
    val rows: List<DownloadRowUi> = emptyList(),
    /** Message d'action (export/déplacement, erreur ou confirmation). */
    val notice: String? = null,
    val filter: DownloadFilter = DownloadFilter.TOUS,
    val sort: DownloadSort = DownloadSort.DATE,
    val ascending: Boolean = false,
    val totalCount: Int = 0,
)

/**
 * Onglet Téléchargements : file temps réel (moteur + Room), filtres par statut,
 * tri date/taille/nom, réorganisation de la file (priorités).
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val dao: DownloadsDao,
    private val engine: DownloadEngine,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    /** Dernier instantané brut (avant filtre/tri) pour re-appliquer la vue sans perdre de lignes. */
    private var rawRows: List<DownloadRowUi> = emptyList()

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

    init {
        viewModelScope.launch {
            // Room fournit la file ordonnée ; le moteur pousse la progression live
            combine(dao.observeAllOrdered(), engine.progressByTask) { tasks, live -> tasks to live }
                .collect { (tasks, liveMap) ->
                    rawRows = tasks.map { t ->
                        val live = liveMap[t.id]
                        val isLive = live != null && t.status == DownloadStatus.DOWNLOADING.name
                        DownloadRowUi(
                            id = t.id,
                            title = t.fileName.removeSuffix(".part"),
                            detail = "${t.server} · ${t.quality}" +
                                if (t.totalBytes > 0) " · ${formatBytes(t.totalBytes)}" else "" +
                                    (t.displayPath.substringBeforeLast('/')
                                        .takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            status = t.status,
                            fraction = when {
                                t.status == DownloadStatus.COMPLETED.name -> 1f
                                isLive && live!!.totalBytes > 0 ->
                                    (live.downloadedBytes.toFloat() / live.totalBytes).coerceIn(0f, 1f)
                                else -> 0f
                            },
                            progressLabel = when {
                                isLive && live!!.totalBytes > 0 ->
                                    "${formatBytes(live.downloadedBytes)} / ${formatBytes(live.totalBytes)}" +
                                        if (live.speedBytesPerSec > 0) " · ${formatBytes(live.speedBytesPerSec)}/s" else ""
                                isLive && live!!.downloadedBytes > 0 ->
                                    "${formatBytes(live.downloadedBytes)} téléchargés"
                                t.status == DownloadStatus.COMPLETED.name && t.totalBytes > 0 ->
                                    formatBytes(t.totalBytes)
                                else -> ""
                            },
                            error = t.error,
                            createdAt = t.createdAt,
                            totalBytes = t.totalBytes,
                            targetUri = t.targetUri,
                        )
                    }
                    applyView(rawRows)
                }
        }
    }

    /** Re-applique filtre + tri sur la liste brute fournie. */
    private fun applyView(rows: List<DownloadRowUi>) {
        val st = _uiState.value
        val filtered = rows.filter { row ->
            when (st.filter) {
                DownloadFilter.TOUS -> true
                DownloadFilter.EN_COURS -> row.status in setOf(
                    DownloadStatus.QUEUED.name, DownloadStatus.PROBING.name, DownloadStatus.DOWNLOADING.name,
                )
                DownloadFilter.PAUSE -> row.status == DownloadStatus.PAUSED.name
                DownloadFilter.ECHEC -> row.status == DownloadStatus.FAILED.name
                DownloadFilter.TERMINE -> row.status == DownloadStatus.COMPLETED.name
            }
        }
        val sorted = when (st.sort) {
            DownloadSort.DATE -> filtered.sortedBy { it.createdAt }
            DownloadSort.TAILLE -> filtered.sortedBy { it.totalBytes }
            DownloadSort.NOM -> filtered.sortedBy { it.title.lowercase() }
        }.let { if (st.ascending) it else it.reversed() }
        _uiState.value = st.copy(rows = sorted, totalCount = rows.size)
    }

    fun setFilter(filter: DownloadFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        applyView(rawRows)
    }

    fun setSort(sort: DownloadSort) {
        _uiState.value = _uiState.value.copy(sort = sort)
        applyView(rawRows)
    }

    fun toggleOrder() {
        _uiState.value = _uiState.value.copy(ascending = !_uiState.value.ascending)
        applyView(rawRows)
    }

    fun clearNotice() { _uiState.value = _uiState.value.copy(notice = null) }

    /** §retrouver-téléchargements : lecture directe d'un fichier terminé (file:// ou SAF). */
    fun play(id: String, onReady: () -> Unit) = viewModelScope.launch {
        val task = dao.byId(id) ?: return@launch
        if (task.status != DownloadStatus.COMPLETED.name) return@launch
        dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
            title = task.fileName.removeSuffix(".part"),
            mediaId = task.mediaId, episodeId = task.episodeId ?: id,
            links = listOf(
                dev.endlesssea.extensions.api.model.VideoLink(
                    url = task.targetUri,
                    streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                    quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN,
                    server = "Téléchargé",
                ),
            ),
            startIndex = 0,
        )
        onReady()
    }

    /** §deplacer-téléchargement : copie vers un répertoire SAF (carte SD incluse) puis
     * réoriente la tâche vers la nouvelle URI et supprime l'original local. */
    fun exportToTree(id: String, treeUri: String) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val task = dao.byId(id) ?: return@launch
        if (task.status != DownloadStatus.COMPLETED.name) return@launch
        val resolver = context.contentResolver
        val u = android.net.Uri.parse(task.targetUri)
        val srcFile = if (u.scheme == null || u.scheme == "file") {
            java.io.File(u.path ?: task.targetUri.removePrefix("file://"))
        } else {
            null
        }
        val mime = when {
            task.fileName.endsWith(".mkv", true) -> "video/x-matroska"
            task.fileName.endsWith(".webm", true) -> "video/webm"
            else -> "video/mp4"
        }
        val displayName = task.fileName.removeSuffix(".part")
        val result = runCatching {
            val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                context, android.net.Uri.parse(treeUri),
            ) ?: error("Dossier illisible")
            val doc = tree.createFile(mime, displayName) ?: error("Création du fichier impossible")
            resolver.openOutputStream(doc.uri, "rwt")?.use { out ->
                if (srcFile != null) {
                    srcFile.inputStream().use { it.copyTo(out) }
                } else {
                    resolver.openInputStream(u)?.use { it.copyTo(out) } ?: error("Source introuvable")
                }
            } ?: error("Ouverture de la destination impossible")
            val folder = tree.name ?: "dossier choisi"
            dao.setTarget(id, doc.uri.toString(), "$folder/$displayName")
            runCatching { srcFile?.delete() }
            "Déplacé vers « $folder » — visible dans vos dossiers."
        }
        _uiState.value = _uiState.value.copy(notice = result.getOrElse { "Déplacement impossible : ${it.message}" })
    }

    fun pause(id: String) = viewModelScope.launch { engine.pause(id) }
    fun resume(id: String) = viewModelScope.launch { engine.resume(id) }
    fun cancel(id: String) = viewModelScope.launch { engine.cancel(id) }
    fun reorder(id: String, up: Boolean) = viewModelScope.launch { engine.reorder(id, up) }

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f Go", bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f Mo", bytes / (1L shl 20).toDouble())
            else -> String.format(Locale.ROOT, "%.0f Ko", bytes / 1024.0)
        }
    }
}
