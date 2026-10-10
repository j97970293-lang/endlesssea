package dev.endlesssea.app.ui.details

import org.junit.Assert.*
import org.junit.Test

class TrailerLookupTest {
    @Test fun parsesPublicCataloguesAndRejectsUnsafeValues() {
        val ani = """{"data":{"Page":{"media":[{"trailer":{"id":"abcdefghijk","site":"youtube"}}]}}}"""
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", TrailerLookup.fromAniList(ani))
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", TrailerLookup.fromKitsu(
            """{"data":[{"attributes":{"youtubeVideoId":"abcdefghijk"}}]}""",
        ))
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", TrailerLookup.fromJikan(
            """{"data":[{"trailer":{"youtube_id":"abcdefghijk","url":"https://www.youtube.com/watch?v=abcdefghijk"}}]}""",
        ))
        assertNull(TrailerLookup.normalize("javascript:alert(1)"))
        assertNull(TrailerLookup.normalize("https://evil.example/watch?v=abcdefghijk"))
        assertNull(TrailerLookup.youtubeWatch("short"))
    }

    @Test fun aniListPayloadKeepsTheTitleOutOfTheQueryText() {
        val payload = TrailerLookup.aniListPayload("Attack on Titan")
        assertTrue(payload.contains("\"search\":\"Attack on Titan\""))
        assertFalse(payload.contains("Attack on Titan)"))
    }
}
