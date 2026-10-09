package dev.endlesssea.player

/** A double tap starts seeking; every subsequent tap in the burst is ONE additional seek. */
class SeekTapSequence(private val doubleTapMs: Long, private val burstMs: Long = 900L) {
    private var previousAt: Long? = null
    private var previousSide: Int = 0
    private var seeking = false

    fun tap(nowMs: Long, side: Int): Boolean {
        val elapsed = previousAt?.let { nowMs - it }
        val seek = elapsed != null && elapsed >= 0 &&
            if (seeking) elapsed <= burstMs else elapsed <= doubleTapMs && side == previousSide
        previousAt = nowMs
        previousSide = side
        seeking = seek
        return seek
    }

    fun reset() { previousAt = null; seeking = false }
}
