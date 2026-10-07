package dev.endlesssea.downloader

import com.google.common.truth.Truth.assertThat
import dev.endlesssea.downloader.segment.SegmentEngine
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * §tests (downloader) — sonde d'URL : le garde-fou anti-« page HTML de 36 Ko »
 * (spec §6). Une régression ici = l'utilisateur télécharge une page d'erreur
 * au lieu de sa vidéo, ou pire : la file part en boucle sur un lien expiré.
 */
class SegmentEngineProbeTest {

    private lateinit var server: MockWebServer
    private val engine by lazy { SegmentEngine(OkHttpClient()) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `une reponse HEAD correcte est traduite en sonde complete`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Content-Range: bytes 0-0/1234567")
                .addHeader("Accept-Ranges: bytes")
                .addHeader("Content-Type: video/mp4")
                .addHeader("Content-Disposition: attachment; filename=\"episode-03.mp4\"")
                .addHeader("ETag: \"abc123\""),
        )
        val url = server.url("/episode-03.mp4").toString()
        val probe = engine.probe(url)

        assertThat(probe.url).isEqualTo(url)
        assertThat(probe.suggestedName).isEqualTo("episode-03.mp4")
        assertThat(probe.contentLength).isEqualTo(1_234_567L)
        assertThat(probe.acceptRanges).isTrue()
        assertThat(probe.etag).isEqualTo("\"abc123\"")
        assertThat(probe.contentType).isEqualTo("video/mp4")
    }

    @Test
    fun `une page HTML servie a la place de la video est refusee`() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Content-Type: text/html; charset=utf-8"),
        )
        val thrown = runCatching {
            runBlocking { engine.probe(server.url("/page.html").toString()) }
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(SegmentEngine.SourceError::class.java)
        assertThat(thrown!!.message).contains("page")
    }

    @Test
    fun `un contenu textuel detecte aux premiers octets est refuse`() {
        // HEAD refusé par le CDN → repli sur un GET « bytes=0-0 » qui renvoie du HTML.
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Content-Type: application/octet-stream")
                .setBody("<html><body>Access denied</body></html>"),
        )
        val thrown = runCatching {
            runBlocking { engine.probe(server.url("/video.mp4").toString()) }
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(SegmentEngine.SourceError::class.java)
        assertThat(thrown!!.message).contains("texte")
    }

    @Test
    fun `une taille derisoire est refusee avant la file d'attente`() {
        server.enqueue(MockResponse().setResponseCode(405)) // HEAD refusé
        server.enqueue(
            MockResponse().setResponseCode(200)
                .addHeader("Content-Type: video/mp4")
                .setBody("f".repeat(36 * 1024)), // la fameuse page de 36 Ko
        )
        val thrown = runCatching {
            runBlocking { engine.probe(server.url("/video.mp4").toString()) }
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(SegmentEngine.SourceError::class.java)
        assertThat(thrown!!.message).contains("36 Ko")
    }

    @Test
    fun `une erreur HTTP est remontee telle quelle`() {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(403))
        val thrown = runCatching {
            runBlocking { engine.probe(server.url("/video.mp4").toString()) }
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(SegmentEngine.SourceError::class.java)
        assertThat(thrown!!.message).contains("403")
    }
}
