package dev.endlesssea.app.ui.details

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaShareLinksTest {
    @Test fun linkKeepsCompositeExtensionIdAsOnePathSegment() {
        val mediaId = "catalog:https://source.test/anime/season 1?id=42"
        val link = mediaShareLink(mediaId)

        assertTrue(link.startsWith("endlesssea://media/"))
        val encodedSegment = link.substringAfter("endlesssea://media/")
        assertEquals(mediaId, URLDecoder.decode(encodedSegment, StandardCharsets.UTF_8.name()))
    }
}
