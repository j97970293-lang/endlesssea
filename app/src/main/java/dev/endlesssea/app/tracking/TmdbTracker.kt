package dev.endlesssea.app.tracking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

/**
 * §suivi — TMDB (métadonnées : affiches, bannières, bandes-annonces).
 *
 * TMDB ne gère pas de progression : il sert à COMPLÉTER les fiches quand
 * l'extension ne fournit ni affiche ni bande-annonce. Clé d'API gratuite
 * (https://www.themoviedb.org/settings/api) — v3 ou jeton v4 (Bearer).
 */
class TmdbTracker(base: OkHttpClient) : Tracker {

    private val http = trackerClient(base)
    override val id = TrackerId.TMDB

    override suspend fun verify(creds: TrackerCredentials): TrackerAccount? =
        withContext(Dispatchers.IO) {
            // /configuration ne renvoie pas de pseudo : on prouve la clé puis on
            // affiche un libellé neutre (aucune donnée personnelle n'est lisible).
            val ok = runCatching { http.get(url(creds, "/configuration"), headers(creds)) }.isSuccess
            if (!ok) null else TrackerAccount(TrackerId.TMDB, "Clé TMDB valide")
        }

    override suspend fun search(creds: TrackerCredentials, title: String): List<TrackerMediaHit> =
        withContext(Dispatchers.IO) {
            val url = url(creds, "/search/multi") + "&query=" + URLEncoder.encode(title, "UTF-8")
            val text = runCatching { http.get(url, headers(creds)) }.getOrNull()
                ?: return@withContext emptyList()
            val results = runCatching { JSONObject(text).optJSONArray("results") }.getOrNull()
                ?: return@withContext emptyList()
            (0 until results.length()).mapNotNull { i ->
                val o = results.optJSONObject(i) ?: return@mapNotNull null
                val kind = o.optString("media_type")
                if (kind != "tv" && kind != "movie") return@mapNotNull null
                TrackerMediaHit(
                    remoteId = "${kind}:${o.optInt("id")}",
                    title = o.optString("name").ifBlank { o.optString("title") },
                    episodes = o.optInt("number_of_episodes").takeIf { it > 0 },
                    posterUrl = o.optString("poster_path").takeIf { it.isNotBlank() }
                        ?.let { IMAGE_BASE + it },
                    year = (o.optString("first_air_date").ifBlank { o.optString("release_date") })
                        .take(4).toIntOrNull(),
                )
            }
        }

    /** TMDB ne suit pas la progression — toujours false (jamais appelé). */
    override suspend fun updateProgress(
        creds: TrackerCredentials,
        remoteId: String,
        progress: Int,
        totalEpisodes: Int?,
        status: TrackerStatus,
    ): Boolean = false

    override fun authorizeUrl(creds: TrackerCredentials, redirectUri: String): String = ""

    // ---------------------------------------------------------------- enrichissement

    /** Affiche 500 px + bannière d'un résultat « tv:123 » / « movie:123 ». */
    data class Visuals(val posterUrl: String?, val bannerUrl: String?, val trailerUrl: String?)

    /**
     * Complète une fiche : affiche, bannière et bande-annonce (française si
     * possible, sinon anglaise). Renvoie null si la clé est absente ou si le
     * titre n'est pas trouvé — la fiche garde alors ses propres visuels.
     */
    suspend fun visualsFor(
        creds: TrackerCredentials,
        title: String,
        year: Int? = null,
    ): Visuals? = withContext(Dispatchers.IO) {
        if (!creds.hasKey) return@withContext null
        val hit = search(creds, title).firstOrNull() ?: return@withContext null
        val parts = hit.remoteId.split(":")
        if (parts.size != 2) return@withContext null
        val kind = parts[0]
        val tmdbId = parts[1]

        val detailsText = runCatching {
            http.get(url(creds, "/$kind/$tmdbId"), headers(creds))
        }.getOrNull() ?: return@withContext null
        val details = runCatching { JSONObject(detailsText) }.getOrNull() ?: return@withContext null
        val poster = details.optString("poster_path").takeIf { it.isNotBlank() }?.let { IMAGE_BASE_ORIG + it }
            ?: hit.posterUrl
        val banner = details.optString("backdrop_path").takeIf { it.isNotBlank() }
            ?.let { IMAGE_BASE_ORIG + it }

        // Bande-annonce : VF d'abord, sinon VO (les deux sont fréquentes).
        val trailer = runCatching { trailerKey(creds, kind, tmdbId, "fr-FR") }.getOrNull()
            ?: runCatching { trailerKey(creds, kind, tmdbId, "en-US") }.getOrNull()

        Visuals(posterUrl = poster, bannerUrl = banner, trailerUrl = trailer)
    }

    private fun trailerKey(creds: TrackerCredentials, kind: String, id: String, lang: String): String? {
        val text = http.get(url(creds, "/$kind/$id/videos") + "&language=$lang", headers(creds))
        val arr = JSONObject(text).optJSONArray("results") ?: return null
        // On privilégie une vraie bande-annonce YouTube, sinon n'importe quel extrait.
        var teaser: String? = null
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            if (v.optString("site") != "YouTube") continue
            val key = v.optString("key").takeIf { it.isNotBlank() } ?: continue
            return when (v.optString("type")) {
                "Trailer" -> "https://www.youtube.com/watch?v=$key"
                "Teaser" -> "https://www.youtube.com/watch?v=$key"
                else -> { teaser = "https://www.youtube.com/watch?v=$key"; null }
            } ?: continue
        }
        return teaser
    }

    private fun url(creds: TrackerCredentials, path: String): String =
        "$API$path" + if (creds.apiKey.isNotBlank()) "?api_key=${creds.apiKey}" else "?"

    private fun headers(creds: TrackerCredentials): Map<String, String> =
        // Jeton v4 (commence par « eyJ ») → en-tête Bearer, clé v3 → paramètre.
        if (creds.apiKey.startsWith("eyJ")) mapOf("Authorization" to "Bearer ${creds.apiKey}")
        else emptyMap()

    companion object {
        private const val API = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
        private const val IMAGE_BASE_ORIG = "https://image.tmdb.org/t/p/original"
    }
}
