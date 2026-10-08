package dev.endlesssea.app.ui.player.themes

import org.junit.Assert.*
import org.junit.Test

class ReferencePlayerPlacementTest {
    @Test fun headerDoesNotReceiveTimelineMegaskipOrTools() { assertFalse(ReferencePlayerPlacement.showBottomSections(true)) }
    @Test fun bottomReceivesTheReferenceSections() { assertTrue(ReferencePlayerPlacement.showBottomSections(false)) }
    @Test fun callingBothAnchorsRendersExactlyOneSet() {
        assertEquals(1, listOf(true, false).count { ReferencePlayerPlacement.showBottomSections(it) })
    }
}
