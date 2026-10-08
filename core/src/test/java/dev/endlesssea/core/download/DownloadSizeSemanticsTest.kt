package dev.endlesssea.core.download

import dev.endlesssea.core.model.DownloadProgress
import dev.endlesssea.core.model.DownloadStatus
import org.junit.Assert.*
import org.junit.Test

class DownloadSizeSemanticsTest {
    @Test fun playlistDisguisedAsDirectIsNotMeasuredAsAVideo() {
        assertEquals(DownloadSourceKind.HLS, downloadSourceKind("DIRECT_FILE", "https://host/MASTER.M3U8?token=x#part"))
    }
    @Test fun manifestDisguisedAsDirectRemainsPlaybackOnly() {
        assertEquals(DownloadSourceKind.DASH, downloadSourceKind("DIRECT_FILE", "https://host/file.mpd?x=1"))
    }
    @Test fun queryTextCannotChangeSourceType() {
        assertEquals(DownloadSourceKind.DIRECT, downloadSourceKind("DIRECT_FILE", "https://host/movie.mp4?next=.m3u8"))
    }
    @Test fun hlsWithoutSuffixStillHasUnknownSize() {
        assertEquals(DownloadSourceKind.HLS, downloadSourceKind("HLS", "https://host/stream"))
    }
    @Test fun segmentProgressDoesNotInventAByteTotal() {
        val p = DownloadProgress("x", DownloadStatus.DOWNLOADING, 0, 300_000_000, 0, -1, 3, 6)
        assertEquals(0L, p.totalBytes)
        assertEquals(0.5f, p.fraction, 0f)
        assertEquals(0.5f, p.copy(downloadedBytes = 600_000_000).fraction, 0f)
    }
    @Test fun directProgressStillUsesBytes() {
        assertEquals(0.25f, DownloadProgress("x", DownloadStatus.DOWNLOADING, 400, 100, 0, -1).fraction, 0f)
    }
    @Test fun unknownAndOutOfBoundsProgressStaysSafe() {
        val p = DownloadProgress("x", DownloadStatus.DOWNLOADING, 0, 500, 0, -1)
        assertEquals(0f, p.fraction, 0f)
        assertEquals(1f, p.copy(completedSegments = 10, totalSegments = 5).fraction, 0f)
        assertEquals(0f, p.copy(completedSegments = -1, totalSegments = 5).fraction, 0f)
    }
}
