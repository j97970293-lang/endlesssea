package dev.endlesssea.app.ui.home

import dev.endlesssea.data.db.WatchHistoryEntity

private const val MAX_HOME_CONTINUE_ITEMS = 12

/**
 * Home shows at most one resume card per title, using the newest unfinished
 * episode. Standalone local files have no series ID, so they remain separate
 * cards keyed by their unique episode URI instead of collapsing under "".
 * The result is bounded even if a caller later supplies an unbounded history.
 */
internal fun latestContinueEntries(history: List<WatchHistoryEntity>): List<WatchHistoryEntity> =
    history.asSequence()
        .filter { it.episodeId.isNotBlank() && !it.watched && it.positionMs > 0L }
        .sortedByDescending { it.updatedAt }
        .distinctBy { entry ->
            if (entry.mediaId.isBlank()) "episode:${entry.episodeId}" else "media:${entry.mediaId}"
        }
        .take(MAX_HOME_CONTINUE_ITEMS)
        .toList()
