package dev.endlesssea.app.ui.player

/**
 * Ensures that one completed stream advances the episode queue at most once.
 * Media3 can report the same ended state more than once while listeners update.
 */
internal class PlaybackAutoAdvanceGate {
    private var lastHandledGeneration: Long? = null

    fun nextIndex(
        generation: Long,
        queueSize: Int,
        currentIndex: Int,
        loading: Boolean,
    ): Int? {
        val previousGeneration = lastHandledGeneration
        if (previousGeneration != null && generation <= previousGeneration) return null
        lastHandledGeneration = generation

        if (loading || currentIndex < 0 || currentIndex >= queueSize - 1) return null
        return currentIndex + 1
    }
}
