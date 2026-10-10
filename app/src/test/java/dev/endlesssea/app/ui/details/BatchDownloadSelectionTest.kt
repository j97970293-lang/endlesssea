package dev.endlesssea.app.ui.details

import dev.endlesssea.extensions.api.model.*
import org.junit.Assert.*
import org.junit.Test

class BatchDownloadSelectionTest {
    private fun link(server: String, kind: StreamType = StreamType.DIRECT_FILE, lang: AudioLang = AudioLang.VF, quality: Quality = Quality.Q720) =
        VideoLink(url = "https://example.test/$server", server = server, streamType = kind, audioLang = lang, quality = quality)
    @Test fun partialListDownloadsWithoutWaitingForThePreferredServer() {
        val ready = link("Autre")
        assertEquals(ready, selectBatchDownload(listOf(ready), listOf("Prioritaire", "Autre")))
        assertNull(selectBatchDownload(listOf(link("Prioritaire", StreamType.EMBED)), listOf("Prioritaire")))
    }
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
        assertEquals(link("A"), selectBatchDownload(listOf(link("A")), emptyList(), language = AudioLang.VOSTFR))
    }
    @Test fun choosesBestQualityWithinServerPriority() {
        val chosen = link("A", quality = Quality.Q1080)
        assertEquals(chosen, selectBatchDownload(listOf(link("A"), chosen, link("B", quality = Quality.Q4K)), listOf("A", "B")))
    }

    @Test fun preferredQualityTargetsResolutionWithoutOverridingServerOrder() {
        val q720 = link("A", quality = Quality.Q720)
        val q1080 = link("A", quality = Quality.Q1080)
        val q4k = link("A", quality = Quality.Q4K)
        assertEquals(q1080, preferredPlaybackLink(listOf(q720, q1080, q4k), "1080"))
        assertEquals(q720, preferredPlaybackLink(listOf(q720, q4k), "1080"))
        assertEquals(q4k, preferredPlaybackLink(listOf(q720, q4k), "auto"))

        val preferredServer = link("A", quality = Quality.Q720)
        val higherQualityElsewhere = link("B", quality = Quality.Q1080)
        assertEquals(
            preferredServer,
            selectBatchDownload(
                listOf(higherQualityElsewhere, preferredServer), listOf("A", "B"), preferredQuality = "1080",
            ),
        )

        val vf = link("A", lang = AudioLang.VF, quality = Quality.Q720)
        val vostfr = link("A", lang = AudioLang.VOSTFR, quality = Quality.Q1080)
        assertEquals(vf, selectBatchDownload(listOf(vostfr, vf), listOf("A"), preferredLanguage = "vf"))
    }
}
