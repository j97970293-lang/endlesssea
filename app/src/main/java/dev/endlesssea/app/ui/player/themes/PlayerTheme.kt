package dev.endlesssea.app.ui.player.themes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.endlesssea.app.skip.CustomSkipButton
import dev.endlesssea.app.skip.SkipSegment

/**
 * Contract for the maintained Essentiel player layout. It keeps the control
 * groups independently composable while obsolete skins stay out of the registry.
 * Shared controls use [TopControls], [CenterControls], and [BottomControls].
 */
data class PlayerControlsState(
    val title: String = "",
    /** Épisode ou source affiché sous le titre (thèmes qui le montrent). */
    val subtitle: String = "",
    val playing: Boolean = false,
    val locked: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** Position de glissement en cours (0..1) ou null si l'utilisateur ne glisse pas. */
    val dragFraction: Float? = null,
    val speed: Float = 1f,
    /** 0 = contenir · 1 = recadrer · 2 = étirer · 3 = fond flou sans rognage. */
    val zoomMode: Int = 0,
    /** Un zoom/pan manuel est actif en plus du mode de cadrage choisi. */
    val manualZoom: Boolean = false,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    /** Des liens qualité/serveurs sont disponibles. */
    val hasLinks: Boolean = false,
    val filterActive: Boolean = false,
    val statsVisible: Boolean = false,
    val skipLoading: Boolean = false,
    val megaSkipSeconds: Int = 85,
    val skipSeconds: Int = 10,
    /** La pastille mégaskip et le bouton de saut actif vivent-ils à gauche ? */
    val megaSkipLeft: Boolean = false,
    val activeSkip: SkipSegment? = null,
    val skipCountdown: Int? = null,
    val customSkips: List<CustomSkipButton> = emptyList(),
    val progressThickness: Int = 4,
    val progressRounded: Boolean = true,
    val thumbSize: Int = 12,
    val bufferThickness: Int = 3,
    val autoHideThumb: Boolean = false,
)

/** Toutes les actions d'un thème — aucun thème ne touche directement au moteur. */
class PlayerControlsActions(
    val onBack: () -> Unit = {},
    val onTogglePlay: () -> Unit = {},
    val onPrev: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onDrag: (Float) -> Unit = {},
    val onDragFinished: (Float) -> Unit = {},
    val onJumpRelative: (Int) -> Unit = {},
    val onOpenQuality: () -> Unit = {},
    val onOpenSubtitles: () -> Unit = {},
    val onOpenAudio: () -> Unit = {},
    val onToggleLock: () -> Unit = {},
    val onToggleOrientation: () -> Unit = {},
    val onCycleZoom: () -> Unit = {},
    val onCycleSpeed: () -> Unit = {},
    val onOpenFilters: () -> Unit = {},
    val onToggleStats: () -> Unit = {},
    val onRefreshSkip: () -> Unit = {},
    val onOpenMore: () -> Unit = {},
    val onOpenPlaylist: () -> Unit = {},
    val onMegaJump: () -> Unit = {},
    val onOpenSkipEditor: () -> Unit = {},
    val onSkipSegment: () -> Unit = {},
    val onCustomSkip: (CustomSkipButton) -> Unit = {},
)

/** Un habillage complet du lecteur. */
interface PlayerTheme {
    val id: String
    val label: String

    /** Couleur d'accent ; `null` = couleur du thème de l'application. */
    val accent: Color?
        get() = null

    /** Opacité du voile bas (0 = aucun voile, façon YouTube). */
    val scrim: Float
        get() = 0.55f

    /** Épaisseur de la barre de progression, en dp. */
    val progressThickness: Int
        get() = 4

    /** Barre arrondie (pastille) ou à angles vifs. */
    val rounded: Boolean
        get() = true

    /** Taille du bouton lecture central, en dp. */
    val playSize: Int
        get() = 78

    @Composable
    fun TopControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier = Modifier)

    @Composable
    fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier = Modifier)

    @Composable
    fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier = Modifier)
}

/** Seul habillage maintenu : les anciens identifiants sont migrés vers Essentiel. */
object ThemeProvider {
    val all: Map<String, PlayerTheme> = linkedMapOf("cinema" to CinemaTheme)
    fun of(id: String?): PlayerTheme = all[id] ?: CinemaTheme
    fun migrate(old: String?): String = old?.takeIf { it == "cinema" } ?: "cinema"
}
