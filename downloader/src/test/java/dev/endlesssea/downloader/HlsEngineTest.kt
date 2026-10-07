package dev.endlesssea.downloader

import com.google.common.truth.Truth.assertThat
import dev.endlesssea.downloader.hls.HlsEngine
import dev.endlesssea.downloader.segment.SegmentEngine
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * §tests (downloader) — lecture des playlists HLS : variante la mieux servie,
 * segmentation, clé AES-128 et init fMP4. Le test du mauvais choix de variante
 * est ici parce qu'un « #EXT-X-STREAM-INF » mal lu = épisodes en 240p.
 */
class HlsEngineTest {

    private lateinit var server: MockWebServer
    private val engine by lazy { HlsEngine(OkHttpClient()) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun queue(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    @Test
    fun `une playlist maitre choisit la meilleure variante et resout les URL relatives`() = runBlocking {
        queue(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=480x270
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720
            high/index.m3u8
            """.trimIndent(),
        )
        queue(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:7
            #EXTINF:9.5,
            seg0.ts
            #EXTINF:9.5,
            seg1.ts
            #EXTINF:9.5,
            seg2.ts
            """.trimIndent(),
        )

        val plan = engine.resolve(server.url("/master.m3u8").toString())

        assertThat(plan.mediaPlaylistUrl).isEqualTo(server.url("/high/index.m3u8").toString())
        assertThat(plan.mediaSequence).isEqualTo(7L)
        assertThat(plan.isFmp4).isFalse()
        assertThat(plan.keyUri).isNull()
        assertThat(plan.segments.map { it.idx }).containsExactly(0, 1, 2).inOrder()
        assertThat(plan.segments.map { it.url }).containsExactly(
            server.url("/high/seg0.ts").toString(),
            server.url("/high/seg1.ts").toString(),
            server.url("/high/seg2.ts").toString(),
        ).inOrder()
    }

    @Test
    fun `une cle AES-128 est resolue avec son IV`() = runBlocking {
        queue(
            """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x000102030405060708090A0B0C0D0E0F
            #EXTINF:6.0,
            a.ts
            #EXTINF:6.0,
            b.ts
            """.trimIndent(),
        )

        val plan = engine.resolve(server.url("/crypt/index.m3u8").toString())

        assertThat(plan.keyUri).isEqualTo(server.url("/crypt/key.bin").toString())
        assertThat(plan.keyIv).isEqualTo(ByteArray(16) { it.toByte() })
        assertThat(plan.segments).hasSize(2)
    }

    @Test
    fun `un flux fMP4 expose son segment d'initialisation`() = runBlocking {
        queue(
            """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4.0,
            a.m4s
            """.trimIndent(),
        )

        val plan = engine.resolve(server.url("/dash/index.m3u8").toString())

        assertThat(plan.isFmp4).isTrue()
        assertThat(plan.initUrl).isEqualTo(server.url("/dash/init.mp4").toString())
    }

    @Test
    fun `un flux SAMPLE-AES est refuse explicitement`() {
        queue(
            """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="key.bin"
            #EXTINF:6.0,
            a.ts
            """.trimIndent(),
        )
        val thrown = runCatching {
            runBlocking { engine.resolve(server.url("/widevine/index.m3u8").toString()) }
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(HlsEngine.HlsError::class.java)
        assertThat(thrown!!.message).contains("SAMPLE-AES")
    }

    @Test
    fun `un corps qui n'est pas une playlist est refuse`() {
        queue("Bonjour, ceci n'est pas du HLS")
        val thrown = runCatching {
            runBlocking { engine.resolve(server.url("/index.m3u8").toString()) }
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(HlsEngine.HlsError::class.java)
        assertThat(thrown!!.message).contains("HLS")
    }

    @Test
    fun `une playlist sans segment est refusee`() {
        queue("#EXTM3U\n#EXT-X-TARGETDURATION:10\n")
        val thrown = runCatching {
            runBlocking { engine.resolve(server.url("/index.m3u8").toString()) }
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(HlsEngine.HlsError::class.java)
        assertThat(thrown!!.message).contains("segment")
    }

    @Test
    fun `une erreur HTTP est remontee comme SourceError`() {
        queue("", code = 404)
        val thrown = runCatching {
            runBlocking { engine.resolve(server.url("/index.m3u8").toString()) }
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(SegmentEngine.SourceError::class.java)
        assertThat(thrown!!.message).contains("404")
    }
}
