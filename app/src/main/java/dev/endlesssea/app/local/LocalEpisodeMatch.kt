package dev.endlesssea.app.local

/** Never infer a season or choose between duplicates. Both sides must have unique keys. */
internal data class EpisodeMatchKey(val season: Int, val number: Double)

internal fun <L, R> matchLocalEpisodes(
    local: List<L>, remote: List<R>, localKey: (L) -> EpisodeMatchKey?, remoteKey: (R) -> EpisodeMatchKey?,
): List<Pair<L, R>> {
    val left = local.mapNotNull { item -> localKey(item)?.let { it to item } }.groupBy({ it.first }, { it.second })
    val right = remote.mapNotNull { item -> remoteKey(item)?.let { it to item } }.groupBy({ it.first }, { it.second })
    return left.mapNotNull { (key, candidates) ->
        val hits = right[key].orEmpty()
        if (candidates.size == 1 && hits.size == 1 && key.number.isFinite() && key.number >= 0 && key.season >= 0)
            candidates.single() to hits.single() else null
    }
}

/** Float source numbers need decimal normalization to match filename doubles (e.g. 12.1). */
internal fun onlineEpisodeKey(season: Int?, number: Float): EpisodeMatchKey? =
    season?.let { EpisodeMatchKey(it, number.toString().toDouble()) }
