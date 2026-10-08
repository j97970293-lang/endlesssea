package dev.endlesssea.core.download

/** Same classification for the download engine and the pre-download size display. */
enum class DownloadSourceKind { DIRECT, HLS, DASH, UNSUPPORTED }

fun downloadSourceKind(declared: String, url: String): DownloadSourceKind {
    val path = url.substringBefore('#').substringBefore('?').lowercase(java.util.Locale.ROOT)
    return when {
        declared == "DASH" || path.endsWith(".mpd") -> DownloadSourceKind.DASH
        declared == "HLS" || path.endsWith(".m3u8") -> DownloadSourceKind.HLS
        declared == "DIRECT_FILE" -> DownloadSourceKind.DIRECT
        else -> DownloadSourceKind.UNSUPPORTED
    }
}
