package dev.endlesssea.downloader.hls

internal data class HlsVariant(val uri: String, val bandwidth: Long, val height: Int?)

internal fun hlsVariants(body: String): List<HlsVariant> {
    val variants = mutableListOf<HlsVariant>()
    var attributes: Map<String, String>? = null
    val pattern = Regex("""(?:^|,)\s*([A-Z0-9-]+)=("[^"]*"|[^,]*)""")
    for (line in body.lineSequence().map { it.trim() }) {
        if (line.startsWith("#EXT-X-STREAM-INF:")) {
            attributes = pattern.findAll(line.substringAfter(':')).associate { it.groupValues[1] to it.groupValues[2].trim().trim('"') }
        } else if (line.isNotEmpty() && !line.startsWith('#')) {
            attributes?.let { values ->
                val resolution = Regex("\\d+x(\\d+)").matchEntire(values["RESOLUTION"].orEmpty())
                variants += HlsVariant(line, values["BANDWIDTH"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0,
                    resolution?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 })
            }
            attributes = null
        }
    }
    return variants
}

/** Explicit quality must match; never silently replace 720p by a larger 1080p rendition. */
internal fun selectHlsVariant(variants: List<HlsVariant>, requestedHeight: Int): HlsVariant? =
    variants.filter { requestedHeight <= 0 || it.height == requestedHeight }.maxByOrNull { it.bandwidth }
