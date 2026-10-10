package dev.endlesssea.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackAutoAdvanceGateTest {
    @Test fun advancesOncePerPlaybackGeneration() {
        val gate = PlaybackAutoAdvanceGate()

        assertEquals(2, gate.nextIndex(generation = 10, queueSize = 4, currentIndex = 1, loading = false))
        assertNull(gate.nextIndex(generation = 10, queueSize = 4, currentIndex = 1, loading = false))
        assertEquals(3, gate.nextIndex(generation = 11, queueSize = 4, currentIndex = 2, loading = false))
        // A delayed duplicate from the prior item must not skip another queue entry.
        assertNull(gate.nextIndex(generation = 10, queueSize = 4, currentIndex = 2, loading = false))
    }

    @Test fun doesNotAdvanceForLoadingOrLastQueueItem() {
        val gate = PlaybackAutoAdvanceGate()

        assertNull(gate.nextIndex(generation = 1, queueSize = 3, currentIndex = 1, loading = true))
        assertNull(gate.nextIndex(generation = 2, queueSize = 3, currentIndex = 2, loading = false))
        assertNull(gate.nextIndex(generation = 3, queueSize = 0, currentIndex = 0, loading = false))
        assertNull(gate.nextIndex(generation = 4, queueSize = 3, currentIndex = -1, loading = false))
    }
}
