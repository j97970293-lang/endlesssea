package dev.endlesssea.core.util

import org.junit.Assert.*
import org.junit.Test

class VideoFileNamesTest {
    private fun name(movie: Boolean, season: Int?=1, number: Float=1f) = FileNames.videoName(
        "Mon titre", movie, 2024, season, number, null, "1080p", "VF", ".mp4")
    @Test fun movieHasNoSeasonOrEpisode() { assertEquals("Mon titre (2024) [1080p] [VF].mp4",name(true)) }
    @Test fun seriesHasSortableNumbering() { assertEquals("Mon titre (2024) - S02E03 [1080p] [VF].mp4",name(false,2,3f)) }
    @Test fun unknownSeasonIsNotInvented() { assertTrue(name(false,null,12f).contains(" - E12 ")) }
    @Test fun specialsKeepZeroAndFraction() { assertTrue(name(false,0,2.5f).contains("S00E02.5")) }
    @Test fun longTitleKeepsEpisodeAndExtension() {
        val value=FileNames.videoName("a".repeat(300),false,null,2,1001f,null,"UNKNOWN","OTHER","ts")
        assertTrue(value.endsWith(" - S02E1001.ts"))
    }
}
