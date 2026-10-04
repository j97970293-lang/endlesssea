package dev.endlesssea.downloader.hls

import dev.endlesssea.downloader.segment.SegmentEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Téléchargeur de flux HLS (`#EXTM3U`), style IDM : la playlist est résolue
 * (variante de meilleure qualité choisie automatiquement), chaque segment est
 * téléchargé séquentiellement avec reprise après coupure (checkpoint par index),
 * support du chiffrement AES-128-CBC, puis assemblage dans un fichier unique
 * (`.ts` MPEG-TS, ou `.mp4` si fMP4/`EXT-X-MAP`).
 *
 * DASH (.mpd) n'est pas géré par ce moteur — la tâche échoue alors avec un
 * message explicite côté appelant.
 */
class HlsEngine(private val client: OkHttpClient) {

    data class Segment(val idx: Int, val url: String)
    data class Plan(
        val mediaPlaylistUrl: String,
        val segments: List<Segment>,
        val keyUri: String?,
        val keyIv: ByteArray?,
        val mediaSequence: Long,
        val initUrl: String?,
    ) {
        val isFmp4: Boolean get() = initUrl != null
    }

    class HlsError(message: String) : Exception(message)

    private fun get(url: String, headers: Map<String, String>): okhttp3.Response {
        val req = Request.Builder().url(url)
            .apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        val res = client.newCall(req).execute()
        if (res.code !in 200..299) throw SegmentEngine.SourceError("HTTP ${res.code}")
        return res
    }

    /** Résout une URL HLS en plan : playlist média de meilleure qualité + segments. */
    suspend fun resolve(url: String, headers: Map<String, String> = emptyMap()): Plan =
        withContext(Dispatchers.IO) {
            val body = get(url, headers).use { it.body?.string() ?: throw HlsError("Playlist vide") }
            if (!body.contains("#EXTM3U")) throw HlsError("Ce n'est pas un flux HLS (.m3u8)")

            val mediaUrl = if (body.contains("#EXT-X-STREAM-INF")) {
                // Variante maître : on prend le débit le plus élevé annoncé
                var bestLine: String? = null
                var bestBandwidth = -1L
                val lines = body.lines()
                for (i in lines.indices) {
                    if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                        val bw = Regex("BANDWIDTH=([0-9]+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0
                        val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                        if (next != null && bw >= bestBandwidth) { bestBandwidth = bw; bestLine = next }
                    }
                }
                bestLine?.let { resolveUrl(url, it) }
                    ?: throw HlsError("Playlist maître sans variante exploitable")
            } else url

            val mediaBody = if (mediaUrl == url) body
            else get(mediaUrl, headers).use { it.body?.string() ?: throw HlsError("Playlist média vide") }
            return@withContext parseMediaPlaylist(mediaUrl, mediaBody)
        }

    private fun parseMediaPlaylist(baseUrl: String, body: String): Plan {
        val segments = mutableListOf<Segment>()
        var keyUri: String? = null
        var keyIv: ByteArray? = null
        var mediaSequence = 0L
        var initUrl: String? = null
        var idx = 0
        for (line in body.lines()) {
            val t = line.trim()
            when {
                t.startsWith("#EXT-X-MEDIA-SEQUENCE") ->
                    mediaSequence = t.substringAfter(':').trim().toLongOrNull() ?: 0
                t.startsWith("#EXT-X-KEY") -> {
                    if (t.contains("METHOD=AES-128")) {
                        keyUri = attr(t, "URI")?.let { resolveUrl(baseUrl, it) }
                        keyIv = attr(t, "IV")?.removePrefix("0x")?.removePrefix("0X")?.hexToBytes()
                    } else if (t.startsWith("#EXT-X-KEY:METHOD=SAMPLE-AES")) {
                        throw HlsError("Flux chiffré SAMPLE-AES non supporté")
                    }
                }
                t.startsWith("#EXT-X-MAP") -> {
                    initUrl = attr(t, "URI")?.let { resolveUrl(baseUrl, it) }
                }
                t.isNotBlank() && !t.startsWith("#") ->
                    segments += Segment(idx++, resolveUrl(baseUrl, t))
            }
        }
        if (segments.isEmpty()) throw HlsError("Flux HLS sans segment (lien expiré ?)")
        return Plan(baseUrl, segments, keyUri, keyIv, mediaSequence, initUrl)
    }

    private fun attr(tag: String, name: String): String? {
        val regex = Regex("""$name=("[^"]+"|[^,]+)""")
        return regex.find(tag)?.groupValues?.get(1)?.trim('"')
    }

    private fun resolveUrl(base: String, child: String): String =
        runCatching { URL(URL(base), child).toString() }.getOrDefault(child)

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ivForSegment(ivHex: ByteArray?, mediaSequence: Long, idx: Int): ByteArray =
        ivHex ?: ByteArray(16).apply {
            val seq = mediaSequence + idx
            for (i in 0..15) this[15 - i] = ((seq shr (i * 8)) and 0xFF).toByte()
        }

    /**
     * Télécharge [plan] dans [target] à partir de [fromIdx] (reprise).
     * [onSegment] est appelé après chaque segment écrit (checkpoint par le gestionnaire).
     * Retourne le total d'octets du fichier final.
     */
    suspend fun download(
        plan: Plan,
        headers: Map<String, String>,
        target: File,
        fromIdx: Int,
        onSegment: suspend (idx: Int) -> Unit,
    ): Long = coroutineScope {
        ensureActive()
        val keyBytes: ByteArray? = withContext(Dispatchers.IO) {
            plan.keyUri?.let { get(it, headers).use { r -> r.body?.bytes() } }
        }
        // Premier lancement : écrire l'init fMP4 en tête
        if (fromIdx == 0) {
            target.parentFile?.mkdirs()
            withContext(Dispatchers.IO) {
                if (plan.initUrl != null) {
                    get(plan.initUrl, headers).use { r -> target.writeBytes(r.body?.bytes() ?: ByteArray(0)) }
                } else {
                    target.writeBytes(ByteArray(0))
                }
            }
        }
        plan.segments.filter { it.idx >= fromIdx }.forEach { seg ->
            coroutineContext.ensureActive()
            val bytes = fetchSegment(plan, seg, headers, keyBytes)
            withContext(Dispatchers.IO) {
                target.appendBytes(bytes)
            }
            onSegment(seg.idx)
        }
        withContext(Dispatchers.IO) { target.length() }
    }

    private suspend fun fetchSegment(
        plan: Plan,
        seg: Segment,
        headers: Map<String, String>,
        keyBytes: ByteArray?,
    ): ByteArray = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (attempt in 0..2) {
            try {
                coroutineContext.ensureActive()
                return@withContext get(seg.url, headers).use { r ->
                    var bytes = r.body?.bytes() ?: ByteArray(0)
                    if (keyBytes != null) {
                        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
                        cipher.init(
                            Cipher.DECRYPT_MODE,
                            SecretKeySpec(keyBytes, "AES"),
                            IvParameterSpec(ivForSegment(plan.keyIv, plan.mediaSequence, seg.idx)),
                        )
                        bytes = cipher.doFinal(bytes)
                        // padding d'origine non retiré par NoPadding : toléré par les lecteurs
                    }
                    bytes
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
                kotlinx.coroutines.delay((attempt + 1) * 1_500L)
            }
        }
        throw SegmentEngine.SourceError("Segment ${seg.idx + 1} indisponible : ${last?.message ?: "réseau"}")
    }
}
