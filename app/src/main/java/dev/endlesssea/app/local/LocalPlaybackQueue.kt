package dev.endlesssea.app.local

import dev.endlesssea.app.ui.library.LocalVideoUi

/** Keep one series per queue; titles edited by the user never affect episode ordering. */
internal fun localPlaybackQueue(files: List<LocalVideoUi>, selected: LocalVideoUi): List<LocalVideoUi> =
    (files.filter { selected.parentUri.isNotBlank() && it.parentUri == selected.parentUri } + selected)
        .distinctBy { it.uri }
        .sortedWith(compareBy<LocalVideoUi>(
            { LocalVideos.episodeSeason(it.name) ?: 0 },
            { LocalVideos.episodeNumber(it.name) ?: Int.MAX_VALUE },
            { it.name.lowercase(java.util.Locale.ROOT) },
        ))
