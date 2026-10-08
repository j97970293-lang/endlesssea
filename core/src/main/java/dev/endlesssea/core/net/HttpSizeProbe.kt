package dev.endlesssea.core.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** A server declaration, not a guarantee. Never consume a video body just to measure it. */
class HttpSizeProbe(private val client: OkHttpClient) {
    fun contentLength(url: String, headers: Map<String, String> = emptyMap()): Long? {
        fun request() = Request.Builder().url(url).apply {
            headers.forEach { (key, value) -> header(key, value) }
            header("Accept-Encoding", "identity")
            removeHeader("Range")
        }
        // The downloader uses GET: a range response is preferable to a possibly inconsistent HEAD.
        val ranged = runCatching {
            val call = client.newCall(request().header("Range", "bytes=0-0").get().build())
            call.execute().use { response ->
                try { declaredLength(response) to (response.code == 405 || response.code == 501) }
                finally { call.cancel() } // Cancel before closing a server-ignored Range body.
            }
        }.getOrNull()
        if (ranged != null && !ranged.second) return ranged.first
        return runCatching {
            client.newCall(request().head().build()).execute().use { declaredLength(it) }
        }.getOrNull()
    }

    private fun declaredLength(response: Response): Long? {
        if (response.code != 200 && response.code != 206) return null
        val encoding = response.header("Content-Encoding")
        if (encoding != null && !encoding.equals("identity", true)) return null
        val mime = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase(java.util.Locale.ROOT)
        if (mime.startsWith("text/") || mime.contains("mpegurl") || mime.contains("dash+xml") ||
            mime.contains("json") || mime.contains("xml") || mime.startsWith("multipart/")) return null
        if (response.code == 206) {
            // A partial Content-Length is NOT the size of the file.
            val match = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
                .matchEntire(response.header("Content-Range").orEmpty().trim()) ?: return null
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val end = match.groupValues[2].toLongOrNull() ?: return null
            val total = match.groupValues[3].toLongOrNull() ?: return null
            return total.takeIf { start <= end && end < total }
        }
        return response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
    }
}
