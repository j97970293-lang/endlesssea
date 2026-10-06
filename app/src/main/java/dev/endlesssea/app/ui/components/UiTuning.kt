package dev.endlesssea.app.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Paramètres visuels globaux en direct (alimentés par [dev.endlesssea.app.di.AppPrefs],
 * lus par les composants « verre », les cartes et la barre sans injection Hilt).
 *
 * IMPORTANT : l'application pousse ici les valeurs persistées au démarrage
 * (voir MainActivity), donc aucun accès disque au composable.
 */
object UiTuning {
    private val _glassOverlay = MutableStateFlow(12)
    private val _glassScrim = MutableStateFlow(25)
    private val _cardStyle = MutableStateFlow("saikou")
    private val _glassTint = MutableStateFlow<Long?>(null)
    private val _liquid = MutableStateFlow(40)
    private val _bloom = MutableStateFlow(false)

    // §anymex-ui — multiplicateurs de la page « Interface »
    private val _glow = MutableStateFlow(1f)
    private val _radius = MutableStateFlow(1f)
    private val _blur = MutableStateFlow(1f)
    private val _roundness = MutableStateFlow(1f)
    private val _cardAnimation = MutableStateFlow(true)
    private val _carouselStyle = MutableStateFlow("classic")
    private val _historyCardStyle = MutableStateFlow("bootiful")
    private val _animations = MutableStateFlow(true)
    private val _borders = MutableStateFlow(18)

    /** Opacité de la couche « verre » (%) — 0 (invisible) .. 30 (laiteux). Défaut 12. */
    val glassOverlay: StateFlow<Int> = _glassOverlay

    /** Intensité du flou simulé = assombrissement du fond derrière la carte (%) — 0..100. Défaut 25. */
    val glassScrim: StateFlow<Int> = _glassScrim

    /** Style des cartes : "saikou" | "exotic" | "minimal_exotic" | "modern". */
    val cardStyle: StateFlow<String> = _cardStyle

    /** Variante Glass §24 : ARGB de teinte dominante du verre (null = neutre auto). */
    val glassTint: StateFlow<Long?> = _glassTint

    /** §verre-liquide : intensité du reflet « liquide » (0..100, défaut 40). */
    val liquid: StateFlow<Int> = _liquid

    /** §anymex-theme : halos amplifiés du fond liquide (Bloom d'AnyMEX). */
    val bloom: StateFlow<Boolean> = _bloom

    /** Multiplicateur de halo lumineux des éléments (0..5). */
    val glow: StateFlow<Float> = _glow

    /** Multiplicateur du rayon des coins de l'interface (0..5). */
    val radius: StateFlow<Float> = _radius

    /** Multiplicateur de flou des halos (0..5). */
    val blur: StateFlow<Float> = _blur

    /** Multiplicateur d'arrondi des cartes média (0..5). */
    val roundness: StateFlow<Float> = _roundness

    /** Animation d'appui sur les cartes média. */
    val cardAnimation: StateFlow<Boolean> = _cardAnimation

    /** Carrousel d'accueil : "classic" | "portrait". */
    val carouselStyle: StateFlow<String> = _carouselStyle

    /** Cartes d'historique : "regular" | "frosted" | "bootiful". */
    val historyCardStyle: StateFlow<String> = _historyCardStyle

    /** Animations globales (défilement auto des carrousels, transitions). */
    val animations: StateFlow<Boolean> = _animations

    /** §bordures : force des liserés (0 = aucun liseré nulle part, 100 = marqués). */
    val borders: StateFlow<Int> = _borders

    fun updateBorders(v: Int) { _borders.value = v.coerceIn(0, 100) }

    fun updateBloom(v: Boolean) { _bloom.value = v }

    fun updateAnimations(v: Boolean) { _animations.value = v }

    fun update(overlay: Int, scrim: Int, style: String, tintArgb: Long? = null, liquid: Int = 40) {
        _glassOverlay.value = overlay.coerceIn(0, 30)
        _glassScrim.value = scrim.coerceIn(0, 100)
        _cardStyle.value = style
        _glassTint.value = tintArgb
        _liquid.value = liquid.coerceIn(0, 100)
    }

    /** §anymex-ui : pousse les réglages « Interface » (multiplicateurs + styles). */
    fun updateStyles(
        glow: Float,
        radius: Float,
        blur: Float,
        roundness: Float,
        cardAnimation: Boolean,
        carouselStyle: String,
        historyCardStyle: String,
    ) {
        _glow.value = glow.coerceIn(0f, 5f)
        _radius.value = radius.coerceIn(0f, 5f)
        _blur.value = blur.coerceIn(0f, 5f)
        _roundness.value = roundness.coerceIn(0f, 5f)
        _cardAnimation.value = cardAnimation
        _carouselStyle.value = carouselStyle
        _historyCardStyle.value = historyCardStyle
    }
}
