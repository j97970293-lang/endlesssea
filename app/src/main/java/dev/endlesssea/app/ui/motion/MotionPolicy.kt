package dev.endlesssea.app.ui.motion

/** Short, shared timings; Android applies its own positive duration scale. */
data class MotionPolicy(val enabled: Boolean, val active: Boolean) {
    val loop: Boolean get() = enabled && active
    fun duration(milliseconds: Int): Int = if (enabled) milliseconds.coerceIn(0, 400) else 0
    companion object {
        const val PRESS = 140
        const val ENTER = 220
        const val EXIT = 160
        fun resolve(preference: Boolean, systemScale: Float, active: Boolean) =
            MotionPolicy(preference && systemScale.isFinite() && systemScale > 0f, active)
    }
}
