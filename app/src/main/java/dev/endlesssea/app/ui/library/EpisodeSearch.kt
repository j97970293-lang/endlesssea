package dev.endlesssea.app.ui.library

/** A number means an exact episode, not a substring (1000 must not return 10001). */
internal fun matchesEpisodeSearch(query: String, number: Double?, vararg titles: String?): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val numeric = q.removePrefix("#").replace(',', '.').toDoubleOrNull()
    if (numeric != null && number != null) return numeric == number
    return titles.any { it?.contains(q, ignoreCase = true) == true }
}
