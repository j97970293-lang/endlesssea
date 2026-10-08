package dev.endlesssea.app.ui.player.themes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.endlesssea.app.skip.CustomSkipButton
import dev.endlesssea.app.skip.SkipSegment

/**
 * §theme-lecteur (conversations 1 & 2) — un thème de lecteur n'est pas une
 * couleur : c'est une **mise en page complète** des commandes. Chaque thème
 * fournit ses propres barres :
 *
 *  - [TopControls]    : retour, titre, pistes, verrouillage ;
 *  - [CenterControls] : transport central (précédent · lecture · suivant,
 *                       sauts ±10 s selon le thème) ;
 *  - [BottomControls] : ligne de temps, outils, mégaskip.
 *
 * Les thèmes se contentent de composer les briques partagées de
 * [dev.endlesssea.app.ui.player.themes] (SkinSeekBar, SkinPlayButton…) et
 * peuvent ignorer une barre (ex. Apple TV+ n'affiche presque rien).
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
    /** 0 = contenir · 1 = remplir · 2 = étirer. */
    val zoomMode: Int = 0,
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
    /** Disposition héritée des réglages : ligne de temps / outils en haut. */
    val progressOnTop: Boolean = false,
    val toolsOnTop: Boolean = false,
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

/** Registre unique des interfaces originales. Les anciens choix reviennent au défaut. */
object ThemeProvider {
    val all: Map<String, PlayerTheme> = linkedMapOf(
        "cinema" to CinemaTheme,
        "default" to DefaultTheme,
        "zen" to ZenTheme,
        "orbit" to OrbitTheme,
        "compactbar" to CompactBarTheme,
        "neonframe" to NeonFrameTheme,
        "split" to SplitControlsTheme,
        "floating" to FloatingCardsTheme,
    )
    fun of(id: String?): PlayerTheme = all[id] ?: DefaultTheme
    fun migrate(old: String?): String = old?.takeIf { it in all } ?: "default"
}
