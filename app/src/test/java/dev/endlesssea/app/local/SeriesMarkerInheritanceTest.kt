package dev.endlesssea.app.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesMarkerInheritanceTest {
    @Test fun episodeOverridesWinIndependentlyAndMissingValuesInherit() {
        val effective = resolveEpisodeSkipMarkers(
            episode = SeriesSkipMarkers(introStartSec = 12, outroStartSec = 980),
            series = SeriesSkipMarkers(introStartSec = 5, introEndSec = 90, outroStartSec = 1000),
        )
        assertEquals(SeriesSkipMarkers(introStartSec = 12, introEndSec = 90, outroStartSec = 980), effective)
    }

    @Test fun absentPreferenceInheritsAndNegativeSentinelExplicitlyClearsSeriesValue() {
        assertEquals(45, seriesMarkerValue(saved = null, fromDetailsFile = 45))
        assertNull(seriesMarkerValue(saved = -1, fromDetailsFile = 45))
        assertEquals(60, seriesMarkerValue(saved = 60, fromDetailsFile = 45))
    }
}
