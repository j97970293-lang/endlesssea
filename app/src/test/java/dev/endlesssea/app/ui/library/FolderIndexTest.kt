package dev.endlesssea.app.ui.library

import org.junit.Assert.*
import org.junit.Test

class FolderIndexTest {
    data class Episode(val uri: String, val folder: String)
    private fun index(files: List<Episode>, managed: Set<String> = emptySet()) =
        indexFolders(files, managed, { it.uri }, { it.folder })

    @Test fun thousandEpisodesProduceOnePosterEntry() {
        val result = index((1..1000).map { Episode("file:///series/ep$it.mp4", "file:///series") })
        assertEquals(1, result.size)
        assertEquals(1000, result.single().episodes.size)
    }
    @Test fun foldersWithIdenticalNamesOnDifferentVolumesStaySeparate() {
        assertEquals(2, index(listOf(Episode("a", "internal/Series"), Episode("b", "sd/Series"))).size)
    }
    @Test fun overlappingScansDoNotDuplicateEpisodes() {
        val ep = Episode("file:///series/1.mp4", "file:///series")
        assertEquals(1, index(listOf(ep, ep)).single().episodes.size)
    }
    @Test fun managedDownloadsAreNotRepeatedAsLocalFolders() {
        assertTrue(index(listOf(Episode("file:///d/1.mp4", "file:///d")), setOf("file:///d/1.mp4")).isEmpty())
    }
    @Test fun unrelatedLocalEpisodeIsKeptWhenFolderAlsoContainsDownloads() {
        val result = index(listOf(Episode("managed", "series"), Episode("own", "series")), setOf("managed"))
        assertEquals(listOf(Episode("own", "series")), result.single().episodes)
    }
    @Test fun safAliasesUseDocumentIdentityNotTreePermission() {
        val a = "content://provider/tree/primary%3AA/document/primary%3AA%2F1.mp4"
        val b = "content://provider/tree/primary%3A/document/primary%3AA%2F1.mp4"
        assertEquals(localFileIdentity(a), localFileIdentity(b))
        assertTrue(index(listOf(Episode(a, "series")), setOf(b)).isEmpty())
    }
    @Test fun differentProvidersAreNeverMergedByName() {
        assertNotEquals(localFileIdentity("content://one/document/1.mp4"), localFileIdentity("content://two/document/1.mp4"))
    }
    @Test fun missingParentsDoNotCreateOneHugeUnknownSeries() {
        assertEquals(2, index(listOf(Episode("a", ""), Episode("b", ""))).size)
    }
    @Test fun emptyLibraryHasNoInventedPoster() { assertTrue(index(emptyList()).isEmpty()) }
}
