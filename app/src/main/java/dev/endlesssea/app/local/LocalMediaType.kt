package dev.endlesssea.app.local

/** The four editable labels used for local folders and downloaded groups. */
object LocalMediaType {
    const val ANIME = "ANIME"
    const val SERIES = "SERIES"
    const val MOVIE = "MOVIE"
    const val OTHER = "OTHER"

    val options: List<Pair<String, String>> = listOf(
        ANIME to "Anime",
        SERIES to "Série",
        MOVIE to "Film",
        OTHER to "Autre",
    )

    fun normalize(value: String?): String = when (value?.trim()?.uppercase()) {
        ANIME -> ANIME
        SERIES -> SERIES
        MOVIE -> MOVIE
        else -> OTHER
    }

    /** Conservative filename hint only; users can override it from the title's metadata editor. */
    fun infer(names: List<String>): String {
        val text = names.joinToString(" ").lowercase(java.util.Locale.ROOT)
        return when {
            Regex("""(?:^|[\s._-])(film|movie|cinema)(?:$|[\s._-])""").containsMatchIn(text) -> MOVIE
            Regex("""(?:saison|season)[ ._-]*\d+|\bs\d{1,2}e\d{1,3}\b""").containsMatchIn(text) -> SERIES
            else -> ANIME
        }
    }
}
