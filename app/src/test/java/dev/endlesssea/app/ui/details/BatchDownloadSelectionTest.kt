package dev.endlesssea.app.ui.details

import dev.endlesssea.extensions.api.model.*
import org.junit.Assert.*
import org.junit.Test

class BatchDownloadSelectionTest {
    private fun link(server: String, kind: StreamType = StreamType.DIRECT_FILE, lang: AudioLang = AudioLang.VF, quality: Quality = Quality.Q720) =
        VideoLink(url = "https://example.test/$server", server = server, streamType = kind, audioLang = lang, quality = quality)
    @Test fun embedDoesNotBlockNextServer() {
        val file = link("B")
        assertEquals(file, selectBatchDownload(listOf(link("A", StreamType.EMBED), file), listOf("A", "B")))
    }
    @Test fun excludedServerNeverReturnsAsFallback() {
        assertNull(selectBatchDownload(listOf(link("A")), emptyList(), setOf("a")))
    }
    @Test fun languageAndQualityAreStrict() {
        val chosen = link("B", lang = AudioLang.VOSTFR, quality = Quality.Q1080)
        assertEquals(chosen, selectBatchDownload(listOf(link("A"), chosen), listOf("A", "B"), language = AudioLang.VOSTFR, quality = Quality.Q1080))
        assertNull(selectBatchDownload(listOf(link("A")), emptyList(), language = AudioLang.VOSTFR))
    }
    @Test fun choosesBestQualityWithinServerPriority() {
        val chosen = link("A", quality = Quality.Q1080)
        assertEquals(chosen, selectBatchDownload(listOf(link("A"), chosen, link("B", quality = Quality.Q4K)), listOf("A", "B")))
    }
}
