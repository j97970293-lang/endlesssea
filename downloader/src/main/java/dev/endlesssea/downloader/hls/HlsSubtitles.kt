package dev.endlesssea.downloader.hls

/** A downloadable HLS subtitle rendition declared by `#EXT-X-MEDIA:TYPE=SUBTITLES`. */
data class HlsSubtitleTrack(
    val url: String,
    val language: String,
    val name: String,
)

/**
 * Reads subtitle media playlists from an HLS master playlist.
 * In-stream `CLOSED-CAPTIONS` have no URI and stay with the video (Media3 extracts them during playback).
 */
fun hlsSubtitleTracks(master: String, masterUrl: String): List<HlsSubtitleTrack> {
    if (!master.contains("#EXT-X-MEDIA:")) return emptyList()
    val attr = Regex("""(?:^|,)\s*([A-Z0-9-]+)=("([^"]*)"|[^,]*)""")
    return master.lineSequence().map { it.trim() }.mapNotNull { line ->
        if (!line.startsWith("#EXT-X-MEDIA:")) return@mapNotNull null
        val values = attr.findAll(line.substringAfter(':')).associate { match ->
            match.groupValues[1] to match.groupValues[3].ifBlank { match.groupValues[2].trim().trim('"') }
        }
        if (!values["TYPE"].equals("SUBTITLES", true)) return@mapNotNull null
        val uri = values["URI"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        HlsSubtitleTrack(
            url = resolveHlsUrl(masterUrl, uri),
            language = values["LANGUAGE"].orEmpty().ifBlank { "und" },
            name = values["NAME"].orEmpty().ifBlank { values["LANGUAGE"].orEmpty().ifBlank { "Sous-titres" } },
        )
    }.distinctBy { it.url }
}

internal fun resolveHlsUrl(base: String, child: String): String =
    if (child.startsWith("http://") || child.startsWith("https://")) child
    else runCatching { java.net.URL(java.net.URL(base), child).toString() }.getOrDefault(child)
