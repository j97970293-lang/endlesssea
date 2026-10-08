package dev.endlesssea.downloader

import dev.endlesssea.downloader.hls.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class HlsSizeEstimateTest {
    @Test fun samplesSpanTheWholeVideoRatherThanJustTheIntro() {
        val indices = HlsSizeMath.sampleIndices(100)
        assertEquals(12, indices.size)
        assertEquals(0, indices.first()); assertEquals(99, indices.last())
        assertEquals(indices.size, indices.distinct().size)
        assertTrue(indices.all { it in 0..99 })
    }
    @Test fun shortPlaylistsMeasureEverySegmentAndIncludeInitialization() {
        val e = HlsSizeMath.estimate(listOf(2.0, 5.0), mapOf(0 to 100L, 1 to 400L), 50, true)!!
        assertTrue(e.measuredAll); assertEquals(550L, e.bytes)
        assertEquals(e.bytes, e.indicativeLowBytes); assertEquals(e.bytes, e.indicativeHighBytes)
    }
    @Test fun longVideoWithTinyIntroDoesNotRepeatTheOldHalfSizeEstimate() {
        val sizes = (0 until 120).associateWith { if (it < 3) 100L else 200L }
        val sampled = HlsSizeMath.sampleIndices(120).associateWith { sizes.getValue(it) }
        val e = HlsSizeMath.estimate(List(120) { 5.0 }, sampled, 0, true)!!
        val actual = sizes.values.sum()
        assertFalse(e.measuredAll)
        assertTrue(e.bytes > actual * 0.9)
        assertTrue(e.bytes < actual * 1.1)
        assertTrue(e.indicativeLowBytes < e.bytes)
        assertTrue(e.indicativeHighBytes >= actual)
    }
    @Test fun differingSegmentDurationsAreWeightedBySecondsNotSegmentCount() {
        val seconds = List(60) { if (it % 2 == 0) 2.0 else 10.0 }
        val samples = HlsSizeMath.sampleIndices(60).associateWith { (seconds[it] * 100).toLong() }
        val e = HlsSizeMath.estimate(seconds, samples, 10, true)!!
        assertEquals((seconds.sum() * 100).toLong() + 10, e.bytes)
    }
    @Test fun liveWindowCannotBeEstimatedAsTheEntireVideo() {
        assertNull(HlsSizeMath.estimate(listOf(2.0), mapOf(0 to 100L), 0, false))
    }
    @Test fun missingSamplesDoNotBiasTheEstimateTowardAccessibleSegments() {
        val samples = HlsSizeMath.sampleIndices(100).associateWith { 100L }.toMutableMap()
        samples.remove(99)
        assertNull(HlsSizeMath.estimate(List(100) { 5.0 }, samples, 0, true))
    }
    @Test fun missingOrInvalidDurationsRejectExtrapolation() {
        val samples = HlsSizeMath.sampleIndices(60).associateWith { 100L }
        for (bad in listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val durations = MutableList<Double?>(60) { 2.0 }; durations[5] = bad
            assertNull(HlsSizeMath.estimate(durations, samples, 0, true))
        }
    }
    @Test fun fullMeasurementDoesNotNeedDurations() {
        assertEquals(100L, HlsSizeMath.estimate(listOf(null), mapOf(0 to 100L), 0, true)!!.bytes)
    }
    @Test fun overflowAndInvalidSizesAreRejected() {
        assertNull(HlsSizeMath.estimate(listOf(1.0, 1.0), mapOf(0 to Long.MAX_VALUE, 1 to 1L), 0, true))
        assertNull(HlsSizeMath.estimate(listOf(1.0), mapOf(0 to -1L), 0, true))
        assertNull(HlsSizeMath.estimate(emptyList(), emptyMap(), 0, true))
        assertNull(HlsSizeMath.estimate(listOf(1.0), mapOf(0 to 100L), -1, true))
    }
    @Test fun allSampleCountsRemainValid() {
        for (count in 1..200) {
            val samples = HlsSizeMath.sampleIndices(count)
            assertEquals(minOf(count, 12), samples.size)
            assertEquals(samples.sorted().distinct(), samples)
            assertEquals(0, samples.first()); assertEquals(count - 1, samples.last())
        }
    }
    @Test fun networkEstimatorUsesSelected720AndSumsSegmentTotalsNotManifestBytes() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/master" -> MockResponse().setBody("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=1280x720\n720.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=9000,RESOLUTION=1920x1080\n1080.m3u8")
                    "/720.m3u8" -> MockResponse().setBody("#EXTM3U\n#EXT-X-MAP:URI=\"init\"\n#EXTINF:2,\na.ts\n#EXTINF:4,\nb.ts\n#EXT-X-ENDLIST")
                    "/a.ts" -> length(100)
                    "/b.ts" -> length(200)
                    "/init" -> length(50)
                    else -> MockResponse().setResponseCode(404)
                }
                private fun length(bytes: Long) = MockResponse().setResponseCode(206).setBody("x").setHeader("Content-Range", "bytes 0-0/$bytes")
            }
            server.start()
            val e = HlsSizeEstimator(OkHttpClient()).estimate(server.url("/master").toString(), mapOf("X-Test" to "preserved"), 720)!!
            assertTrue(e.measuredAll); assertEquals(350L, e.bytes)
            val requests = (0 until server.requestCount).map { server.takeRequest() }
            assertFalse(requests.any { it.path == "/1080.m3u8" })
            assertTrue(requests.all { it.getHeader("X-Test") == "preserved" })
            assertEquals(setOf("/master", "/720.m3u8", "/init", "/a.ts", "/b.ts"), requests.map { it.path }.toSet())
        }
    }
    @Test fun livePlaylistDoesNotTriggerSegmentProbes() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:6,\na.ts")); server.start()
            val result = HlsSizeEstimator(OkHttpClient()).estimate(server.url("/live").toString(), emptyMap(), 0)
            assertNull(result); assertEquals(1, server.requestCount)
        }
    }
}
