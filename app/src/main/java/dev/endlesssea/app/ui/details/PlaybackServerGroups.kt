package dev.endlesssea.app.ui.details

import dev.endlesssea.extensions.api.model.AudioLang
import dev.endlesssea.extensions.api.model.VideoLink

/** Audio buckets ordered with VOSTFR and VF first for quick access in the player sheet. */
internal fun playbackLanguageGroups(links: List<VideoLink>): List<Pair<AudioLang, List<VideoLink>>> {
    val order = listOf(AudioLang.VOSTFR, AudioLang.VF, AudioLang.MULTI, AudioLang.VO, AudioLang.OTHER)
    return order.mapNotNull { lang ->
        links.filter { it.audioLang == lang }.takeIf { it.isNotEmpty() }?.let { lang to it }
    }
}
