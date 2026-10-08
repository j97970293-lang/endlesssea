package dev.endlesssea.downloader

import dev.endlesssea.downloader.hls.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HlsQualityAndResumeTest {
    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=9000000,BANDWIDTH=1500000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2"
        medium/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=4000000,RESOLUTION=1920x1080
        high/index.m3u8
    """.trimIndent()
    private val media = "#EXTM3U\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST"

    @Test fun selected720DoesNotBecome1080() {
        val selected = selectHlsVariant(hlsVariants(master), 720)!!
        assertEquals("medium/index.m3u8", selected.uri)
        assertEquals(1500000L, selected.bandwidth)
    }
    @Test fun automaticChoiceStillUsesHighestActualBandwidth() {
        assertEquals("high/index.m3u8", selectHlsVariant(hlsVariants(master), 0)!!.uri)
    }
    @Test fun missingRequestedQualityDoesNotSilentlyUpgrade() {
        assertNull(selectHlsVariant(hlsVariants(master), 480))
    }
    @Test fun unknownResolutionCannotPretendToBeSelectedQuality() {
        val variants = hlsVariants("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=999999\nunknown.m3u8")
        assertNull(selectHlsVariant(variants, 720))
        assertNotNull(selectHlsVariant(variants, 0))
    }
    @Test fun engineRequestsTheChosenVariantAndResolvesRelativeSegments() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(master)); server.enqueue(MockResponse().setBody(media)); server.start()
            val plan = HlsEngine(OkHttpClient()).resolve(server.url("/master.m3u8").toString(), requestedHeight = 720)
            assertEquals(server.url("/medium/a.ts").toString(), plan.segments.single().url)
            assertEquals("/master.m3u8", server.takeRequest().path)
            assertEquals("/medium/index.m3u8", server.takeRequest().path)
        }
    }
    @Test fun missingQualityFailsBeforeDownloadingAnotherVariant() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(master)); server.start()
            try { HlsEngine(OkHttpClient()).resolve(server.url("/master.m3u8").toString(), requestedHeight = 480); fail("Wrong quality accepted") }
            catch (e: HlsEngine.HlsError) { assertTrue(e.message!!.contains("480p")) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun relativeUrlsUseTheFinalRedirectLocation() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/redirect/index.m3u8")))
            server.enqueue(MockResponse().setBody(media))
            val plan = HlsEngine(OkHttpClient()).resolve(server.url("/old").toString())
            assertEquals(server.url("/redirect/a.ts").toString(), plan.segments.single().url)
        }
    }
    @Test fun byteRangesAreRejectedRatherThanDownloadingTheWholeFileRepeatedly() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("#EXTM3U\n#EXT-X-BYTERANGE:1000@0\nwhole.mp4")); server.start()
            try { HlsEngine(OkHttpClient()).resolve(server.url("/list.m3u8").toString()); fail("Unsupported ranges accepted") }
            catch (e: HlsEngine.HlsError) { assertTrue(e.message!!.contains("plages")) }
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun cancelledAppendStillFinishesItsCheckpoint() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("segment")); server.start()
            val file = Files.createTempFile("hls", ".part").toFile()
            try {
                val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
                var checkpoint = false
                val engine = HlsEngine(OkHttpClient())
                val plan = HlsEngine.Plan("playlist", listOf(HlsEngine.Segment(0, server.url("/seg").toString())), null, null, 0, null)
                withTimeout(5000) {
                    val job = launch { engine.download(plan, emptyMap(), file, 0) { entered.complete(Unit); release.await(); checkpoint = true } }
                    entered.await(); job.cancel(); release.complete(Unit); job.join()
                }
                assertTrue(checkpoint)
                assertEquals("segment", file.readText())
            } finally { file.delete() }
        }
    }
    @Test fun resumedDownloadHasExactlyTheBytesOfAnUninterruptedDownload() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("AAA")); server.enqueue(MockResponse().setBody("BBB")); server.start()
            val dir = Files.createTempDirectory("hls-exact-resume").toFile()
            try {
                val part = File(dir, "video.part"); val marker = File(dir, "identity")
                val engine = HlsEngine(OkHttpClient())
                val plan = HlsEngine.Plan("playlist", listOf(
                    HlsEngine.Segment(0, server.url("/a").toString()),
                    HlsEngine.Segment(1, server.url("/b").toString())), null, null, 0, null)
                val identity = engine.fingerprint(plan)
                prepareHlsResume(part, marker, identity, null)
                var checkpointEnd = 0L
                try {
                    engine.download(plan, emptyMap(), part, 0) {
                        checkpointEnd = part.length()
                        throw java.io.IOException("simulated interruption after checkpoint")
                    }
                    fail("Expected interruption")
                } catch (_: java.io.IOException) { }
                part.appendText("BB") // Simulate a tail written without a completed checkpoint.
                prepareHlsResume(part, marker, identity, checkpointEnd)
                val bytes = engine.download(plan, emptyMap(), part, 1) { }
                assertEquals(6L, bytes)
                assertEquals("AAABBB", part.readText())
                assertEquals("/a", server.takeRequest().path)
                assertEquals("/b", server.takeRequest().path)
            } finally { dir.deleteRecursively() }
        }
    }
    @Test fun uncheckpointedTailIsRemovedBeforeRetry() = withFiles { part, marker ->
        part.writeText("goodduplicate"); marker.writeText("same")
        prepareHlsResume(part, marker, "same", 4)
        assertEquals("good", part.readText())
    }
    @Test fun oldOrChangedPartialIsPreservedAndRefused() = withFiles { part, marker ->
        part.writeText("keep"); marker.writeText("old")
        for ((identity, end) in listOf("new" to 4L, "old" to 0L, "old" to 8L)) {
            try { prepareHlsResume(part, marker, identity, end); fail("Unsafe resume accepted") }
            catch (_: java.io.IOException) { }
            assertEquals("keep", part.readText())
        }
        marker.delete()
        try { prepareHlsResume(part, marker, "same", 4); fail("Legacy checkpoint accepted") }
        catch (_: java.io.IOException) { }
        assertEquals("keep", part.readText())
    }
    @Test fun freshDownloadWritesOnlyTheIdentityMarker() = withFiles { part, marker ->
        part.writeText("not touched yet")
        prepareHlsResume(part, marker, "new", null)
        assertEquals("new", marker.readText())
        assertEquals("not touched yet", part.readText())
    }
    @Test fun fingerprintChangesWhenRenditionChanges() {
        val engine = HlsEngine(OkHttpClient())
        val plan = HlsEngine.Plan("https://host/720.m3u8", listOf(HlsEngine.Segment(0, "https://host/a.ts")), null, null, 0, null)
        assertEquals(engine.fingerprint(plan), engine.fingerprint(plan.copy()))
        assertNotEquals(engine.fingerprint(plan), engine.fingerprint(plan.copy(mediaPlaylistUrl = "https://host/1080.m3u8")))
        assertEquals(64, engine.fingerprint(plan).length)
    }
    private fun withFiles(block: (File, File) -> Unit) {
        val dir = Files.createTempDirectory("hls-resume").toFile()
        try { block(File(dir, "video.part"), File(dir, "identity")) } finally { dir.deleteRecursively() }
    }
}
