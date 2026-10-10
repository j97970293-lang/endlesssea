package dev.endlesssea.app.ui.home

import dev.endlesssea.extensions.api.model.HomeCategory

/**
 * Sélectionne un échantillon représentatif des rangées d'une source.
 *
 * Plusieurs extensions déclarent d'abord 5–6 rangées de films puis leurs séries.
 * Un simple `take(2)`/`take(6)` donnait donc l'impression que le catalogue ne
 * contenait que des films. On alterne les familles tout en conservant l'ordre à
 * l'intérieur de chacune.
 */
fun balancedCategories(categories: List<HomeCategory>, limit: Int): List<HomeCategory> {
    if (limit <= 0) return emptyList()
    val clean = categories.filter { it.key.isNotBlank() }.distinctBy { it.key }
    if (clean.size <= limit) return clean

    fun family(category: HomeCategory): String {
        val text = (category.key + " " + category.title)
            .lowercase()
            .replace(Regex("[_/\\-]+"), " ")
        return when {
            // « live-tv » doit rester une chaîne en direct, pas être classée série.
            Regex("""\b(live|direct|chaine|chaîne)s?\b""").containsMatchIn(text) -> "live"
            Regex("""\b(series?|serie|série|tv|show)s?\b""").containsMatchIn(text) -> "series"
            Regex("""\b(anime|animation|manga)s?\b""").containsMatchIn(text) -> "anime"
            Regex("""\b(film|movie|cinema|cinéma)s?\b""").containsMatchIn(text) -> "movies"
            else -> "other"
        }
    }

    val groups = LinkedHashMap<String, ArrayDeque<HomeCategory>>()
    clean.forEach { groups.getOrPut(family(it)) { ArrayDeque() }.addLast(it) }
    val out = ArrayList<HomeCategory>(limit)
    while (out.size < limit && groups.values.any { it.isNotEmpty() }) {
        groups.values.forEach { queue ->
            if (out.size < limit && queue.isNotEmpty()) out += queue.removeFirst()
        }
    }
    return out
}
