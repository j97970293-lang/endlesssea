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
    /** §mégaskip : secondes du grand saut (bouton dédié). */
    val megaSkipSeconds: Int = 85,
    /** §auto-skip : sauter seul les marqueurs intro/outro. */
    val autoSkipMarkers: Boolean = true,
    /** Liens de la lecture courante (sélecteur qualité §qualité-lecteur). */
    val links: List<VideoLink> = emptyList(),
    /** Index du lien actuellement joué (pour le badge du dialogue qualité). */
    val currentLinkIndex: Int = 0,
    // Filtre vidéo courant (constant : aucun = tout à 0)
    val filterBrightness: Float = 0f,
    val filterSaturation: Float = 0f,
    val filterHue: Float = 0f,
    val filterPresetName: String = "none",
    val savedPresets: List<VideoFilterPreset> = emptyList(),
    /** Affichage bref du saut (double appui) : « −15 s » / « +15 s » ou null. */
    val skipFlash: String? = null,
    /** §minuterie : timestamp de fin de minuterie (null = inactive), pause auto. */
    val sleepEndAt: Long? = null,
    /** Notification brève affichée en surimpression (ex: pause par minuterie). */
    val toast: String? = null,
    /** §épisode-suivant : y a-t-il un élément avant / après dans la file ? */
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    // §filtres-video : réglages avancés (contraste, gamma, netteté, température)
    val filterContrast: Float = 1f,
    val filterGamma: Float = 1f,
    val filterSharp: Float = 0f,
    val filterTemp: Float = 0f,
    /** §amelioration-video : "none" | "net" | "eclat" | "doux" | "cinema" | "nuit". */
    val enhance: String = "none",
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
            prefs.megaSkipSeconds.collect { v -> _uiState.value = _uiState.value.copy(megaSkipSeconds = v) }
        }
        viewModelScope.launch {
            prefs.autoSkipMarkers.collect { v -> _uiState.value = _uiState.value.copy(autoSkipMarkers = v) }
        }
        viewModelScope.launch {
            prefs.videoPresetsJson.collect { _uiState.value = _uiState.value.copy(savedPresets = parsePresets(it)) }
        }
        _uiState.value = _uiState.value.copy(
            filterContrast = prefs.videoContrast.value,
            filterGamma = prefs.videoGamma.value,
            filterSharp = prefs.videoSharp.value,
            filterTemp = prefs.videoTemp.value,
            enhance = prefs.videoEnhance.value,
        )
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
        _uiState.value = _uiState.value.copy(
            filterBrightness = brightness, filterSaturation = saturation,
            filterHue = hue, filterPresetName = presetName,
        )
        if (persist) prefs.setVideoFilter(brightness, saturation, hue, presetName)
        rebuildEffects()
    }

    /** §filtres-video : contraste / gamma (via luminosité) / netteté / température. */
    fun applyAdvanced(contrast: Float, gamma: Float, sharp: Float, temp: Float) {
        _uiState.value = _uiState.value.copy(
            filterContrast = contrast, filterGamma = gamma,
            filterSharp = sharp, filterTemp = temp,
        )
        prefs.setVideoAdvanced(contrast, gamma, sharp, temp)
        rebuildEffects()
    }

    /** §amelioration-video : profils d'amélioration d'image légers (sans surcoût GPU notable). */
    fun setEnhance(mode: String) {
        _uiState.value = _uiState.value.copy(enhance = mode)
        prefs.setVideoEnhance(mode)
        rebuildEffects()
    }

    /**
     * Recompose TOUTE la chaîne d'effets (filtres + amélioration) et la pousse au
     * moteur. Un seul point d'entrée : c'est ce qui corrige les filtres « qui ne
     * s'appliquaient qu'après avoir quitté la vidéo » (chaînes concurrentes).
     */
    private fun rebuildEffects() {
        val st = _uiState.value
        val effects = mutableListOf<androidx.media3.common.Effect>()
        // 1) profil d'amélioration
        when (st.enhance) {
            // §anime-4k : netteté GPU (unsharp mask 5 échantillons) — l'esprit
            // d'Anime4K sans ses multiples passes qui font ramer un téléphone.
            // §upscale : on AGRANDIT vraiment l'image (480p → 720p/1080p) avant
            // d'appliquer la netteté — c'est l'ordre qui compte : agrandir puis
            // renforcer les contours donne le rendu « anime HD », l'inverse
            // ne fait que grossir les pixels.
            "720p" -> {
                runCatching {
                    effects += androidx.media3.effect.Presentation.createForHeight(720)
                }
                runCatching { effects += dev.endlesssea.player.SharpenEffect(0.35f) }
                effects += androidx.media3.effect.HslAdjustment.Builder()
                    .adjustSaturation(6f).build()
            }
            "1080p" -> {
                runCatching {
                    effects += androidx.media3.effect.Presentation.createForHeight(1080)
                }
                runCatching { effects += dev.endlesssea.player.SharpenEffect(0.45f) }
                effects += androidx.media3.effect.HslAdjustment.Builder()
                    .adjustSaturation(8f).build()
            }
            "anime" -> {
                runCatching {
                    effects += androidx.media3.effect.Presentation.createForHeight(720)
                }
                runCatching { effects += dev.endlesssea.player.SharpenEffect(0.30f) }
                effects += androidx.media3.effect.HslAdjustment.Builder()
                    .adjustSaturation(8f).build()
            }
            "anime_fort" -> {
                runCatching {
                    effects += androidx.media3.effect.Presentation.createForHeight(1080)
                }
                runCatching { effects += dev.endlesssea.player.SharpenEffect(0.45f) }
                runCatching { effects += androidx.media3.effect.Contrast(0.06f) }
                effects += androidx.media3.effect.HslAdjustment.Builder()
                    .adjustSaturation(10f).build()
            }
            "net" -> {
                runCatching { effects += dev.endlesssea.player.SharpenEffect(0.18f) }
                effects += androidx.media3.effect.HslAdjustment.Builder()
                    .adjustSaturation(4f).build()
            }
            "eclat" -> {
                runCatching { effects += androidx.media3.effect.Contrast(0.22f) }
                effects += androidx.media3.effect.HslAdjustment.Builder()
                    .adjustSaturation(18f).build()
            }
            "doux" -> {
                // « anti-grain » : on baisse légèrement le contraste et la saturation,
                // ce qui noie le bruit de compression sans flouter l'image.
                runCatching { effects += androidx.media3.effect.Contrast(-0.08f) }
                effects += androidx.media3.effect.HslAdjustment.Builder()
                    .adjustSaturation(-6f).adjustLightness(2f).build()
            }
            "cinema" -> {
                runCatching {
                    effects += androidx.media3.effect.RgbAdjustment.Builder()
                        .setRedScale(1.06f).setGreenScale(1.0f).setBlueScale(0.94f).build()
                }
                runCatching { effects += androidx.media3.effect.Contrast(0.10f) }
            }
            "nuit" -> {
                effects += androidx.media3.effect.HslAdjustment.Builder()
                    .adjustLightness(14f).build()
                runCatching { effects += androidx.media3.effect.Contrast(-0.05f) }
            }
        }
        // 2) réglages fins de l'utilisateur
        if (st.filterContrast != 1f) {
            runCatching {
                effects += androidx.media3.effect.Contrast((st.filterContrast - 1f).coerceIn(-1f, 1f))
            }
        }
        if (st.filterTemp != 0f) {
            runCatching {
                val t = st.filterTemp.coerceIn(-1f, 1f)
                effects += androidx.media3.effect.RgbAdjustment.Builder()
                    .setRedScale(1f + 0.15f * t)
                    .setBlueScale(1f - 0.15f * t)
                    .build()
            }
        }
        if (st.filterSharp > 0f) {
            runCatching { effects += dev.endlesssea.player.SharpenEffect(st.filterSharp * 0.6f) }
        }
        // 3) le triplet classique (luminosité / saturation / teinte), toujours en dernier
        val lightness = st.filterBrightness + (st.filterGamma - 1f) * 20f
        effects += androidx.media3.effect.HslAdjustment.Builder()
            .adjustLightness(lightness)
            .adjustSaturation(st.filterSaturation)
            .adjustHue(st.filterHue)
            .build()
        engine.applyVideoEffects(effects)
    }

    fun applyPreset(preset: VideoFilterPreset) =
        applyFilter(preset.brightness, preset.saturation, preset.hue, preset.name)

    // ---- §minuterie (mpv-infinity) : 0/off ou 15/30/45/60 min puis pause automatique
    private var sleepJob: kotlinx.coroutines.Job? = null

    fun scheduleSleep(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) {
            _uiState.value = _uiState.value.copy(sleepEndAt = null, toast = "Minuterie désactivée")
            return
        }
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _uiState.value = _uiState.value.copy(sleepEndAt = endAt, toast = "Pause dans $minutes min")
        sleepJob = viewModelScope.launch {
            kotlinx.coroutines.delay(minutes * 60_000L)
            engine.pause()
            _uiState.value = _uiState.value.copy(sleepEndAt = null, toast = "Minuterie — lecture mise en pause")
        }
    }

    fun clearToast() { if (_uiState.value.toast != null) _uiState.value = _uiState.value.copy(toast = null) }

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
        _uiState.value = _uiState.value.copy(links = links, currentLinkIndex = startIndex.coerceAtLeast(0))
        val resumeMs = episodeId?.let { historyDao.byEpisode(it)?.positionMs } ?: 0L
        runCatching {
            engine.prepare(links, startPositionMs = resumeMs)
            engine.player.seekTo(startIndex.coerceAtLeast(0), resumeMs)
            engine.play()
        }.onFailure {
            _uiState.value = _uiState.value.copy(error = it.message ?: "Lecture impossible", loading = false)
        }
        _uiState.value = _uiState.value.copy(loading = false)
        refreshQueueFlags()
    }

    /**
     * §épisode-suivant — passe à l'élément voisin de la file (offset −1 ou +1).
     * Fonctionne pour les épisodes d'extension (liens résolus à la demande) comme
     * pour les vidéos locales (liens déjà connus).
     */
    fun playQueueOffset(offset: Int) = viewModelScope.launch {
        val queue = PlayerLaunchStore.queue
        val target = PlayerLaunchStore.queueIndex + offset
        if (target !in queue.indices) return@launch
        persistPosition()
        val item = queue[target]
        _uiState.value = _uiState.value.copy(loading = true, title = item.title, error = null)
        val links = item.links.ifEmpty {
            val id = item.episodeId
            if (id == null) emptyList()
            else runCatching { PlayerLaunchStore.resolver?.invoke(id) ?: emptyList() }.getOrDefault(emptyList())
        }
        if (links.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                loading = false,
                toast = "Aucun lien pour « ${item.title} »",
            )
            return@launch
        }
        PlayerLaunchStore.queueIndex = target
        prepare(
            mediaId = mediaId, episodeId = item.episodeId,
            title = item.title, links = links, startIndex = 0,
        )
    }

    private fun refreshQueueFlags() {
        val q = PlayerLaunchStore.queue
        val i = PlayerLaunchStore.queueIndex
        _uiState.value = _uiState.value.copy(
            hasPrev = i > 0 && q.isNotEmpty(),
            hasNext = i >= 0 && i < q.lastIndex,
        )
    }

    fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
        _uiState.value = _uiState.value.copy(speed = speed)
    }

    /** §orientation-lecteur : mémorise le choix portrait/paysage. */
    fun setOrientationPreference(v: String) = prefs.setPlayerOrientation(v)

    /** §qualité-lecteur : bascule vers le lien i (même position) sans relancer l'activité. */
    fun switchQuality(index: Int) {
        if (index !in _uiState.value.links.indices || index == _uiState.value.currentLinkIndex) return
        val pos = engine.player.currentPosition.coerceAtLeast(0)
        engine.player.seekTo(index, pos)
        engine.play()
        _uiState.value = _uiState.value.copy(currentLinkIndex = index)
    }

    fun megaJump(deltaSec: Int) = viewModelScope.launch {
        engine.player.seekTo((engine.player.currentPosition + deltaSec * 1000L).coerceAtLeast(0))
    }

    // §lecteur-placement / §theme-lecteur : réglages lus directement par l'écran
    val progressPosition: kotlinx.coroutines.flow.StateFlow<String> get() = prefs.progressPosition
    val toolsPosition: kotlinx.coroutines.flow.StateFlow<String> get() = prefs.toolsPosition
    val megaSkipSide: kotlinx.coroutines.flow.StateFlow<String> get() = prefs.megaSkipSide
    val progressThickness: kotlinx.coroutines.flow.StateFlow<Int> get() = prefs.progressThickness
    val progressRounded: kotlinx.coroutines.flow.StateFlow<Boolean> get() = prefs.progressRounded
    val playerTheme: kotlinx.coroutines.flow.StateFlow<String> get() = prefs.playerTheme
    fun setProgressPosition(v: String) = prefs.setProgressPosition(v)
    fun setToolsPosition(v: String) = prefs.setToolsPosition(v)
    fun setMegaSkipSide(v: String) = prefs.setMegaSkipSide(v)
    fun setProgressThickness(v: Int) = prefs.setProgressThickness(v)
    fun setProgressRounded(v: Boolean) = prefs.setProgressRounded(v)
    fun setPlayerTheme(v: String) = prefs.setPlayerTheme(v)

    /** §rendu-vidéo : "texture" (filtres) ou "surface" (perf/HDR). */
    val videoRender: kotlinx.coroutines.flow.StateFlow<String> get() = prefs.videoRender
    fun setVideoRender(v: String) = prefs.setVideoRender(v)

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
