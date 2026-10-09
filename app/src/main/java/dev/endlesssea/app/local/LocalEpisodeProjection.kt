package dev.endlesssea.app.local

import dev.endlesssea.app.ui.library.LocalVideoUi
import dev.endlesssea.extensions.api.model.Episode

/** Local URIs remain the episode/history identity, regardless of metadata association or sorting. */
internal fun localEpisode(file: LocalVideoUi): Episode = Episode(
    id = file.uri,
    number = file.matchedNumber ?: playbackEpisodeOrder(file.name)?.toFloat() ?: Float.NaN,
    season = file.matchedSeason ?: LocalVideos.episodeSeason(file.name),
    title = file.customTitle ?: file.matchedTitle ?: LocalVideos.episodeTitleFromFileName(file.name),
    thumbnailUrl = file.uri, durationMs = file.durationMs, data = file.uri,
)
