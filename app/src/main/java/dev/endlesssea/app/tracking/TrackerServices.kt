package dev.endlesssea.app.tracking

import dev.endlesssea.core.diag.EsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * §suivi (conversation 11) — implémentations des services de suivi gratuits.
 *
 * Chaque service reste facultatif : l'application fonctionne exactement pareil
 * sans aucun compte. Deux familles :
 *  · services de suivi (AniList, MyAnimeList, Shikimori) : recherche d'un titre,
 *    rattachement, progression, statut ;
 *  · fournisseur de métadonnées (TMDB) : affiches, bannières, bandes-annonces.
 *
 * Tous utilisent le même [OkHttpClient] que le reste de l'application (DoH,
 * cache, en-têtes honnêtes) — un seul point de sortie réseau.
 */
interface TrackerService {
    val id: String

    /** Ping : renvoie le pseudo si les identifiants sont valides, null sinon. */
    suspend fun whoAmI(account: dev.endlesssea.data.db.TrackerAccountEntity): String?

    /** Recherche un titre (sert au rattachement manuel depuis la fiche). */
    suspend fun search(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        query: String,
    ): List<TrackerSearchHit>

    /** Écrit progression et statut sur le service. False = à rejouer plus tard. */
    suspend fun pushProgress(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        remoteId: String,
        progress: Int,
        totalEpisodes: Int?,
        status: String,
    ): Boolean

    /** Détails complémentaires (affiche, bannière, bande-annonce) — TMDB surtout. */
    suspend fun details(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        remoteId: String,
    ): TrackerDetails? = null
}

/** Résultat de recherche d'un service (proposé au rattachement manuel). */
data class TrackerSearchHit(
    val remoteId: String,
    val title: String,
    val totalEpisodes: Int? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
)

/** Métadonnées récupérées auprès d'un fournisseur (TMDB). */
data class TrackerDetails(
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val trailerUrl: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
)

private val JSON = "application/json; charset=utf-8".toMediaType()

private fun OkHttpClient.call(request: Request): String {
    return newCall(request).execute().use { res ->
        val body = res.body?.string().orEmpty()
        if (res.code == 401 || res.code == 403) throw TrackerError("Jeton refusé (HTTP ${res.code})")
        if (res.code == 429) throw TrackerError("Quota atteint (HTTP 429) — réessaie plus tard")
        if (res.code !in 200..299) throw TrackerError("HTTP ${res.code} : ${body.take(160)}")
        body
    }
}

class TrackerError(message: String) : Exception(message)

private fun OkHttpClient.getJson(url: String, bearer: String? = null, extra: Map<String, String> = emptyMap()): String {
    val b = Request.Builder().url(url)
    if (bearer != null) b.header("Authorization", "Bearer $bearer")
    // AniList et Shikimori exigent un User-Agent explicite.
    b.header("User-Agent", "EndlessSea/0.23 (+https://github.com/j97970293-lang/endlesssea)")
    extra.forEach { (k, v) -> b.header(k, v) }
    return call(b.get().build())
}

private fun OkHttpClient.postJson(url: String, json: String, bearer: String? = null): String {
    val b = Request.Builder().url(url).post(json.toRequestBody(JSON))
    if (bearer != null) b.header("Authorization", "Bearer $bearer")
    b.header("User-Agent", "EndlessSea/0.23 (+https://github.com/j97970293-lang/endlesssea)")
    return call(b.build())
}

// ------------------------------------------------------------------ AniList

/**
 * AniList (GraphQL, gratuit). Le plus simple des services : un « access token »
 * créé depuis https://anilist.co/settings/developer (flux *implicit grant*) —
 * l'URL de redirection contient le jeton, il suffit de le copier dans l'app.
 */
class AniListService(private val http: OkHttpClient) : TrackerService {
    override val id = "ANILIST"

    override suspend fun whoAmI(account: dev.endlesssea.data.db.TrackerAccountEntity): String? =
        withContext(Dispatchers.IO) {
            val token = account.accessToken ?: return@withContext null
            val body = http.postJson(
                ENDPOINT,
                """{"query":"query { Viewer { name } }"}""",
                token,
            )
            JSONObject(body).optJSONObject("data")?.optJSONObject("Viewer")?.optString("name")
                ?.takeIf { it.isNotBlank() }
        }

    override suspend fun search(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        query: String,
    ): List<TrackerSearchHit> = withContext(Dispatchers.IO) {
        val token = account.accessToken ?: return@withContext emptyList()
        val gql = """
            query (${'$'}q: String) {
              Page(perPage: 8) {
                media(search: ${'$'}q, type: ANIME, sort: SEARCH_MATCH) {
                  id
                  title { romaji english }
                  episodes
                  seasonYear
                  coverImage { large }
                }
              }
            }
        """.trimIndent()
        val payload = JSONObject()
            .put("query", gql)
            .put("variables", JSONObject().put("q", query))
            .toString()
        val body = http.postJson(ENDPOINT, payload, token)
        val media = JSONObject(body).optJSONObject("data")
            ?.optJSONObject("Page")?.optJSONArray("media") ?: return@withContext emptyList()
        (0 until media.length()).mapNotNull { i ->
            val m = media.optJSONObject(i) ?: return@mapNotNull null
            val title = m.optJSONObject("title")
            TrackerSearchHit(
                remoteId = m.optInt("id").toString(),
                title = title?.optString("romaji").orEmpty().ifBlank {
                    title?.optString("english").orEmpty()
                },
                totalEpisodes = m.optInt("episodes").takeIf { it > 0 },
                posterUrl = m.optJSONObject("coverImage")?.optString("large"),
                year = m.optInt("seasonYear").takeIf { it > 0 },
            )
        }
    }

    override suspend fun pushProgress(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        remoteId: String,
        progress: Int,
        totalEpisodes: Int?,
        status: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val token = account.accessToken ?: return@withContext false
        val gql = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress, status: ${'$'}status) {
                id
              }
            }
        """.trimIndent()
        // « Terminé » est forcé quand la progression atteint le total connu :
        // envoyer 12/12 avec le statut « en cours » serait incohérent côté AniList.
        val effective = if (totalEpisodes != null && progress >= totalEpisodes && totalEpisodes > 0) {
            "COMPLETED"
        } else status
        val payload = JSONObject()
            .put("query", gql)
            .put(
                "variables",
                JSONObject()
                    .put("mediaId", remoteId.toIntOrNull() ?: return@withContext false)
                    .put("progress", progress)
                    .put("status", effective),
            )
            .toString()
        val body = http.postJson(ENDPOINT, payload, token)
        JSONObject(body).has("data") && !JSONObject(body).has("errors")
    }

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co"

        /** Lien de recherche directe (secours quand aucun compte n'est connecté). */
        fun searchUrl(query: String) =
            "https://anilist.co/search/anime?search=" + URLEncoder.encode(query, "UTF-8")
    }
}

// ------------------------------------------------------------------ MyAnimeList

/**
 * MyAnimeList (API v2). Le jeton d'accès s'obtient via OAuth2 avec PKCE ;
 * l'utilisateur colle ici l'`access_token` (et, s'il l'a, le `refresh_token`
 * pour que l'application puisse le renouveler toute seule — le jeton MAL ne
 * dure qu'une heure).
 */
class MalService(private val http: OkHttpClient) : TrackerService {
    override val id = "MAL"

    override suspend fun whoAmI(account: dev.endlesssea.data.db.TrackerAccountEntity): String? =
        withContext(Dispatchers.IO) {
            val token = account.accessToken ?: return@withContext null
            val body = http.getJson("$API/users/@me", token)
            JSONObject(body).optString("name").takeIf { it.isNotBlank() }
        }

    override suspend fun search(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        query: String,
    ): List<TrackerSearchHit> = withContext(Dispatchers.IO) {
        val token = account.accessToken ?: return@withContext emptyList()
        val url = "$API/anime?q=" + URLEncoder.encode(query, "UTF-8") +
            "&limit=8&fields=id,title,num_episodes,start_season,main_picture"
        val body = http.getJson(url, token)
        val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val node = arr.optJSONObject(i)?.optJSONObject("node") ?: return@mapNotNull null
            TrackerSearchHit(
                remoteId = node.optInt("id").toString(),
                title = node.optString("title"),
                totalEpisodes = node.optInt("num_episodes").takeIf { it > 0 },
                posterUrl = node.optJSONObject("main_picture")?.optString("medium"),
                year = node.optJSONObject("start_season")?.optInt("year")?.takeIf { it > 0 },
            )
        }
    }

    override suspend fun pushProgress(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        remoteId: String,
        progress: Int,
        totalEpisodes: Int?,
        status: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val token = account.accessToken ?: return@withContext false
        val effective = if (totalEpisodes != null && totalEpisodes > 0 && progress >= totalEpisodes) {
            "completed"
        } else status.lowercase()
        val form = "num_watched_episodes=$progress&status=$effective"
        val request = Request.Builder()
            .url("$API/anime/$remoteId/my_list_status")
            .put(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "EndlessSea/0.23")
            .build()
        runCatching { http.call(request) }.isSuccess
    }

    /** Renouvelle un jeton expiré (MAL expire après 1 h). */
    suspend fun refresh(account: dev.endlesssea.data.db.TrackerAccountEntity): String? =
        withContext(Dispatchers.IO) {
            val clientId = account.clientId ?: return@withContext null
            val refresh = account.refreshToken ?: return@withContext null
            val form = "client_id=$clientId&grant_type=refresh_token&refresh_token=" +
                URLEncoder.encode(refresh, "UTF-8")
            val request = Request.Builder()
                .url("https://myanimelist.net/v1/oauth2/token")
                .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            runCatching {
                JSONObject(http.call(request)).optString("access_token").takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    companion object {
        private const val API = "https://api.myanimelist.net/v2"
    }
}

// ------------------------------------------------------------------ Shikimori

/**
 * Shikimori — utile quand un titre est absent du catalogue MAL ou pour les
 * utilisateurs hispanophones/russophones. Jeton collé après autorisation sur
 * https://shikimori.one/oauth (le client doit être public, redirect vide).
 */
class ShikimoriService(private val http: OkHttpClient) : TrackerService {
    override val id = "SHIKIMORI"

    override suspend fun whoAmI(account: dev.endlesssea.data.db.TrackerAccountEntity): String? =
        withContext(Dispatchers.IO) {
            val token = account.accessToken ?: return@withContext null
            val body = http.getJson("$API/users/whoami", token)
            JSONObject(body).optString("nickname").takeIf { it.isNotBlank() }
        }

    override suspend fun search(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        query: String,
    ): List<TrackerSearchHit> = withContext(Dispatchers.IO) {
        val url = "$API/animes?search=" + URLEncoder.encode(query, "UTF-8") +
            "&limit=8&order=popularity"
        val body = http.getJson(url)
        val arr = runCatching { JSONArray(body) }.getOrNull() ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            TrackerSearchHit(
                remoteId = a.optInt("id").toString(),
                title = a.optString("name"),
                totalEpisodes = a.optInt("episodes").takeIf { it > 0 },
                posterUrl = a.optJSONObject("image")?.optString("original"),
                year = a.optString("aired_on").take(4).toIntOrNull(),
            )
        }
    }

    override suspend fun pushProgress(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        remoteId: String,
        progress: Int,
        totalEpisodes: Int?,
        status: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val token = account.accessToken ?: return@withContext false
        val shikiStatus = when {
            totalEpisodes != null && totalEpisodes > 0 && progress >= totalEpisodes -> "completed"
            status.equals("COMPLETED", true) -> "completed"
            status.equals("DROPPED", true) -> "dropped"
            status.equals("PLANNING", true) -> "planned"
            else -> "watching"
        }
        // Shikimori crée/écrase le taux de visionnage (upsert métier).
        val payload = JSONObject()
            .put("user_rate", JSONObject()
                .put("target_id", remoteId)
                .put("target_type", "Anime")
                .put("episodes", progress)
                .put("status", shikiStatus))
            .toString()
        val request = Request.Builder()
            .url("$API/v2/user_rates")
            .post(payload.toRequestBody(JSON))
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "EndlessSea/0.23")
            .build()
        runCatching { http.call(request) }.isSuccess
    }

    companion object {
        private const val API = "https://shikimori.one/api"
    }
}

// ------------------------------------------------------------------ TMDB

/**
 * TMDB — pas un service de suivi mais la meilleure source de métadonnées :
 * affiches, bannières, synopsis et **bandes-annonces**. Clé d'API gratuite
 * (v3) collée dans les Paramètres ; l'application ne l'utilise que pour
 * compléter les fiches où l'extension ne fournit rien.
 */
class TmdbService(private val http: OkHttpClient) : TrackerService {
    override val id = "TMDB"

    override suspend fun whoAmI(account: dev.endlesssea.data.db.TrackerAccountEntity): String? =
        withContext(Dispatchers.IO) {
            val key = account.apiKey ?: return@withContext null
            val body = http.getJson("$API/configuration?api_key=$key")
            if (JSONObject(body).has("images")) "Clé TMDB valide" else null
        }

    override suspend fun search(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        query: String,
    ): List<TrackerSearchHit> = withContext(Dispatchers.IO) {
        val key = account.apiKey ?: return@withContext emptyList()
        val url = "$API/search/multi?api_key=$key&include_adult=false&language=fr-FR&query=" +
            URLEncoder.encode(query, "UTF-8")
        val body = http.getJson(url)
        val arr = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val kind = r.optString("media_type")
            if (kind != "tv" && kind != "movie") return@mapNotNull null
            TrackerSearchHit(
                // « tv:1399 » conserve le type : indispensable pour l'URL des détails.
                remoteId = "$kind:${r.optInt("id")}",
                title = r.optString("name").ifBlank { r.optString("title") },
                totalEpisodes = r.optInt("number_of_episodes").takeIf { it > 0 },
                posterUrl = r.optString("poster_path").takeIf { it.isNotBlank() }
                    ?.let { IMAGE_BASE + it },
                year = r.optString("first_air_date").ifBlank { r.optString("release_date") }
                    .take(4).toIntOrNull(),
            )
        }
    }

    /** TMDB ne gère pas de progression : rien à pousser. */
    override suspend fun pushProgress(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        remoteId: String,
        progress: Int,
        totalEpisodes: Int?,
        status: String,
    ): Boolean = false

    override suspend fun details(
        account: dev.endlesssea.data.db.TrackerAccountEntity,
        remoteId: String,
    ): TrackerDetails? = withContext(Dispatchers.IO) {
        val key = account.apiKey ?: return@withContext null
        val parts = remoteId.split(":")
        if (parts.size != 2) return@withContext null
        val (kind, tmdbId) = parts
        val body = http.getJson("$API/$kind/$tmdbId?api_key=$key&language=fr-FR&append_to_response=videos")
        val root = JSONObject(body)
        // Bande-annonce : on préfère la VF, sinon la VO (les deux sont fréquentes).
        val videos = root.optJSONObject("videos")?.optJSONArray("results")
        var trailer: String? = null
        if (videos != null) {
            for (i in 0 until videos.length()) {
                val v = videos.optJSONObject(i) ?: continue
                if (!v.optString("site").equals("YouTube", true)) continue
                val key2 = v.optString("key")
                val type = v.optString("type")
                if (type == "Trailer" || type == "Teaser") trailer = "https://www.youtube.com/watch?v=$key2"
                if (type == "Trailer") break
            }
        }
        TrackerDetails(
            posterUrl = root.optString("poster_path").takeIf { it.isNotBlank() }
                ?.let { IMAGE_BASE + it },
            bannerUrl = root.optString("backdrop_path").takeIf { it.isNotBlank() }
                ?.let { IMAGE_BASE + it },
            trailerUrl = trailer,
            synopsis = root.optString("overview").takeIf { it.isNotBlank() },
            year = (root.optString("first_air_date").ifBlank { root.optString("release_date") })
                .take(4).toIntOrNull(),
        )
    }

    companion object {
        private const val API = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    }
}

/** Fabrique centralisée : un seul endroit qui connaît les quatre services. */
object TrackerRegistry {
    fun all(http: OkHttpClient): List<TrackerService> = listOf(
        AniListService(http),
        MalService(http),
        ShikimoriService(http),
        TmdbService(http),
    )

    /** Libellés affichés dans l'interface (écran « Comptes & suivi »). */
    val LABELS: Map<String, String> = mapOf(
        "ANILIST" to "AniList",
        "MAL" to "MyAnimeList",
        "SHIKIMORI" to "Shikimori",
        "TMDB" to "TMDB (affiches & bandes-annonces)",
    )

    /** Aide affichée sous chaque service (où trouver le jeton/la clé). */
    val HINTS: Map<String, String> = mapOf(
        "ANILIST" to "anilist.co/settings/developer → crée une application, autorise-la, " +
            "puis copie le jeton de l'URL de redirection.",
        "MAL" to "myanimelist.net/apiconfig → crée une application, récupère un " +
            "access_token (OAuth2 PKCE) et colle-le ici.",
        "SHIKIMORI" to "shikimori.one/oauth → autorise l'application, puis copie le jeton " +
            "reçu (l'API est ouverte en lecture, le jeton sert à écrire la progression).",
        "TMDB" to "themoviedb.org/settings/api → clé gratuite (v3). Utilisée uniquement " +
            "pour compléter affiches et bandes-annonces manquantes.",
    )

    fun log(tag: String, message: String) = EsLog.e("Tracker", tag, message)
}
