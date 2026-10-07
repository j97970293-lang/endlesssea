package dev.endlesssea.app.tracking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

/**
 * §suivi — AniList (GraphQL, gratuit, sans clé d'application côté serveur).
 *
 * Connexion : l'utilisateur crée une application sur
 * https://anilist.co/settings/developer (une seule fois, 30 s) et colle ici son
 * « Client ID ». L'application ouvre alors la page d'autorisation AniList dans
 * le navigateur ; AniList renvoie un jeton dans l'URL (`#access_token=…`) qu'il
 * suffit de coller dans le champ. Ce détour évite d'imposer un client secret —
 * c'est le mode « implicit grant » documenté par AniList.
 */
class AniListTracker(base: OkHttpClient) : Tracker {

    private val http = trackerClient(base)
    override val id = TrackerId.ANILIST

    override suspend fun verify(creds: TrackerCredentials): TrackerAccount? =
        withContext(Dispatchers.IO) {
            val body = """{"query":"query { Viewer { id name avatar { large } } }"}"""
            val text = runCatching {
                http.postJson(ENDPOINT, body, auth(creds))
            }.getOrNull() ?: return@withContext null
            val viewer = runCatching {
                JSONObject(text).getJSONObject("data").getJSONObject("Viewer")
            }.getOrNull() ?: return@withContext null
            TrackerAccount(
                id = TrackerId.ANILIST,
                userName = viewer.optString("name").ifBlank { "AniList" },
                avatarUrl = viewer.optJSONObject("avatar")?.optString("large"),
            )
        }

    override suspend fun search(creds: TrackerCredentials, title: String): List<TrackerMediaHit> =
        withContext(Dispatchers.IO) {
            val query = """
                query (${'$'}q: String) {
                  Page(perPage: 6) {
                    media(search: ${'$'}q, type: ANIME, sort: SEARCH_MATCH) {
                      id
                      title { romaji english native }
                      episodes
                      startDate { year }
                      coverImage { large }
                    }
                  }
                }
            """.trimIndent()
            val body = JSONObject()
                .put("query", query)
                .put("variables", JSONObject().put("q", title))
                .toString()
            val text = runCatching { http.postJson(ENDPOINT, body, auth(creds)) }.getOrNull()
                ?: return@withContext emptyList()
            val media = runCatching {
                JSONObject(text).getJSONObject("data").getJSONObject("Page").getJSONArray("media")
            }.getOrNull() ?: return@withContext emptyList()
            (0 until media.length()).mapNotNull { i ->
                val o = media.optJSONObject(i) ?: return@mapNotNull null
                TrackerMediaHit(
                    remoteId = o.optInt("id").toString(),
                    title = anilistTitle(o).ifBlank { "Fiche AniList" },
                    episodes = o.optInt("episodes").takeIf { it > 0 },
                    posterUrl = o.optJSONObject("coverImage")?.optString("large"),
                    year = o.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 },
                )
            }
        }

    override suspend fun updateProgress(
        creds: TrackerCredentials,
        remoteId: String,
        progress: Int,
        totalEpisodes: Int?,
        status: TrackerStatus,
    ): Boolean = withContext(Dispatchers.IO) {
        val query = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) {
                id
                progress
                status
              }
            }
        """.trimIndent()
        val body = JSONObject()
            .put("query", query)
            .put(
                "variables",
                JSONObject()
                    .put("mediaId", remoteId.toIntOrNull() ?: return@withContext false)
                    .put("progress", progress)
                    .put("status", aniListStatus(status, progress, totalEpisodes)),
            )
            .toString()
        val text = runCatching { http.postJson(ENDPOINT, body, auth(creds)) }.getOrNull()
            ?: return@withContext false
        runCatching { JSONObject(text).getJSONObject("data").has("SaveMediaListEntry") }
            .getOrDefault(false)
    }

    override fun authorizeUrl(creds: TrackerCredentials, redirectUri: String): String =
        if (creds.clientId.isBlank()) ""
        else "https://anilist.co/api/v2/oauth/authorize?client_id=${creds.clientId}" +
            "&response_type=token"

    private fun auth(creds: TrackerCredentials): Map<String, String> =
        mapOf("Authorization" to "Bearer ${creds.token}", "Content-Type" to "application/json")

    /** Statut AniList déduit de la progression (évite d'envoyer un état incohérent). */
    private fun aniListStatus(status: TrackerStatus, progress: Int, total: Int?): String = when {
        total != null && progress >= total -> "COMPLETED"
        else -> when (status) {
            TrackerStatus.WATCHING -> "CURRENT"
            TrackerStatus.COMPLETED -> "COMPLETED"
            TrackerStatus.PLANNING -> "PLANNING"
            TrackerStatus.DROPPED -> "DROPPED"
        }
    }

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co"

        /** Lien de recherche AniList (secours depuis la fiche, sans compte connecté). */
        fun searchPageUrl(title: String): String =
            "https://anilist.co/search/anime?search=" + URLEncoder.encode(title, "UTF-8")
    }
}
