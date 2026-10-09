package dev.endlesssea.app.local

import dev.endlesssea.app.ui.library.LocalVideoUi
import org.junit.Assert.*
import org.junit.Test

class LocalEpisodeProjectionTest {
    private fun file(name: String = "Show S02E12.5.mkv") = LocalVideoUi(
        uri = "content://provider/video-42", name = name, parentUri = "content://provider/folder", sizeBytes = 42L)
    @Test fun sameUriUsedForPlaybackEpisodeAndThumbnail() {
        val f = file(); val ep = localEpisode(f)
        assertEquals(f.uri,ep.id); assertEquals(f.uri,ep.data); assertEquals(f.uri,ep.thumbnailUrl)
    }
    @Test fun fractionalNumberAndSeasonSurviveCommonDetailsProjection() {
        val ep = localEpisode(file())
        assertEquals(12.5f,ep.number,0f); assertEquals(2,ep.season)
    }
    @Test fun manualTitleWinsWithoutChangingIdentity() {
        val f = file().copy(customTitle = "Mon titre", matchedTitle = "Titre source")
        val ep = localEpisode(f)
        assertEquals("Mon titre",ep.title); assertEquals(f.uri,ep.id)
    }
    @Test fun importedMetadataDoesNotReplaceVideoByRemotePoster() {
        val f = file().copy(matchedTitle = "Titre importé",matchedSeason = 3,matchedNumber = 12.5f,customCoverUri = "https://poster")
        val ep = localEpisode(f)
        assertEquals("Titre importé",ep.title); assertEquals(3,ep.season); assertEquals(f.uri,ep.thumbnailUrl)
    }
    @Test fun unknownFileIsNotInventedAsEpisodeOneSeasonOne() {
        val ep = localEpisode(file("Bonus interview.mkv"))
        assertFalse(ep.number.isFinite()); assertNull(ep.season)
    }
    @Test fun thousandLocalEpisodesKeepAllIdentities() {
        val episodes = (1..1000).map { localEpisode(file("Episode $it.mkv").copy(uri = "file-$it")) }
        assertEquals(1000,episodes.distinctBy { it.id }.size)
        assertEquals(1000f,episodes.last().number,0f)
    }
}
