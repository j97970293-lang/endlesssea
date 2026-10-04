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
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

/** Préréglage de filtre vidéo sauvegardé (teinte/saturation/luminosité). */
data class VideoFilterPreset(val name: String, val brightness: Float, val saturation: Float, val hue: Float)

data class PlayerUiState(
    val title: String = "",
    val loading: Boolean = true,
    val controlsVisible: Boolean = true,
    val locked: Boolean = false,
    val speed: Float = 1f,
    val error: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val skipSeconds: Int = 10,
    // Filtre vidéo courant (constant : aucun = tout à 0)
    val filterBrightness: Float = 0f,
    val filterSaturation: Float = 0f,
    val filterHue: Float = 0f,
    val filterPresetName: String = "none",
    val savedPresets: List<VideoFilterPreset> = emptyList(),
    /** Affichage bref du saut (double appui) : « −15 s » / « +15 s » ou null. */
    val skipFlash: String? = null,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    okHttp: OkHttpClient,
    private val historyDao: WatchHistoryDao,
    private val prefs: dev.endlesssea.app.di.AppPrefs,
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
        // Progression live (barre de lecture)
        viewModelScope.launch {
            while (isActive) {
                _uiState.value = _uiState.value.copy(
                    positionMs = engine.player.currentPosition.coerceAtLeast(0),
                    durationMs = engine.player.duration.coerceAtLeast(0),
                )
                delay(500)
            }
        }
        // Préférences lecteur : saut double appui + dernier filtre utilisé
        viewModelScope.launch {
            prefs.skipSeconds.collect { v -> _uiState.value = _uiState.value.copy(skipSeconds = v) }
        }
        viewModelScope.launch {
            prefs.videoPresetsJson.collect { _uiState.value = _uiState.value.copy(savedPresets = parsePresets(it)) }
        }
        applyFilter(
            prefs.videoBrightness.value, prefs.videoSaturation.value, prefs.videoHue.value,
            prefs.videoPreset.value, persist = false,
        )
    }

    // ------------------------------------------------------------------ filtres vidéo

    private fun parsePresets(json: String): List<VideoFilterPreset> = runCatching {
        val arr = org.json.JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    VideoFilterPreset(
                        name = o.getString("name"),
                        brightness = o.optDouble("b", 0.0).toFloat(),
                        saturation = o.optDouble("s", 0.0).toFloat(),
                        hue = o.optDouble("h", 0.0).toFloat(),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    /** Applique le triplet au moteur (et persiste si [persist]=true). Valeurs -100..100 / hue -180..180. */
    fun applyFilter(brightness: Float, saturation: Float, hue: Float, presetName: String, persist: Boolean = true) {
        val hl = androidx.media3.effect.HslAdjustment.Builder()
            .adjustBrightness(brightness)
            .adjustSaturation(saturation)
            .adjustHue(hue)
            .build()
        engine.applyVideoEffects(listOf(hl))
        _uiState.value = _uiState.value.copy(
            filterBrightness = brightness, filterSaturation = saturation,
            filterHue = hue, filterPresetName = presetName,
        )
        if (persist) prefs.setVideoFilter(brightness, saturation, hue, presetName)
    }

    fun applyPreset(preset: VideoFilterPreset) =
        applyFilter(preset.brightness, preset.saturation, preset.hue, preset.name)

    /** Enregistre le filtre courant comme préréglage nommé (dans les préférences JSON). */
    fun saveCurrentAsPreset(name: String) {
        val n = name.trim().ifBlank { return }
        val current = _uiState.value
        val next = (current.savedPresets.filter { it.name != n } +
            VideoFilterPreset(n, current.filterBrightness, current.filterSaturation, current.filterHue))
        val arr = org.json.JSONArray()
        next.forEach {
            arr.put(org.json.JSONObject().apply {
                put("name", it.name); put("b", it.brightness.toDouble()); put("s", it.saturation.toDouble()); put("h", it.hue.toDouble())
            })
        }
        prefs.setVideoPresetsJson(arr.toString())
        _uiState.value = _uiState.value.copy(filterPresetName = n)
    }

    fun deletePreset(name: String) {
        val next = _uiState.value.savedPresets.filter { it.name != name }
        val arr = org.json.JSONArray()
        next.forEach {
            arr.put(org.json.JSONObject().apply {
                put("name", it.name); put("b", it.brightness.toDouble()); put("s", it.saturation.toDouble()); put("h", it.hue.toDouble())
            })
        }
        prefs.setVideoPresetsJson(arr.toString())
    }

    // ------------------------------------------------------------------ sauts

    /** Double appui à gauche/droite : avance/recul de [seconds], flash visuel côté écran. */
    fun jumpBy(seconds: Int) {
        engine.seekBy(seconds * 1000L)
        _uiState.value = _uiState.value.copy(
            skipFlash = if (seconds > 0) "+${seconds} s" else "−${seconds.absoluteValue} s",
        )
        viewModelScope.launch {
            delay(700)
            _uiState.value = _uiState.value.copy(skipFlash = null)
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
