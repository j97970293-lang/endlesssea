package dev.endlesssea.downloader.hls

import dev.endlesssea.core.net.HttpSizeProbe
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Uses the exact download resolver/quality, never treats a master playlist as video segments. */
class HlsSizeEstimator(client: OkHttpClient) {
    private val boundedClient = client.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()
    private val engine = HlsEngine(boundedClient)
    private val probe = HttpSizeProbe(boundedClient)
    private val gate = Semaphore(3)

    suspend fun estimate(url: String, headers: Map<String, String>, requestedHeight: Int): HlsSizeEstimate? = try {
        withTimeoutOrNull(20_000) {
            val plan = gate.withPermit { engine.resolve(url, headers, requestedHeight) }
            if (!plan.endList) return@withTimeoutOrNull null // A live window is not the whole video.
            val indices = HlsSizeMath.sampleIndices(plan.segments.size)
            val sizes = coroutineScope {
                indices.map { index -> async(Dispatchers.IO) {
                    gate.withPermit {
                        currentCoroutineContext().ensureActive()
                        index to probe.contentLength(plan.segments[index].url, headers)
                    }
                } }.awaitAll().mapNotNull { (index, bytes) -> bytes?.let { index to it } }.toMap()
            }
            val init = plan.initUrl?.let { url ->
                withContext(Dispatchers.IO) {
                    gate.withPermit { currentCoroutineContext().ensureActive(); probe.contentLength(url, headers) }
                } ?: return@withTimeoutOrNull null
            } ?: 0L
            HlsSizeMath.estimate(plan.segments.map { it.durationSeconds }, sizes, init, plan.endList)
        }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }
}
