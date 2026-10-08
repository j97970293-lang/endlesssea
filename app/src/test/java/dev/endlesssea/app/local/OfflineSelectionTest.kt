package dev.endlesssea.app.local

import org.junit.Assert.*
import org.junit.Test

class OfflineSelectionTest {
    data class Copy(val episode: String?, val uri: String)
    private fun choose(items: List<Copy>, readable: Set<String>) =
        readableDownload(items, "episode-2", { it.episode }, { it.uri }, { it in readable })
    @Test fun readableCopyWinsForTheSameEpisode() {
        val local = Copy("episode-2", "local")
        assertEquals(local, choose(listOf(local), setOf("local")))
    }
    @Test fun deletedCopyReturnsToOnlineResolution() {
        assertNull(choose(listOf(Copy("episode-2", "deleted")), emptySet()))
    }
    @Test fun anotherEpisodeNeverReplacesRequestedEpisode() {
        assertNull(choose(listOf(Copy("episode-1", "local")), setOf("local")))
    }
    @Test fun aRemainingReadableQualityCanReplaceDeletedQuality() {
        val remaining = Copy("episode-2", "720p")
        assertEquals(remaining, choose(listOf(Copy("episode-2", "1080p"), remaining), setOf("720p")))
    }
    @Test fun unknownEpisodeIdentityIsNotGuessedFromFilename() {
        assertNull(choose(listOf(Copy(null, "episode-2.mp4")), setOf("episode-2.mp4")))
    }
    @Test fun unrelatedEpisodesAreNotProbedOnTap() {
        var probes = 0
        readableDownload((1..1000).map { Copy("episode-$it", "$it") }, "episode-2",
            { it.episode }, { it.uri }, { probes++; true })
        assertEquals(1, probes)
    }
}
