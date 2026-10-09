package dev.endlesssea.player

/** Pending taps accumulate against the requested position, never a stale decoder position. */
internal class RelativeSeekTarget {
    private var pending: Long? = null
    fun add(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val base = pending ?: positionMs.coerceAtLeast(0L)
        val next = (base + deltaMs).coerceAtLeast(0L)
        return (if (durationMs > 0L) next.coerceAtMost(durationMs) else next).also { pending = it }
    }
    fun take(): Long? = pending.also { pending = null }
    fun clear() { pending = null }
}
