package dev.endlesssea.app.ui.explore

import dev.endlesssea.extensions.api.model.MediaType

/** Genre metadata is available for cached details; unknown values do not match a filter. */
internal fun filterExploreRows(
    rows: List<ExploreRowUi>,
    genre: String?,
    year: Int?,
    typeLabel: String?,
): List<ExploreRowUi> {
    val type = when (typeLabel) {
        "Anime" -> MediaType.ANIME
        "Film" -> MediaType.MOVIE
        "Série" -> MediaType.SERIES
        "OVA" -> MediaType.OVA
        "ONA" -> MediaType.ONA
        else -> null
    }
    return rows.map { row ->
        row.copy(items = row.items.filter { item ->
            (genre == null || item.genres.any { it.equals(genre, ignoreCase = true) }) &&
                (year == null || item.year == year) &&
                (typeLabel == null || (type != null && item.type == type))
        })
    }.filter { it.items.isNotEmpty() }
}
