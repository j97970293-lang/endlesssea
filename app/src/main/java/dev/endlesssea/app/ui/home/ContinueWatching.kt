package dev.endlesssea.app.ui.home

import dev.endlesssea.data.db.WatchHistoryEntity

/** A resume card opens a title, not an episode: the newest unfinished episode wins. */
internal fun latestContinueEntries(history: List<WatchHistoryEntity>): List<WatchHistoryEntity> =
    history.sortedByDescending { it.updatedAt }.distinctBy { it.mediaId }
