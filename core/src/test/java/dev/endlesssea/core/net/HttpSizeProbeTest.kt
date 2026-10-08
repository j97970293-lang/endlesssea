package dev.endlesssea.core.net

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class HttpSizeProbeTest {
    private fun probe(vararg responses: MockResponse, check: (Long?, MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try {
            responses.forEach(server::enqueue)
            val result = HttpSizeProbe(OkHttpClient()).contentLength(server.url("/video").toString(), mapOf("X-Test" to "preserved"))
            check(result, server)
        } finally { server.shutdown() }
    }
    @Test fun partialResponseUsesFullRangeTotalNotOneByte() = probe(
        MockResponse().setResponseCode(206).setBody("x").setHeader("Content-Range", "bytes 0-0/629145600"),
    ) { size, server ->
        assertEquals(629145600L, size)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("bytes=0-0", request.getHeader("Range"))
        assertEquals("identity", request.getHeader("Accept-Encoding"))
        assertEquals("preserved", request.getHeader("X-Test"))
        assertEquals(1, server.requestCount)
    }
    @Test fun ignoredRangeUsesFullResponseLengthWithoutReadingVideo() = probe(
        MockResponse().setBody("x").setHeader("Content-Length", "600000000"),
    ) { size, _ -> assertEquals(600000000L, size) }
    @Test fun unsupportedGetFallsBackToHead() = probe(
        MockResponse().setResponseCode(405), MockResponse().setHeader("Content-Length", "12345"),
    ) { size, server ->
        assertEquals(12345L, size)
        server.takeRequest(); assertEquals("HEAD", server.takeRequest().method)
    }
    @Test fun partialResponseWithoutTotalIsUnknown() = probe(
        MockResponse().setResponseCode(206).setBody("x"),
    ) { size, _ -> assertNull(size) }
    @Test fun invalidRangeIsUnknown() = probe(
        MockResponse().setResponseCode(206).setBody("x").setHeader("Content-Range", "bytes 0-9/5"),
    ) { size, _ -> assertNull(size) }
    @Test fun errorsAreNotReportedAsVideoSizes() = probe(
        MockResponse().setResponseCode(403).setBody("Access denied"),
    ) { size, server -> assertNull(size); assertEquals(1, server.requestCount) }
    @Test fun playlistMimeIsNotAVideoSize() = probe(
        MockResponse().setBody("#EXTM3U").setHeader("Content-Type", "application/vnd.apple.mpegurl"),
    ) { size, _ -> assertNull(size) }
    @Test fun htmlAndCompressedBodiesAreNotUsedForAFileTotal() {
        probe(MockResponse().setBody("html").setHeader("Content-Type", "text/html")) { size, _ -> assertNull(size) }
        probe(MockResponse().setBody("gzip").setHeader("Content-Encoding", "gzip")) { size, _ -> assertNull(size) }
    }
}
