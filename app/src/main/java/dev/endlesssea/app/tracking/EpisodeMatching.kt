package dev.endlesssea.app.tracking

/** Règle conservatrice : un rattachement correspond à une saison, pas à tout un catalogue. */
internal object EpisodeMatching {
    fun nextProgress(
        enabled: Boolean, matchedSeason: Int?, season: Int?, number: Float?,
        progress: Int, total: Int,
    ): Int? {
        if (!enabled || season != matchedSeason || number == null || !number.isFinite()) return null
        if (number <= 0f || number >= Int.MAX_VALUE.toFloat() || number % 1f != 0f) return null
        val episode = number.toInt()
        if (episode <= progress || (total > 0 && episode > total)) return null
        return episode
    }
}
