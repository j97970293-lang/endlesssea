package dev.endlesssea.downloader.hls

import dev.endlesssea.downloader.segment.SegmentEngine
import kotlinx.coroutines.NonCancellable
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
 * (qualité explicite respectée, meilleure variante seulement en mode automatique), chaque segment est
 * téléchargé séquentiellement avec reprise après coupure (checkpoint par index),
 * support du chiffrement AES-128-CBC, puis assemblage dans un fichier unique
 * (`.ts` MPEG-TS, ou `.mp4` si fMP4/`EXT-X-MAP`).
 *
 * DASH is handled by DashEngine, not by this class.
 */
class HlsEngine(private val client: OkHttpClient) {

    data class Segment(val idx: Int, val url: String, val durationSeconds: Double? = null)
    data class Plan(
        val mediaPlaylistUrl: String,
        val segments: List<Segment>,
        val keyUri: String?,
        val keyIv: ByteArray?,
        val mediaSequence: Long,
        val initUrl: String?,
        val endList: Boolean = false,
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

    /** Subtitle playlists from a master manifest. A media playlist returns none. */
    suspend fun listSubtitles(url: String, headers: Map<String, String> = emptyMap()): List<HlsSubtitleTrack> =
        withContext(Dispatchers.IO) {
            get(url, headers).use { response ->
                val body = response.body?.string()?.take(512 * 1024).orEmpty()
                if (!body.contains("#EXT-X-STREAM-INF:")) return@withContext emptyList()
                hlsSubtitleTracks(body, response.request.url.toString())
            }
        }

    /** Resolve the requested rendition; UNKNOWN retains automatic highest-bandwidth selection. */
    suspend fun resolve(url: String, headers: Map<String, String> = emptyMap(), requestedHeight: Int = 0): Plan =
        withContext(Dispatchers.IO) {
            var current = url
            val visited = mutableSetOf<String>()
            repeat(4) {
                coroutineContext.ensureActive()
                val (base, body) = get(current, headers).use { response ->
                    val input = response.body?.byteStream() ?: throw HlsError("Playlist vide")
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    input.use { stream ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 2 * 1024 * 1024) throw HlsError("Playlist HLS trop volumineuse")
                            output.write(buffer, 0, count)
                        }
                    }
                    response.request.url.toString() to output.toString("UTF-8").removePrefix("\uFEFF").trim()
                }
                if (!visited.add(base)) throw HlsError("Boucle dans les playlists HLS")
                if (!body.startsWith("#EXTM3U")) throw HlsError("Ce n'est pas un flux HLS (.m3u8)")
                if (!body.contains("#EXT-X-STREAM-INF:")) return@withContext parseMediaPlaylist(base, body)
                val variant = selectHlsVariant(hlsVariants(body), requestedHeight)
                    ?: throw HlsError(if (requestedHeight > 0)
                        "Aucune variante ${requestedHeight}p identifiable dans ce manifeste. Choisis une autre qualité."
                        else "Playlist maître sans variante exploitable")
                current = resolveUrl(base, variant.uri)
            }
            throw HlsError("Trop de playlists HLS imbriquées")
        }

    /** Conservative identity: changed signed URLs also require a fresh download. No tokens are stored in the marker. */
    fun fingerprint(plan: Plan): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        add(plan.mediaPlaylistUrl); add(plan.mediaSequence.toString()); add(plan.initUrl.orEmpty())
        add(plan.keyUri.orEmpty()); add(plan.keyIv?.joinToString(",") ?: "")
        if (plan.keyUri != null) add("aes128-pkcs7-v2") // Old decrypted prefixes may already be corrupt.
        plan.segments.forEach { add(it.idx.toString()); add(it.url) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parseMediaPlaylist(baseUrl: String, body: String): Plan {
        val segments = mutableListOf<Segment>()
        var keyUri: String? = null
        var keyIv: ByteArray? = null
        var mediaSequence = 0L
        var initUrl: String? = null
        var idx = 0
        var duration: Double? = null
        var endList = false
        for (line in body.lines()) {
            val t = line.trim()
            when {
                t == "#EXT-X-ENDLIST" -> endList = true
                t.startsWith("#EXTINF:") -> duration = t.substringAfter(':').substringBefore(',').trim()
                    .toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                t.startsWith("#EXT-X-MEDIA-SEQUENCE") ->
                    mediaSequence = t.substringAfter(':').trim().toLongOrNull()?.takeIf { it >= 0 }
                        ?: throw HlsError("Séquence HLS invalide ou hors plage prise en charge")
                t.startsWith("#EXT-X-KEY") -> {
                    if (t.contains("METHOD=AES-128")) {
                        keyUri = attr(t, "URI")?.let { resolveUrl(baseUrl, it) }
                        keyIv = attr(t, "IV")?.removePrefix("0x")?.removePrefix("0X")?.hexToBytes()
                    } else if (t.startsWith("#EXT-X-KEY:METHOD=SAMPLE-AES")) {
                        throw HlsError("Flux chiffré SAMPLE-AES non supporté")
                    }
                }
                t.startsWith("#EXT-X-BYTERANGE") ->
                    throw HlsError("HLS à plages d'octets non pris en charge : téléchargement refusé pour éviter de recopier le fichier entier par segment")
                t.startsWith("#EXT-X-MAP") -> {
                    if (attr(t, "BYTERANGE") != null) throw HlsError("Initialisation HLS à plage d'octets non prise en charge")
                    initUrl = attr(t, "URI")?.let { resolveUrl(baseUrl, it) }
                }
                t.isNotBlank() && !t.startsWith("#") -> {
                    segments += Segment(idx++, resolveUrl(baseUrl, t), duration)
                    duration = null
                }
            }
        }
        if (segments.isEmpty()) throw HlsError("Flux HLS sans segment (lien expiré ?)")
        return Plan(baseUrl, segments, keyUri, keyIv, mediaSequence, initUrl, endList)
    }

    private fun attr(tag: String, name: String): String? {
        val regex = Regex("""$name=("[^"]+"|[^,]+)""")
        return regex.find(tag)?.groupValues?.get(1)?.trim('"')
    }

    private fun resolveUrl(base: String, child: String): String =
        runCatching { URL(URL(base), child).toString() }.getOrDefault(child)

    private fun String.hexToBytes(): ByteArray {
        if (length !in 1..32 || any { it !in "0123456789abcdefABCDEF" }) {
            throw HlsError("IV AES-128 invalide : entier hexadécimal de 128 bits maximum attendu")
        }
        return padStart(32, '0').chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ivForSegment(ivHex: ByteArray?, mediaSequence: Long, idx: Int): ByteArray =
        ivHex ?: ByteArray(16).apply {
            if (mediaSequence < 0 || idx < 0 || mediaSequence > Long.MAX_VALUE - idx) {
                throw HlsError("Séquence HLS hors plage prise en charge")
            }
            val seq = mediaSequence + idx
            // JVM Long shifts wrap modulo 64: writing 16 bytes would repeat the sequence twice.
            // HLS requires the sequence in big-endian order, with the upper eight bytes zero.
            for (i in 0..7) this[15 - i] = ((seq ushr (i * 8)) and 0xFF).toByte()
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
            // Cancellation must not split append from its durable checkpoint.
            withContext(NonCancellable + Dispatchers.IO) {
                target.appendBytes(bytes)
                onSegment(seg.idx)
            }
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
                        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                        cipher.init(
                            Cipher.DECRYPT_MODE,
                            SecretKeySpec(keyBytes, "AES"),
                            IvParameterSpec(ivForSegment(plan.keyIv, plan.mediaSequence, seg.idx)),
                        )
                        bytes = cipher.doFinal(bytes)
                        // JCA PKCS5Padding for AES implements the PKCS7 padding required by HLS.
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
