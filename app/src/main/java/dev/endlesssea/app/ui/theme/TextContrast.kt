package dev.endlesssea.app.ui.theme

import kotlin.math.pow
import kotlin.math.roundToInt

internal fun contrastRatio(a: Int, b: Int): Double {
    fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val v = ((color ushr shift) and 255) / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }
    val x = luminance(a); val y = luminance(b)
    return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
}

/** Keep the user's hue where possible, but require readable text against every solid surface. */
internal fun readableColor(color: Int, backgrounds: List<Int>): Int {
    if (backgrounds.isEmpty()) return color
    fun worst(candidate: Int) = backgrounds.minOf { contrastRatio(candidate, it) }
    if (worst(color) >= 4.5) return color
    val target = if (worst(-1) >= worst(0xFF000000.toInt())) -1 else 0xFF000000.toInt()
    for (step in 1..100) {
        var mixed = 0xFF000000.toInt()
        for (shift in listOf(0, 8, 16)) {
            val from = (color ushr shift) and 255
            val to = (target ushr shift) and 255
            mixed = mixed or ((from + (to - from) * step / 100.0).roundToInt() shl shift)
        }
        if (worst(mixed) >= 4.5) return mixed
    }
    return target
}
