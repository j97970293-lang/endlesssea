package dev.endlesssea.player

import org.junit.Assert.*
import org.junit.Test

class VideoFramingTest {
    @Test fun fitLeavesTheContainedPictureUntouched() {
        assertEquals(VideoDisplayScale(1f, 1f), videoDisplayScale(1000f, 1000f, 2f, 0))
        assertEquals(VideoDisplayScale(1f, 1f), videoDisplayScale(1000f, 1000f, 2f, 3))
    }

    @Test fun cropAndStretchAreUniformOrIndependent() {
        val crop = videoDisplayScale(1000f, 1000f, 2f, 1)
        assertEquals(2f, crop.scaleX, .01f)
        assertEquals(2f, crop.scaleY, .01f)
        val stretch = videoDisplayScale(1000f, 1000f, 2f, 2)
        assertEquals(1f, stretch.scaleX, .01f)
        assertEquals(2f, stretch.scaleY, .01f)
    }

    @Test fun forcedRatioStretchesTheContainedPictureWithoutNan() {
        val scale = videoDisplayScale(1000f, 1000f, 2f, 4)
        assertEquals(1f, scale.scaleX, .01f)
        assertEquals(1.125f, scale.scaleY, .01f)
        val invalid = videoDisplayScale(100f, 80f, Float.NaN, 6)
        assertTrue(invalid.scaleX.isFinite() && invalid.scaleY.isFinite())
        assertTrue(invalid.scaleX > 0f && invalid.scaleY > 0f)
    }

    @Test fun cycleVisitsEveryModeOnceThenWraps() {
        val seen = mutableSetOf<Int>()
        var mode = 0
        repeat(FRAMING_MODE_COUNT) {
            seen += mode
            mode = nextFramingMode(mode)
        }
        assertEquals((0 until FRAMING_MODE_COUNT).toSet(), seen)
        assertEquals(1, nextFramingMode(0))
        assertEquals(0, nextFramingMode(3))
    }

    @Test fun panLimitsFollowTheTransformedPicture() {
        val limits = videoPanLimits(1000f, 1000f, 2f, 1, 1f)
        assertEquals(500f, limits.width, .01f)
        assertEquals(0f, limits.height, .01f)
    }
}
