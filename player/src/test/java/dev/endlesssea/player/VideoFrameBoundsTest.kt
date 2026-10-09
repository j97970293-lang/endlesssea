package dev.endlesssea.player

import org.junit.Assert.*
import org.junit.Test

class VideoFrameBoundsTest {
    @Test fun containKeepsSourceAspectAndLetterboxes() {
        val b = videoFrameBounds(1000f, 1000f, 2f, 0)
        assertEquals(1000f, b.width, .01f); assertEquals(500f, b.height, .01f)
    }
    @Test fun cropCoversViewportWithoutStretching() {
        val b = videoFrameBounds(1000f, 1000f, 2f, 1)
        assertEquals(2000f, b.width, .01f); assertEquals(1000f, b.height, .01f)
    }
    @Test fun stretchUsesEntireViewport() { assertEquals(VideoFrameBounds(1000f,1000f), videoFrameBounds(1000f,1000f,2f,2)) }
    @Test fun portraitSourceFitsLandscape() {
        val b = videoFrameBounds(1600f,900f,.5f,0)
        assertEquals(450f,b.width,.01f); assertEquals(900f,b.height,.01f)
    }
    @Test fun invalidAspectFallsBackWithoutNan() { assertEquals(VideoFrameBounds(100f,200f), videoFrameBounds(100f,200f,Float.NaN,0)) }
    @Test fun neutralSettingsRemainNeutral() { assertEquals(LiveVideoSettings(),LiveVideoSettings().bounded()) }
    @Test fun valuesAreBoundedForShader() {
        val s = LiveVideoSettings(brightness=3f,gamma=-1f,sharpness=50f).bounded()
        assertEquals(1f,s.brightness,0f); assertEquals(.1f,s.gamma,0f); assertEquals(2f,s.sharpness,0f)
    }
}
