package dev.endlesssea.downloader.dash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** Downloads a static DASH manifest into one video file, plus optional audio and text sidecars. */
class DashEngine(private val client: OkHttpClient) {

    suspend fun resolve(url: String, headers: Map<String, String> = emptyMap(), requestedHeight: Int = 0): DashPlan =
        withContext(Dispatchers.IO) {
            get(url, headers).use { response ->
                val body = response.body?.byteStream()?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > 4 * 1024 * 1024) throw DashError("Manifeste DASH trop volumineux")
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                } ?: throw DashError("Manifeste DASH vide")
                dashPlan(body, response.request.url.toString(), requestedHeight)
            }
        }

    /**
     * Appends [track] to [target], starting at segment [fromIdx].
     * Bytes are streamed to disk so a long episode does not sit in RAM.
     */
    suspend fun download(
        track: DashTrack,
        headers: Map<String, String>,
        target: File,
        fromIdx: Int,
        onSegment: suspend (Int) -> Unit,
    ): Long {
        target.parentFile?.mkdirs()
        if (fromIdx == 0 && target.exists()) target.delete()
        if (fromIdx == 0 && track.initUrl != null) {
            withContext(Dispatchers.IO) { appendUrl(track.initUrl, headers, target) }
        }
        track.segments.filter { it.idx >= fromIdx }.forEach { segment ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            withContext(NonCancellable + Dispatchers.IO) {
                appendUrl(segment.url, headers, target)
                onSegment(segment.idx)
            }
        }
        return target.length()
    }

    private fun appendUrl(url: String, headers: Map<String, String>, target: File) {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                get(url, headers).use { response ->
                    val body = response.body ?: throw DashError("Segment vide")
                    FileOutputStream(target, true).use { out -> body.byteStream().use { it.copyTo(out) } }
                }
                return
            } catch (e: DashError) {
                last = e
            } catch (e: Exception) {
                last = e
            }
            if (attempt < 2) Thread.sleep((attempt + 1) * 700L)
        }
        throw DashError("Segment indisponible : ${last?.message ?: "réseau"}")
    }

    private fun get(url: String, headers: Map<String, String>): okhttp3.Response {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        val response = client.newCall(request).execute()
        if (response.code !in 200..299) {
            response.close()
            throw DashError("HTTP ${response.code}")
        }
        return response
    }
}
