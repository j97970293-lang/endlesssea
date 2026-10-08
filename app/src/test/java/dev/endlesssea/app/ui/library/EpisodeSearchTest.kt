package dev.endlesssea.app.ui.library

import org.junit.Assert.*
import org.junit.Test

class EpisodeSearchTest {
    @Test fun findEpisodeThousandWithoutWalkingTheWholeList() {
        assertEquals(listOf(1000), (1..1100).filter { matchesEpisodeSearch("1000", it.toDouble(), "Épisode $it") })
    }
    @Test fun exactNumberDoesNotMatchLongerNumber() { assertFalse(matchesEpisodeSearch("1000", 10001.0, "10001")) }
    @Test fun supportsNumberPrefix() { assertTrue(matchesEpisodeSearch("#1000", 1000.0)) }
    @Test fun fractionalSpecialIsNotTruncated() {
        assertTrue(matchesEpisodeSearch("12,5", 12.5))
        assertFalse(matchesEpisodeSearch("12,5", 12.0))
    }
    @Test fun titleSearchIsCaseInsensitive() { assertTrue(matchesEpisodeSearch("final", 23.0, "La FINALE")) }
    @Test fun missingNumberCanStillBeFoundByFilename() { assertTrue(matchesEpisodeSearch("OVA", null, "Episode OVA.mp4")) }
    @Test fun clearingQueryRestoresAllEpisodes() { assertTrue(matchesEpisodeSearch("  ", 8.0)) }
}
