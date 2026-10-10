package dev.endlesssea.app.ui.details

import dev.endlesssea.app.di.MediaPlaybackPreference
import dev.endlesssea.extensions.api.model.StreamType
import dev.endlesssea.extensions.api.model.VideoLink

/** A saved per-media choice is active only when its complete saved combination exists. */
internal fun availableMediaPlaybackPreference(
    links: List<VideoLink>,
    preference: MediaPlaybackPreference?,
): MediaPlaybackPreference? {
    if (preference == null || preference.server.isBlank() || preference.audioLanguage.isBlank()) return null
    return preference.takeIf { saved ->
        links.any { link ->
            link.server.equals(saved.server, ignoreCase = true) &&
                link.audioLang.iso.equals(saved.audioLanguage, ignoreCase = true) &&
                (saved.qualityPixels == null || link.quality.pixels == saved.qualityPixels)
        }
    }
}

/**
 * Apply a remembered per-media combination when it is available; otherwise keep
 * all global preferences in control. Within the saved server, its exact quality
 * is preferred. Other servers retain the configured global priority and quality.
 */
internal fun orderPlaybackLinks(
    links: List<VideoLink>,
    preferredAudioLanguage: String,
    serverPriority: List<String>,
    preferredQuality: String,
    rememberedPreference: MediaPlaybackPreference? = null,
): List<VideoLink> {
    if (links.size < 2) return links

    val activePreference = availableMediaPlaybackPreference(links, rememberedPreference)
    fun configuredServerRank(name: String): Int =
        serverPriority.indexOfFirst { it.equals(name, ignoreCase = true) }
            .takeIf { it >= 0 } ?: Int.MAX_VALUE

    return links.sortedWith(
        compareBy<VideoLink> {
            val language = activePreference?.audioLanguage ?: preferredAudioLanguage
            if (language == "auto" || it.audioLang.iso.equals(language, ignoreCase = true)) 0 else 1
        }.thenBy { link ->
            val saved = activePreference
            if (saved == null) {
                configuredServerRank(link.server)
            } else if (link.server.equals(saved.server, ignoreCase = true)) {
                0
            } else {
                configuredServerRank(link.server).let { rank ->
                    if (rank == Int.MAX_VALUE) rank else rank + 1
                }
            }
        }.thenBy { link ->
            val savedQuality = activePreference
                ?.takeIf {
                    it.server.equals(link.server, ignoreCase = true) &&
                        it.audioLanguage.equals(link.audioLang.iso, ignoreCase = true)
                }
                ?.qualityPixels
            playbackQualityRank(link.quality, savedQuality?.toString() ?: preferredQuality)
        }.thenByDescending { it.quality.pixels }
            .thenBy { if (it.streamType == StreamType.EMBED) 1 else 0 },
    )
}
