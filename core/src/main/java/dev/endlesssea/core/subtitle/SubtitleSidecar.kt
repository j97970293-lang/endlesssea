package dev.endlesssea.core.subtitle

/** Subtitle or extra audio file stored next to a download. No secrets, only public URLs or local URIs. */
data class SidecarTrack(
    val url: String,
    val lang: String = "",
    val label: String = "",
    val format: String = "UNKNOWN",
)

fun encodeSidecars(tracks: List<SidecarTrack>): String {
    if (tracks.isEmpty()) return "[]"
    return tracks.joinToString(",", "[", "]") { track ->
        """{"url":"${esc(track.url)}","lang":"${esc(track.lang)}","label":"${esc(track.label)}","format":"${esc(track.format)}"}"""
    }
}

fun decodeSidecars(json: String): List<SidecarTrack> {
    val body = json.trim().removePrefix("[").removeSuffix("]")
    if (body.isBlank()) return emptyList()
    return Regex("""\{[^{}]*}""").findAll(body).mapNotNull { match ->
        val obj = match.value
        val url = field(obj, "url") ?: return@mapNotNull null
        SidecarTrack(
            url = url,
            lang = field(obj, "lang").orEmpty(),
            label = field(obj, "label").orEmpty(),
            format = field(obj, "format") ?: "UNKNOWN",
        )
    }.toList()
}

fun sidecarFileName(videoName: String, role: String, extension: String): String {
    val stem = videoName.removeSuffix(".part").substringBeforeLast('.').ifBlank { "video" }
    val safeRole = role.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-').ifBlank { "sub" }
    val ext = extension.lowercase().removePrefix(".").ifBlank { "vtt" }
    return "$stem.$safeRole.$ext".take(140)
}

private fun field(obj: String, name: String): String? =
    Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""").find(obj)?.groupValues?.get(1)?.let(::unesc)

private fun esc(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")

private fun unesc(value: String) = value.replace("\\\"", "\"").replace("\\\\", "\\")
