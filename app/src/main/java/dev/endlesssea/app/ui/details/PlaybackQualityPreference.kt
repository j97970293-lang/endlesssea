package dev.endlesssea.app.ui.details

import dev.endlesssea.extensions.api.model.Quality
import dev.endlesssea.extensions.api.model.VideoLink

/**
 * Sort key for a target resolution. A user-selected target prefers an exact
 * match, then the nearest lower resolution, and only then a higher resolution.
 * "auto" picks the highest known resolution. Unknown qualities are last.
 */
internal fun playbackQualityRank(quality: Quality, preference: String): Int {
    if (quality.pixels <= 0) return Int.MAX_VALUE
    val target = preference.toIntOrNull() ?: return -quality.pixels
    return if (quality.pixels <= target) {
        target - quality.pixels
    } else {
        1_000_000 + quality.pixels - target
    }
}

internal fun preferredPlaybackLink(links: List<VideoLink>, preference: String): VideoLink? =
    links.minWithOrNull(
        compareBy<VideoLink> { playbackQualityRank(it.quality, preference) }
            .thenByDescending { it.quality.pixels },
    )
