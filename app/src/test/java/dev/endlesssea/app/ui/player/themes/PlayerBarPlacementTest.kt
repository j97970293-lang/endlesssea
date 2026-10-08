package dev.endlesssea.app.ui.player.themes

import org.junit.Assert.*
import org.junit.Test

class PlayerBarPlacementTest {
    @Test fun eachPreferenceCombinationRendersEachSectionExactlyOnce() {
        for (progressTop in listOf(false, true)) for (toolsTop in listOf(false, true)) {
            val placement = PlayerBarPlacement(progressTop, toolsTop)
            assertEquals(progressTop, placement.showsProgress(true))
            assertEquals(toolsTop, placement.showsTools(true))
            assertNotEquals(placement.showsProgress(true), placement.showsProgress(false))
            assertNotEquals(placement.showsTools(true), placement.showsTools(false))
        }
    }
}
