package dev.endlesssea.app.local

/** An offline copy replaces a source only for the exact episode identity and while readable. */
internal fun <T> readableDownload(
    candidates: List<T>, episodeId: String,
    id: (T) -> String?, uri: (T) -> String, readable: (String) -> Boolean,
): T? = candidates.firstOrNull { id(it) == episodeId && readable(uri(it)) }
