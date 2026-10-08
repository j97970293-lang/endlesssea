package dev.endlesssea.app.local

import org.junit.Assert.*
import org.junit.Test

class LocalEpisodeMatchTest {
    private data class Item(val id: String, val key: EpisodeMatchKey?)
    private fun match(left: List<Item>, right: List<Item>) = matchLocalEpisodes(left, right, { it.key }, { it.key })
    private fun item(id: String, season: Int, episode: Double) = Item(id, EpisodeMatchKey(season, episode))
    @Test fun matchUsesSeasonAndNumberNotListPosition() {
        val local = item("local", 2, 10.0)
        val target = item("remote", 2, 10.0)
        assertEquals(listOf(local to target), match(listOf(local), listOf(item("other", 1, 10.0), target)))
    }
    @Test fun thousandEpisodesKeepTheirOriginalLocalIdentities() {
        val local = (1..1000).map { item("file-$it", 1, it.toDouble()) }
        val remote = (1..1000).reversed().map { item("online-$it", 1, it.toDouble()) }
        val result = match(local, remote)
        assertEquals(1000, result.size)
        result.forEach { (a,b) -> assertEquals(a.key, b.key); assertTrue(a.id.startsWith("file-")) }
    }
    @Test fun duplicateLocalNumbersAreNotGuessed() {
        assertTrue(match(listOf(item("a",1,1.0), item("b",1,1.0)), listOf(item("online",1,1.0))).isEmpty())
    }
    @Test fun duplicateRemoteNumbersAreNotGuessed() {
        assertTrue(match(listOf(item("a",1,1.0)), listOf(item("one",1,1.0), item("two",1,1.0))).isEmpty())
    }
    @Test fun unknownSeasonOrEpisodeIsLeftUnchanged() {
        assertTrue(match(listOf(Item("unknown", null)), listOf(item("one",1,1.0))).isEmpty())
    }
    @Test fun specialsAreNotRoundedToAnInteger() {
        assertEquals(1, match(listOf(item("special",1,12.5)), listOf(item("normal",1,12.0), item("special",1,12.5))).size)
    }
    @Test fun wrongSeasonNeverMatches() {
        assertTrue(match(listOf(item("local",2,1.0)), listOf(item("remote",1,1.0))).isEmpty())
    }
    @Test fun invalidNumbersAreRejected() {
        for (number in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertTrue(match(listOf(item("local",1,number)), listOf(item("remote",1,number))).isEmpty())
        }
    }
    @Test fun floatSourceNumbersMatchDecimalFilenameNumbers() {
        assertEquals(EpisodeMatchKey(1,12.1), onlineEpisodeKey(1,12.1f))
    }
    @Test fun missingOnlineSeasonDoesNotDefaultToSeasonOne() { assertNull(onlineEpisodeKey(null, 1f)) }
    @Test fun bareNumberedFilesCanBeAssociatedWithAnExplicitSeason() {
        assertEquals(1000, LocalVideos.episodeNumber("01000.mkv"))
        assertEquals(1, LocalVideos.episodeNumber("1.mp4"))
        assertNull(LocalVideos.episodeSeason("1.mp4"))
    }
    @Test fun decimalFilenameMatchesNormalizedOnlineNumber() {
        assertEquals(onlineEpisodeKey(2,12.1f), EpisodeMatchKey(2, playbackEpisodeOrder("Show S02E12.1.mkv")!!))
    }
    @Test fun emptyInputHasNoAssociations() { assertTrue(match(emptyList(), emptyList()).isEmpty()) }
    @Test fun unrelatedRemoteEpisodesDoNotChangeMatches() {
        val local = item("local",1,5.0)
        assertEquals(1, match(listOf(local), listOf(item("target",1,5.0), item("unrelated",4,90.0))).size)
    }
}
