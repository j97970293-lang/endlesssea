package dev.endlesssea.downloader

import dev.endlesssea.downloader.hls.HlsEngine
import dev.endlesssea.downloader.hls.prepareHlsResume
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Reference ciphertexts are produced independently of the downloader's IV calculation. */
class HlsAesDownloadTest {
    private val key = ByteArray(16) { (it * 7).toByte() }

    private fun verify(sequence: Long, explicitIv: String? = null, resume: Boolean = false) = runBlocking {
        val server = MockWebServer()
        server.start()
        val target = File.createTempFile("hls-aes-", ".ts")
        try {
            val plain = listOf(ByteArray(188) { it.toByte() }, ByteArray(32) { (255 - it).toByte() })
            val explicit = explicitIv?.removePrefix("0x")?.removePrefix("0X")?.padStart(32, '0')
                ?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
            val playlist = "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:$sequence\n" +
                "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"" +
                (explicitIv?.let { ",IV=$it" } ?: "") +
                "\n#EXTINF:6,\na.ts\n#EXTINF:6,\nb.ts\n#EXT-X-ENDLIST\n"
            server.enqueue(MockResponse().setBody(playlist))
            server.enqueue(MockResponse().setBody(Buffer().write(key)))
            val start = if (resume) 1 else 0
            if (resume) target.writeBytes(plain[0])
            for (i in start..1) {
                val iv = explicit ?: ByteBuffer.allocate(16).putLong(0L).putLong(sequence + i).array()
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                server.enqueue(MockResponse().setBody(Buffer().write(cipher.doFinal(plain[i]))))
            }
            val engine = HlsEngine(OkHttpClient())
            val plan = engine.resolve(server.url("/media.m3u8").toString())
            val checkpoints = mutableListOf<Int>()
            val bytes = engine.download(plan, emptyMap(), target, start) { checkpoints += it }
            assertArrayEquals(plain[0] + plain[1], target.readBytes())
            assertEquals(220L, bytes)
            assertEquals((start..1).toList(), checkpoints)
        } finally {
            target.delete()
            server.shutdown()
        }
    }

    @Test fun implicitIvStartsAtZero() = verify(0L)
    @Test fun implicitIvStartsAtNonZeroSequence() = verify(7L)
    @Test fun implicitIvPreservesHighSequenceBytes() = verify(0x0102030405060708L)
    @Test fun implicitIvCarriesBetweenBytes() = verify(255L)
    @Test fun resumedSegmentUsesOriginalSequenceAndIndex() = verify(256L, resume = true)
    @Test fun explicitFullIvRemovesPaddingEvenForAlignedPlaintext() =
        verify(9L, "0x000102030405060708090a0b0c0d0e0f")
    @Test fun shortExplicitIvIsLeftPadded() = verify(9L, "0x1")
    @Test fun uppercaseExplicitIvIsAccepted() = verify(9L, "0XABC")

    private fun rejectPlaylist(tag: String) {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("#EXTM3U\n$tag\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST"))
            assertThrows(HlsEngine.HlsError::class.java) {
                runBlocking { HlsEngine(OkHttpClient()).resolve(server.url("/media.m3u8").toString()) }
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test fun explicitIvLargerThan128BitsIsRejectedBeforeDownload() =
        rejectPlaylist("#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\",IV=0x1" + "0".repeat(32))
    @Test fun nonHexadecimalIvIsRejectedBeforeDownload() =
        rejectPlaylist("#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\",IV=0xGG")
    @Test fun negativeSequenceIsRejectedBeforeDownload() = rejectPlaylist("#EXT-X-MEDIA-SEQUENCE:-1")
    @Test fun unsupportedSequenceRangeIsRejectedBeforeDownload() =
        rejectPlaylist("#EXT-X-MEDIA-SEQUENCE:18446744073709551615")

    private fun legacyFingerprint(plan: HlsEngine.Plan): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        add(plan.mediaPlaylistUrl); add(plan.mediaSequence.toString()); add(plan.initUrl.orEmpty())
        add(plan.keyUri.orEmpty()); add(plan.keyIv?.joinToString(",") ?: "")
        plan.segments.forEach { add(it.idx.toString()); add(it.url) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test fun oldEncryptedPartialIsPreservedButCannotResume() {
        val engine = HlsEngine(OkHttpClient())
        val plan = HlsEngine.Plan("https://example.test/media.m3u8",
            listOf(HlsEngine.Segment(0, "https://example.test/a.ts")),
            "https://example.test/key.bin", null, 7, null)
        val part = File.createTempFile("aes-part-", ".ts")
        val marker = File.createTempFile("aes-marker-", ".txt")
        try {
            part.writeBytes(byteArrayOf(1, 2, 3))
            marker.writeText(legacyFingerprint(plan))
            assertNotEquals(marker.readText(), engine.fingerprint(plan))
            assertThrows(java.io.IOException::class.java) {
                prepareHlsResume(part, marker, engine.fingerprint(plan), 3L)
            }
            assertArrayEquals(byteArrayOf(1, 2, 3), part.readBytes())
        } finally {
            part.delete(); marker.delete()
        }
    }

    @Test fun unencryptedResumeFingerprintRemainsCompatible() {
        val plan = HlsEngine.Plan("https://example.test/media.m3u8",
            listOf(HlsEngine.Segment(0, "https://example.test/a.ts")), null, null, 7, null)
        assertEquals(legacyFingerprint(plan), HlsEngine(OkHttpClient()).fingerprint(plan))
    }

}
