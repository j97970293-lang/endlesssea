package dev.endlesssea.app.ui.home

import dev.endlesssea.data.db.WatchHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueWatchingTest {
    private fun entry(media: String, episode: String, at: Long) = WatchHistoryEntity(
        mediaId = media, episodeId = episode, positionMs = 1000, durationMs = 10000, updatedAt = at)
    @Test fun repeatedTitleAtEndHasOnlyOneLazyKey() {
        val result = latestContinueEntries(listOf(entry("a","1",30), entry("b","2",20), entry("a","3",10)))
        assertEquals(listOf("a","b"), result.map { it.mediaId })
        assertEquals("1", result.first().episodeId)
    }
    @Test fun newestEpisodeWinsEvenForUnsortedInput() {
        assertEquals("new", latestContinueEntries(listOf(entry("a","old",1), entry("a","new",2))).single().episodeId)
    }
    @Test fun emptyHistoryIsSupported() { assertEquals(emptyList<WatchHistoryEntity>(), latestContinueEntries(emptyList())) }
}
