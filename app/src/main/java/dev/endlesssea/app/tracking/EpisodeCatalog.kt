package dev.endlesssea.app.tracking

import dev.endlesssea.extensions.api.model.Episode
import org.json.JSONObject

data class CatalogEpisode(val number: Int, val title: String?, val imageUrl: String?)

/**
 * Fills missing episode titles and stills. Callers must skip the network when the
 * extension already provided both, so opening a complete fiche stays free.
 */
fun mergeCatalog(episodes: List<Episode>, catalog: List<CatalogEpisode>): List<Episode> {
    if (catalog.isEmpty()) return episodes
    val byNumber = catalog.associateBy { it.number }
    return episodes.map { episode ->
        val extra = byNumber[episode.number.toInt()] ?: return@map episode
        episode.copy(
            title = episode.title?.takeIf { it.isNotBlank() } ?: extra.title,
            thumbnailUrl = episode.thumbnailUrl?.takeIf { it.isNotBlank() } ?: extra.imageUrl,
        )
    }
}

fun parseAniZipEpisodes(json: String): List<CatalogEpisode> = runCatching {
    val episodes = JSONObject(json).optJSONObject("episodes") ?: return emptyList()
    episodes.keys().asSequence().mapNotNull { key ->
        val item = episodes.optJSONObject(key) ?: return@mapNotNull null
        val number = item.optString("episode").toIntOrNull() ?: key.toIntOrNull() ?: return@mapNotNull null
        val title = item.optJSONObject("title")?.let { titles ->
            titles.optString("fr").takeIf { it.isNotBlank() }
                ?: titles.optString("en").takeIf { it.isNotBlank() }
                ?: titles.optString("ja").takeIf { it.isNotBlank() }
        } ?: item.optString("title").takeIf { it.isNotBlank() && !it.startsWith("{") }
        CatalogEpisode(number, title, item.optString("image").takeIf { it.startsWith("http") })
    }.toList()
}.getOrDefault(emptyList())

fun parseJikanEpisodes(json: String): List<CatalogEpisode> = runCatching {
    val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
    (0 until data.length().coerceAtMost(100)).mapNotNull { index ->
        val item = data.optJSONObject(index) ?: return@mapNotNull null
        val number = item.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
        CatalogEpisode(number, item.optString("title").takeIf { it.isNotBlank() }, null)
    }
}.getOrDefault(emptyList())

fun parseTmdbSeason(json: String, imageBase: String = "https://image.tmdb.org/t/p/w300"): List<CatalogEpisode> =
    runCatching {
        val episodes = JSONObject(json).optJSONArray("episodes") ?: return emptyList()
        (0 until episodes.length().coerceAtMost(100)).mapNotNull { index ->
            val item = episodes.optJSONObject(index) ?: return@mapNotNull null
            val number = item.optInt("episode_number", 0).takeIf { it > 0 } ?: return@mapNotNull null
            val still = item.optString("still_path").takeIf { it.startsWith("/") }?.let { imageBase + it }
            CatalogEpisode(number, item.optString("name").takeIf { it.isNotBlank() }, still)
        }
    }.getOrDefault(emptyList())
