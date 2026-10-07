package dev.endlesssea.app.local

import org.junit.Assert.*
import org.junit.Test

class LocalEpisodeMatchingTest {
    @Test fun seasonIsIndependentOfRenamedDisplayTitle() {
        assertEquals(2, LocalVideos.episodeSeason("Show S02E03.mkv"))
        assertEquals(3, LocalVideos.episodeNumber("Show S02E03.mkv"))
        assertNull(LocalVideos.episodeSeason("03 - Show.mkv"))
    }
    @Test fun fractionalEpisodesAreNotRoundedDown() {
        assertNull(LocalVideos.episodeNumber("Show E12.5.mkv"))
        assertNull(LocalVideos.episodeNumber("12.5 - Special.mkv"))
        assertEquals(10, LocalVideos.episodeNumber("Show.S02E10.1080p.mkv"))
        assertEquals(3, LocalVideos.episodeNumber("Show E03.720p.mp4"))
    }
    @Test fun folderIdentityIsStableAndDistinct() {
        assertEquals("local:content://folder/one", LocalMediaIds.series("content://folder/one"))
        assertNotEquals(LocalMediaIds.series("content://folder/one"), LocalMediaIds.series("content://folder/two"))
    }
}
