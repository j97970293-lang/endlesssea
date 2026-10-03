package dev.endlesssea.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.core.model.DownloadStatus
import dev.endlesssea.data.db.DownloadsDao
import dev.endlesssea.downloader.DownloadEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class DownloadsUiState(
    val active: List<DownloadRowUi> = emptyList(),
    val finished: List<DownloadRowUi> = emptyList(),
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val dao: DownloadsDao,
    private val engine: DownloadEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

    init {
        viewModelScope.launch {
            dao.observeAllOrdered().collect { tasks ->
                val ui = tasks.map { t ->
                    val done = t.status == DownloadStatus.COMPLETED.name
                    DownloadRowUi(
                        id = t.id,
                        title = t.fileName.removeSuffix(".part"),
                        detail = "${t.server} · ${t.quality}" +
                            if (t.totalBytes > 0) " · ${formatBytes(t.totalBytes)}" else "",
                        status = t.status,
                        fraction = 0f,                   // live fraction merged from engine.progress
                        progressLabel = "",
                        error = t.error,
                    )
                }
                _uiState.value = DownloadsUiState(
                    active = ui.filter { it.status != DownloadStatus.COMPLETED.name && it.status != DownloadStatus.CANCELLED.name },
                    finished = ui.filter { it.status == DownloadStatus.COMPLETED.name },
                )
            }
        }
    }

    fun pause(id: String) = viewModelScope.launch { engine.pause(id) }
    fun resume(id: String) = viewModelScope.launch { engine.resume(id) }
    fun cancel(id: String) = viewModelScope.launch { engine.cancel(id) }

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f Go", bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f Mo", bytes / (1L shl 20).toDouble())
            else -> String.format(Locale.ROOT, "%.0f Ko", bytes / 1024.0)
        }
    }
}
