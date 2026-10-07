package dev.endlesssea.app.ui.details

import org.junit.Assert.*
import org.junit.Test

class TrailerUrlsTest {
    @Test fun youtubeQueryOrderAndShorts() {
        assertEquals("https://www.youtube.com/embed/abcdefghijk?playsinline=1&rel=0", TrailerUrls.embed("https://www.youtube.com/watch?feature=share&v=abcdefghijk"))
        assertNotNull(TrailerUrls.embed("https://youtube.com/shorts/abcdefghijk"))
        assertNotNull(TrailerUrls.embed("https://youtu.be/abcdefghijk?t=12"))
    }
    @Test fun rejectsSpoofedHostsAndUnsafeSchemes() {
        assertNull(TrailerUrls.embed("https://youtube.com.evil/watch?v=abcdefghijk"))
        assertNull(TrailerUrls.embed("javascript:alert(1)"))
        assertNull(TrailerUrls.embed("https://youtu.be/invalid"))
        assertFalse(TrailerUrls.isDirect("file:///secret.mp4"))
    }
    @Test fun directVideoWithQueryAndProviderEmbeds() {
        assertTrue(TrailerUrls.isDirect("https://example.org/Trailer.MP4?token=x"))
        assertTrue(TrailerUrls.isDirect("https://example.org/master.m3u8"))
        assertFalse(TrailerUrls.isDirect("https://example.org/page"))
        assertEquals("https://player.vimeo.com/video/1234", TrailerUrls.embed("https://vimeo.com/1234"))
        assertEquals("https://www.dailymotion.com/embed/video/x1234", TrailerUrls.embed("https://dai.ly/x1234"))
    }
}
