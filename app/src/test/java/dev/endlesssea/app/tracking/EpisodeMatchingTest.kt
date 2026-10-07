package dev.endlesssea.app.tracking

import org.junit.Assert.*
import org.junit.Test

class EpisodeMatchingTest {
    private fun next(number: Float?, progress: Int = 0, total: Int = 12, enabled: Boolean = true, season: Int? = 1, matched: Int? = 1) =
        EpisodeMatching.nextProgress(enabled, matched, season, number, progress, total)

    @Test fun disabledIsAlwaysManual() { assertNull(next(3f, enabled = false)) }
    @Test fun usesNumberNotWatchCount() { assertEquals(3, next(3f)); assertEquals(12, next(12f)) }
    @Test fun rewatchOrOlderEpisodeDoesNotIncrement() {
        assertNull(next(3f, progress = 3)); assertNull(next(2f, progress = 3))
    }
    @Test fun refusesUnknownFractionalSpecialAndInvalidNumbers() {
        listOf(null, 0f, -1f, 1.5f, Float.NaN, Float.POSITIVE_INFINITY, Int.MAX_VALUE.toFloat()).forEach { assertNull(next(it)) }
    }
    @Test fun doesNotExceedKnownTotal() { assertNull(next(13f)) }
    @Test fun ignoresOtherSeasons() { assertNull(next(3f, season = 2)); assertNull(next(3f, season = null)) }
    @Test fun supportsUnknownTotalAndUnspecifiedSeasonExplicitly() {
        assertEquals(40, next(40f, total = 0, season = null, matched = null))
        assertNull(next(3f, season = 1, matched = null))
    }
}
