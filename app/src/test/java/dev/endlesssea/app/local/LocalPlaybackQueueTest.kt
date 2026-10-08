package dev.endlesssea.app.local

import dev.endlesssea.app.ui.library.LocalVideoUi
import org.junit.Assert.*
import org.junit.Test

class LocalPlaybackQueueTest {
    private fun file(id: String, name: String, folder: String = "show") = LocalVideoUi(id, name, folder, 0)
    @Test fun ordersEpisodesNumericallyAndExcludesOtherSeries() {
        val current = file("2", "S01E02.mkv")
        val queue = localPlaybackQueue(listOf(file("10", "S01E10.mkv"), file("1", "S01E01.mkv"), current,
            file("other", "S01E03.mkv", "other")), current)
        assertEquals(listOf("1", "2", "10"), queue.map { it.uri })
    }
    @Test fun coldResumeStillIncludesSelectedFileExactlyOnce() {
        val current = file("2", "E02.mkv")
        assertEquals(listOf(current), localPlaybackQueue(emptyList(), current))
        assertEquals(listOf(current), localPlaybackQueue(listOf(current, current), current))
    }
    @Test fun unknownParentDoesNotMixUnrelatedFiles() {
        val current = file("2", "E02.mkv", "")
        assertEquals(listOf(current), localPlaybackQueue(listOf(file("1", "E01.mkv", "")), current))
    }
    @Test fun longSeriesDoNotTruncateEpisodeNumbers() {
        assertEquals(1000, LocalVideos.episodeNumber("Show.S01E1000.mkv"))
        assertEquals(1234, LocalVideos.episodeNumber("Episode 1234.mp4"))
    }
    @Test fun fractionalSpecialStaysBetweenItsNeighborsWithoutIntegerTracking() {
        val special = file("special", "S01E12.5.mkv")
        val queue = localPlaybackQueue(listOf(file("13", "S01E13.mkv"), special, file("12", "S01E12.mkv")), special)
        assertEquals(listOf("12", "special", "13"), queue.map { it.uri })
        assertNull(special.episodeNumber)
    }
    @Test fun renamedTitlesDoNotChangeOrder() {
        val first = file("1", "E01.mkv").copy(customTitle = "Zebra")
        val second = file("2", "E02.mkv").copy(customTitle = "Apple")
        assertEquals(listOf(first, second), localPlaybackQueue(listOf(second, first), second))
    }
}
