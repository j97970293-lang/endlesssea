package dev.endlesssea.app.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.endlesssea.data.db.WatchHistoryDao
import dev.endlesssea.data.db.WatchHistoryEntity
import dev.endlesssea.extensions.api.model.VideoLink
import dev.endlesssea.player.EsPlayer
import dev.endlesssea.player.SubtitleStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

data class PlayerUiState(
    val title: String = "",
    val loading: Boolean = true,
    val controlsVisible: Boolean = true,
    val locked: Boolean = false,
    val speed: Float = 1f,
    val error: String? = null,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    okHttp: OkHttpClient,
    private val historyDao: WatchHistoryDao,
) : ViewModel() {

    val engine = EsPlayer(context, okHttp, viewModelScope)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    private var episodeId: String? = null
    private var mediaId: String? = null

    init {
        // Persistance de la position toutes les 5 s (spec §7 « mémorisation de la position »)
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                persistPosition()
            }
        }
    }

    /** Prépare la lecture (stream extension ou fichier local) puis reprend la position. */
    fun prepare(
        mediaId: String?,
        episodeId: String?,
        title: String,
        links: List<VideoLink>,
        startIndex: Int = 0,
    ) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(title = title, loading = true, error = null)
        this@PlayerViewModel.mediaId = mediaId
        this@PlayerViewModel.episodeId = episodeId
        val resumeMs = episodeId?.let { historyDao.byEpisode(it)?.positionMs } ?: 0L
        runCatching {
            engine.prepare(links, startPositionMs = resumeMs)
            engine.player.seekTo(startIndex.coerceAtLeast(0), resumeMs)
            engine.play()
        }.onFailure {
            _uiState.value = _uiState.value.copy(error = it.message ?: "Lecture impossible", loading = false)
        }
        _uiState.value = _uiState.value.copy(loading = false)
    }

    fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
        _uiState.value = _uiState.value.copy(speed = speed)
    }

    fun toggleLock() = _uiState.value.let { _uiState.value = it.copy(locked = !it.locked) }
    fun toggleControls() = _uiState.value.let { _uiState.value = it.copy(controlsVisible = !it.controlsVisible) }
    fun showControls(visible: Boolean) { _uiState.value = _uiState.value.copy(controlsVisible = visible) }

    fun setSubtitleStyle(style: SubtitleStyle) = engine.setSubtitleStyle(style)

    suspend fun persistPosition() {
        val ep = episodeId ?: return
        val pos = engine.positionMs.value
        val dur = engine.durationMs.value
        if (pos <= 0 || dur <= 0) return
        historyDao.upsert(
            WatchHistoryEntity(
                episodeId = ep,
                mediaId = mediaId ?: "",
                positionMs = pos,
                durationMs = dur,
                watched = pos.toFloat() / dur >= 0.9f,   // ≥ 90 % → marqué « vu » (spec §24)
            )
        )
    }

    override fun onCleared() {
        viewModelScope.launch { persistPosition() }
        engine.release()
        super.onCleared()
    }
}
