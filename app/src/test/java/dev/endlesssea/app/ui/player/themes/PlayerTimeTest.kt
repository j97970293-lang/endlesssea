package dev.endlesssea.app.ui.player.themes

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTimeTest {
    @Test fun clampsNegativeAndZero() {
        assertEquals("0:00", fmtTime(-1000))
        assertEquals("0:00", fmtTime(0))
    }
    @Test fun formatsMinutesAndHours() {
        assertEquals("1:05", fmtTime(65_000))
        assertEquals("1:02:03", fmtTime(3_723_000))
    }
}
