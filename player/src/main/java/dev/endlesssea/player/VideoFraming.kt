package dev.endlesssea.player

/** Modes mémorisés : 0 ajuster, 1 recadrer, 2 étirer, 3 fond flou, puis ratios forcés. */
const val FRAMING_MODE_COUNT = 9

/** Ordre d'un vrai lecteur : ajuster, remplir, étirer, puis les ratios. Le fond flou reste accessible. */
val FRAMING_CYCLE = intArrayOf(0, 1, 2, 4, 5, 6, 7, 8, 3)

fun nextFramingMode(current: Int): Int {
    val index = FRAMING_CYCLE.indexOf(current).let { if (it < 0) 0 else it }
    return FRAMING_CYCLE[(index + 1) % FRAMING_CYCLE.size]
}

data class VideoDisplayScale(val scaleX: Float, val scaleY: Float)

/** Ratio imposé par le mode, ou null pour garder celui de la source. */
fun forcedDisplayAspect(mode: Int): Float? = when (mode) {
    4 -> 16f / 9f
    5 -> 4f / 3f
    6 -> 21f / 9f
    7 -> 18f / 9f
    8 -> 2.35f
    else -> null
}

/**
 * Échelle à appliquer à une vidéo déjà contenue (resize FIT) dans le viewport.
 * Le lecteur n'est pas touché : seul un calque graphique change, donc la lecture continue.
 *
 * 0 ajuster, 1 recadrer, 2 étirer, 3 ajuster (le fond flou est un calque séparé),
 * 4 16:9, 5 4:3, 6 21:9, 7 18:9, 8 2.35:1.
 */
fun videoDisplayScale(viewW: Float, viewH: Float, sourceAspect: Float, mode: Int): VideoDisplayScale {
    if (viewW <= 0f || viewH <= 0f) return VideoDisplayScale(1f, 1f)
    val aspect = sourceAspect.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    val fit = videoFrameBounds(viewW, viewH, aspect, 0)
    if (fit.width <= 0f || fit.height <= 0f) return VideoDisplayScale(1f, 1f)
    val desired = when (mode) {
        1 -> videoFrameBounds(viewW, viewH, aspect, 1)
        2 -> VideoFrameBounds(viewW, viewH)
        else -> {
            val forced = forcedDisplayAspect(mode)
            if (forced == null) fit else videoFrameBounds(viewW, viewH, forced, 0)
        }
    }
    fun axis(value: Float) = value.takeIf { it.isFinite() && it > 0f } ?: 1f
    return VideoDisplayScale(axis(desired.width / fit.width), axis(desired.height / fit.height))
}
