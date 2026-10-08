package dev.endlesssea.app.ui.details

import dev.endlesssea.extensions.api.model.AudioLang
import dev.endlesssea.extensions.api.model.Quality
import dev.endlesssea.extensions.api.model.StreamType
import dev.endlesssea.extensions.api.model.VideoLink

/** Filter before ranking: an embed on server #1 must not hide a file on server #2. */
internal fun selectBatchDownload(
    links: List<VideoLink>, priority: List<String>, excluded: Set<String> = emptySet(),
    language: AudioLang? = null, quality: Quality? = null,
): VideoLink? {
    val candidates = links.filter { link ->
        (link.streamType == StreamType.DIRECT_FILE || link.streamType == StreamType.HLS) &&
            excluded.none { it.equals(link.server, true) } &&
            (language == null || link.audioLang == language) &&
            (quality == null || link.quality == quality)
    }
    return candidates.sortedWith(
        compareBy<VideoLink> { link -> priority.indexOfFirst { it.equals(link.server, true) }.takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .thenByDescending { it.quality.pixels }
            .thenByDescending { it.streamType == StreamType.DIRECT_FILE },
    ).firstOrNull()
}
