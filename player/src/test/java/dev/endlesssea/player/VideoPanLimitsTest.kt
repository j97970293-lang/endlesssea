package dev.endlesssea.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPanLimitsTest {
    @Test fun fitAllowsRepositioningInsideLetterboxOnly() {
        val limits=videoPanLimits(1920f,1080f,4f/3f,0,1f)
        assertEquals(240f,limits.width,0.01f)
        assertEquals(0f,limits.height,0.01f)
    }
    @Test fun fillAllowsOnlyCroppingMargin() {
        val limits=videoPanLimits(1920f,1080f,4f/3f,1,1f)
        assertEquals(0f,limits.width,0.01f)
        assertEquals(180f,limits.height,0.01f)
    }
    @Test fun zoomHasBoundedTravel() {
        val limits=videoPanLimits(1920f,1080f,16f/9f,0,2f)
        assertEquals(960f,limits.width,0.01f)
        assertEquals(540f,limits.height,0.01f)
    }
    @Test fun blurredBackdropUsesTheSamePanLimitsAsFit() {
        assertEquals(
            videoPanLimits(1920f, 1080f, 4f / 3f, 0, 1f),
            videoPanLimits(1920f, 1080f, 4f / 3f, 3, 1f),
        )
    }
    @Test fun badScaleCannotProduceNanOffsets() {
        val limits=videoPanLimits(1920f,1080f,16f/9f,0,Float.NaN)
        assertEquals(0f,limits.width,0.01f)
        assertEquals(0f,limits.height,0.01f)
    }
}
