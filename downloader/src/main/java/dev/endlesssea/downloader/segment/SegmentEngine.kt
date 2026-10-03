package dev.endlesssea.downloader.segment

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-connection HTTP download engine (docs/en/06). Blueprint credit: AB Download Manager
 * (Apache-2.0) — probe → plan → Range workers → positioned writes into ONE .part file.
 *
 * Pure Kotlin + OkHttp: fully unit-testable with MockWebServer (docs/en/12).
 * Pause = cancelling the calling coroutine. Resume = caller passes back the persisted
 * segment table; workers re-request only their missing tail.
 */
class SegmentEngine(private val client: OkHttpClient) {

    // ---------------------------------------------------------------- probe

    data class Probe(
        val url: String,
        val suggestedName: String?,
        val contentLength: Long,      // -1 = unknown/sizeless
        val acceptRanges: Boolean,
        val etag: String?,
    )

    suspend fun probe(url: String, headers: Map<String, String> = emptyMap()): Probe =
        withContext(Dispatchers.IO) {
            // 1) HEAD first…
            try {
                client.newCall(request(url, "HEAD", headers)).execute().use { res ->
                    if (res.isSuccessful) return@withContext toProbe(url, res::header)
                }
            } catch (_: IOException) { /* fall through */ }
            // 2) …fallback: ranged GET (some CDNs reject HEAD)
            client.newCall(request(url, "GET", headers + ("Range" to "bytes=0-0"))).execute()
                .use { res ->
                    if (res.code !in 200..299) throw SourceError("HTTP ${res.code}")
                    toProbe(res.request.url.toString(), res::header)
                }
        }

    private fun toProbe(url: String, header: (String) -> String?): Probe {
        val range = header("Content-Range") // "bytes 0-0/1234567"
        val total = range?.substringAfter('/')?.toLongOrNull()
            ?: header("Content-Length")?.toLongOrNull() ?: -1L
        val ranges = total != -1L && total > 0 && (range != null || header("Accept-Ranges") == "bytes")
        val name = header("Content-Disposition")
            ?.let { Regex("filename=\"?([^\";]+)").find(it)?.groupValues?.get(1) }
        return Probe(url, name, total, ranges, header("ETag"))
    }

    class SourceError(msg: String) : IOException(msg)
    class IntegrityError(expected: Long, actual: Long) :
        IOException("size mismatch: expected $expected, got $actual")

    // ---------------------------------------------------------------- plan

    data class Segment(val idx: Int, val start: Long, val end: Long, var downloaded: Long = 0) {
        val total: Long get() = end - start + 1
        val done: Boolean get() = downloaded >= total
        val remaining: Long get() = total - downloaded
    }

    /** IDM-style adaptive plan: 1 segment below 8 MiB / without ranges, else 4–8 (mobile-bounded). */
    fun plan(contentLength: Long, acceptRanges: Boolean, maxParts: Int = 0): List<Segment> {
        if (contentLength <= 0) return emptyList()
        val parts = when {
            !acceptRanges || contentLength < MIN_SPLIT_BYTES -> 1
            else -> (contentLength / PART_TARGET_BYTES).toInt()
                .coerceIn(2, if (maxParts > 0) maxParts else DEFAULT_MAX_PARTS)
        }
        val size = contentLength / parts
        return (0 until parts).map { i ->
            Segment(i, i * size, if (i == parts - 1) contentLength - 1 else (i + 1) * size - 1)
        }
    }

    // ---------------------------------------------------------------- run

    /**
     * Downloads [segments] into [target] (.part file), writing each worker at its own
     * absolute offset (no merge step). [onCheckpoint] is throttled per segment; it MUST be
     * cheap (engine calls it under IO). Throws on unrecoverable errors; callers persist
     * the segment table + map failures (doc 06 §3).
     */
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        segments: List<Segment>,
        target: java.io.File,
        onCheckpoint: (Segment) -> Unit,
    ) = coroutineScope {
        RandomAccessFile(target, "rw").use { raf ->
            raf.setLength(segments.last().end + 1) // preallocate
            val channel: FileChannel = raf.channel
            segments.filter { !it.done }.map { seg ->
                async(Dispatchers.IO) { worker(url, headers, seg, channel, onCheckpoint) }
            }.forEach { job ->
                if (job.await().downloaded <= 0 && segments.sumOf { it.downloaded } <= 0) {
                    // server lied about ranges → whole-task single-connection fallback happens
                    // one level up (DownloadManager); here we just stop early
                    throw SourceError("no bytes received (ranges denied?)")
                }
            }
        }
        verify(segments, target.length())
    }

    private tailrec suspend fun worker(
        url: String,
        headers: Map<String, String>,
        seg: Segment,
        channel: FileChannel,
        onCheckpoint: (Segment) -> Unit,
        attempt: Int = 0,
    ): Segment {
        currentCoroutineContext().ensureActive()
        val from = seg.start + seg.downloaded
        try {
            client.newCall(request(url, "GET", headers + ("Range" to "bytes=$from-${seg.end}")))
                .execute().use { res ->
                    if (res.code == 416 && seg.done) return seg     // already complete
                    if (res.code == 429 || res.code == 503) throw RateLimit(res.header("Retry-After")?.toIntOrNull())
                    if (res.code !in 200..299) throw SourceError("HTTP ${res.code}")
                    val body = res.body ?: throw SourceError("HTTP ${res.code} : corps de réponse vide")
                    body.byteStream().use { input ->
                        val buf = ByteArray(32 * 1024)
                        val wrapped = ByteBuffer.wrap(buf)
                        var lastFlush = System.currentTimeMillis()
                        var absolute = from
                        while (seg.downloaded < seg.total) {
                            currentCoroutineContext().ensureActive()
                            val want = min(buf.size.toLong(), seg.remaining).toInt()
                            val read = input.read(buf, 0, want)
                            if (read < 0) break
                            wrapped.clear(); wrapped.limit(read)
                            while (wrapped.hasRemaining()) {
                                absolute += channel.write(wrapped, absolute)
                            }
                            seg.downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastFlush >= CHECKPOINT_MS) {
                                lastFlush = now
                                onCheckpoint(seg)
                            }
                        }
                        onCheckpoint(seg) // final per-segment checkpoint
                    }
                }
            if (!seg.done) throw IOException("segment ${seg.idx} ended short")
            return seg
        } catch (e: CancellationException) {
            throw e                                     // pause = cancel, byte-exact resume
        } catch (e: RateLimit) {
            if (attempt >= MAX_RETRIES) throw SourceError("rate limited")
            backoff((e.retryAfterSec ?: DEFAULT_BACKOFF[attempt]).toLong() * 1000)
        } catch (e: IOException) {
            if (attempt >= MAX_RETRIES) throw e
            backoff(DEFAULT_BACKOFF[attempt].toLong() * 1000)
        }
        return worker(url, headers, seg, channel, onCheckpoint, attempt + 1)
    }

    private fun verify(segments: List<Segment>, fileLength: Long) {
        val expected = segments.last().end + 1
        val downloaded = segments.sumOf { it.downloaded }
        if (fileLength != expected || downloaded != expected) throw IntegrityError(expected, downloaded)
    }

    private suspend fun backoff(ms: Long) = kotlinx.coroutines.delay(ms)

    private class RateLimit(val retryAfterSec: Int?) : IOException("rate limited")

    private fun request(url: String, method: String, headers: Map<String, String>): Request =
        Request.Builder().url(url).method(method, null)
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()

    companion object {
        const val MIN_SPLIT_BYTES = 8L * 1024 * 1024          // < 8 MiB → single connection
        const val PART_TARGET_BYTES = 8L * 1024 * 1024        // ~8 MiB per part target
        const val DEFAULT_MAX_PARTS = 8                       // mobile-bounded (doc 06 §4)
        const val CHECKPOINT_MS = 1000L
        const val MAX_RETRIES = 3
        val DEFAULT_BACKOFF = intArrayOf(30, 120, 600)        // retries: 30s → 2min → 10min
    }
}
