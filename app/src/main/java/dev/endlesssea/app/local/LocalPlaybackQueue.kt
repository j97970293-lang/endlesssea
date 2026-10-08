package dev.endlesssea.app.local

import dev.endlesssea.app.ui.library.LocalVideoUi

/** Keep one series per queue; titles edited by the user never affect episode ordering. */
internal fun localPlaybackQueue(files: List<LocalVideoUi>, selected: LocalVideoUi): List<LocalVideoUi> =
    (files.filter { selected.parentUri.isNotBlank() && it.parentUri == selected.parentUri } + selected)
        .distinctBy { it.uri }
        .sortedWith(compareBy<LocalVideoUi>(
            { LocalVideos.episodeSeason(it.name) ?: 0 },
            { playbackEpisodeOrder(it.name) ?: Double.MAX_VALUE },
            { it.name.lowercase(java.util.Locale.ROOT) },
        ))

/** Fractional specials stay in viewing order without being sent as integer tracker episodes. */
internal fun playbackEpisodeOrder(name: String): Double? {
    LocalVideos.episodeNumber(name)?.let { return it.toDouble() }
    val base = name.substringBeforeLast('.')
    val explicit = Regex("(?i)(?:s\\d{1,2}[ ._-]*e|\\bep?(?:isode)?[ ._-]?)(\\d{1,5}[.,]\\d+)").find(base)
    val standalone = Regex("(?:^|[ _-])(\\d{1,5}[.,]\\d+)(?=$|[ _-])").find(base)
    return (explicit ?: standalone)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
}
