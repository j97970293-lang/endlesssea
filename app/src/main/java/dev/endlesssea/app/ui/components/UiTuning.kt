package dev.endlesssea.app.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Paramètres visuels globaux en direct (alimentés par [dev.endlesssea.app.di.AppPrefs],
 * lus par les composants « verre » et les cartes sans injection Hilt).
 *
 * IMPORTANT : l'application pousse ici les valeurs persistées au démarrage
 * (voir MainActivity), donc aucun accès disque au composable.
 */
object UiTuning {
    private val _glassOverlay = MutableStateFlow(12)
    private val _glassScrim = MutableStateFlow(25)
    private val _cardStyle = MutableStateFlow("detail")
    private val _glassTint = MutableStateFlow<Long?>(null)
    private val _liquid = MutableStateFlow(40)

    /** Opacité de la couche « verre » (%) — 0 (invisible) .. 30 (laiteux). Défaut 12. */
    val glassOverlay: StateFlow<Int> = _glassOverlay

    /** Intensité du flou simulé = assombrissement du fond derrière la carte (%) — 0..100. Défaut 25. */
    val glassScrim: StateFlow<Int> = _glassScrim

    /** Style des cartes : "detail" (affiche + titre + badge), "poster" (affiche seule), "minimal" (sans badge). */
    val cardStyle: StateFlow<String> = _cardStyle

    /** Variante Glass §24 : ARGB de teinte dominante du verre (null = neutre auto). */
    val glassTint: StateFlow<Long?> = _glassTint

    /** §verre-liquide : intensité du reflet « liquide » (0..100, défaut 40). */
    val liquid: StateFlow<Int> = _liquid

    fun update(overlay: Int, scrim: Int, style: String, tintArgb: Long? = null, liquid: Int = 40) {
        _glassOverlay.value = overlay.coerceIn(0, 30)
        _glassScrim.value = scrim.coerceIn(0, 100)
        _cardStyle.value = style
        _glassTint.value = tintArgb
        _liquid.value = liquid.coerceIn(0, 100)
    }
}
