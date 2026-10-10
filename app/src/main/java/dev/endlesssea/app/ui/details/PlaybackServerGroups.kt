package dev.endlesssea.app.ui.details

import dev.endlesssea.extensions.api.model.AudioLang
import dev.endlesssea.extensions.api.model.VideoLink

/** Audio buckets ordered with VOSTFR and VF first for quick access in the player sheet. */
internal fun playbackLanguageGroups(
    links: List<VideoLink>,
    preferredLanguage: String = "auto",
): List<Pair<AudioLang, List<VideoLink>>> {
    val defaultOrder = listOf(AudioLang.VOSTFR, AudioLang.VF, AudioLang.MULTI, AudioLang.VO, AudioLang.OTHER)
    val preferred = AudioLang.entries.firstOrNull { preferredLanguage != "auto" && it.iso.equals(preferredLanguage, true) }
    val order = listOfNotNull(preferred) + defaultOrder.filterNot { it == preferred }
    return order.mapNotNull { lang ->
        links.filter { it.audioLang == lang }.takeIf { it.isNotEmpty() }?.let { lang to it }
    }
}
