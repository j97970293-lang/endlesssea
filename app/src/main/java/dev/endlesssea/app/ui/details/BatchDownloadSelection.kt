package dev.endlesssea.app.ui.details

import dev.endlesssea.extensions.api.model.AudioLang
import dev.endlesssea.extensions.api.model.Quality
import dev.endlesssea.extensions.api.model.StreamType
import dev.endlesssea.extensions.api.model.VideoLink

/** Filter before ranking: an embed on server #1 must not hide a file on server #2. */
internal fun selectBatchDownload(
    links: List<VideoLink>, priority: List<String>, excluded: Set<String> = emptySet(),
    language: AudioLang? = null, quality: Quality? = null,
    preferredQuality: String = "auto", preferredLanguage: String = "auto",
): VideoLink? {
    fun pool(strict: Boolean) = links.filter { link ->
        (link.streamType == StreamType.DIRECT_FILE || link.streamType == StreamType.HLS || link.streamType == StreamType.DASH) &&
            excluded.none { it.equals(link.server, true) } &&
            (!strict || language == null || link.audioLang == language) &&
            (!strict || quality == null || link.quality == quality)
    }
    val candidates = pool(true).ifEmpty { pool(false) }
    return candidates.sortedWith(
        compareBy<VideoLink> { link -> priority.indexOfFirst { it.equals(link.server, true) }.takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .thenBy { if (preferredLanguage == "auto" || it.audioLang.iso == preferredLanguage) 0 else 1 }
            .thenBy { if (language != null && it.audioLang == language) 0 else defaultLanguageRank(it.audioLang) }
            .thenBy { if (quality != null && it.quality == quality) 0 else 1 }
            .thenBy { playbackQualityRank(it.quality, preferredQuality) }
            .thenByDescending { it.quality.pixels }
            .thenByDescending { it.streamType == StreamType.DIRECT_FILE },
    ).firstOrNull()
}

/** Ordre utilisé quand l'extension ne classe pas les langues. */
internal fun defaultLanguageRank(lang: AudioLang) = when (lang) {
    AudioLang.VF -> 0
    AudioLang.VOSTFR -> 1
    AudioLang.MULTI -> 2
    AudioLang.VO -> 3
    AudioLang.OTHER -> 4
}
