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
) : ViewModel() {

    /** Dernier instantané brut (avant filtre/tri) pour re-appliquer la vue sans perdre de lignes. */
    private var rawRows: List<DownloadRowUi> = emptyList()

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

    init {
        viewModelScope.launch {
            // Room fournit la file ordonnée ; le moteur pousse la progression live
            combine(dao.observeAllOrdered(), engine.progress) { tasks, live -> tasks to live }
                .collect { (tasks, live) ->
                    rawRows = tasks.map { t ->
                        val isLive = live.taskId == t.id
                        DownloadRowUi(
                            id = t.id,
                            title = t.fileName.removeSuffix(".part"),
                            detail = "${t.server} · ${t.quality}" +
                                if (t.totalBytes > 0) " · ${formatBytes(t.totalBytes)}" else "",
                            status = t.status,
                            fraction = when {
                                isLive && live.totalBytes > 0 ->
                                    (live.downloadedBytes.toFloat() / live.totalBytes).coerceIn(0f, 1f)
                                t.status == DownloadStatus.COMPLETED.name -> 1f
                                else -> 0f
                            },
                            progressLabel = if (isLive && live.totalBytes > 0) {
                                "${formatBytes(live.downloadedBytes)} / ${formatBytes(live.totalBytes)}" +
                                    if (live.bytesPerSecond > 0) " · ${formatBytes(live.bytesPerSecond)}/s" else ""
                            } else "",
                            error = t.error,
                            createdAt = t.createdAt,
                            totalBytes = t.totalBytes,
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
