package dev.endlesssea.app.tracking

import dev.endlesssea.core.diag.EsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

    /** Null means confirmed absence; network/auth failures must throw, never mean zero. */
    suspend fun readProgress(account: dev.endlesssea.data.db.TrackerAccountEntity, remoteId: String): RemoteTrackerProgress? =
        throw TrackerError("Ce service ne fournit pas de progression")

    suspend fun library(account: dev.endlesssea.data.db.TrackerAccountEntity): List<RemoteLibraryEntry> =
        throw TrackerError("Import de liste indisponible pour ce service")

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

data class RemoteTrackerProgress(val progress: Int, val status: String)

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
    val title: String? = null,
    val genres: List<String> = emptyList(),
)

private val JSON = "application/json; charset=utf-8".toMediaType()

private fun OkHttpClient.call(request: Request): String {
    return newCall(request).execute().use { res ->
        val body = res.body?.string().orEmpty()
        if (res.code == 401 || res.code == 403) throw TrackerError("Jeton refusé (HTTP ${res.code})", res.code)
        if (res.code == 429) throw TrackerError("Quota atteint (HTTP 429) — réessaie plus tard")
        if (res.code !in 200..299) throw TrackerError("HTTP ${res.code} : ${body.take(160)}")
        body
    }
}

class TrackerError(message: String, val httpCode: Int? = null) : Exception(message)

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
        val result = JSONObject(body)
        if (result.optJSONArray("errors")?.length()?.let { it > 0 } == true) {
            throw TrackerError("Recherche AniList refusée ou indisponible. Vérifiez la connexion de votre compte.")
        }
        val media = result.optJSONObject("data")
            ?.optJSONObject("Page")?.optJSONArray("media") ?: throw TrackerError("Réponse AniList incomplète")
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

    override suspend fun readProgress(account: dev.endlesssea.data.db.TrackerAccountEntity, remoteId: String): RemoteTrackerProgress? = withContext(Dispatchers.IO) {
        val token = account.accessToken ?: throw TrackerError("Compte non connecté")
        val query = "query (${'$'}id: Int) { Media(id: ${'$'}id, type: ANIME) { mediaListEntry { progress status } } }"
        val payload = JSONObject().put("query", query).put("variables", JSONObject().put("id", remoteId.toInt())).toString()
        val response = JSONObject(http.postJson(ENDPOINT, payload, token))
        if (response.has("errors")) throw TrackerError("Lecture de la progression AniList impossible")
        val media = response.getJSONObject("data").getJSONObject("Media")
        val entry = media.optJSONObject("mediaListEntry") ?: return@withContext null
        RemoteTrackerProgress(entry.getInt("progress"), entry.getString("status").let { if (it == "CURRENT") "WATCHING" else it })
    }

    override suspend fun library(account: dev.endlesssea.data.db.TrackerAccountEntity): List<RemoteLibraryEntry> = withContext(Dispatchers.IO) {
        val token = account.accessToken ?: throw TrackerError("Connectez AniList pour importer votre liste")
        val viewer = JSONObject(http.postJson(ENDPOINT, JSONObject().put("query", "query { Viewer { id } }").toString(), token))
        if (viewer.has("errors")) throw TrackerError("Compte AniList refusé ; reconnectez-vous")
        val userId = viewer.getJSONObject("data").getJSONObject("Viewer").getInt("id")
        val query = """query (${'$'}user: Int!, ${'$'}page: Int!) {
            Page(page: ${'$'}page, perPage: 50) {
                pageInfo { hasNextPage }
                mediaList(userId: ${'$'}user, type: ANIME) {
                    progress status media { id title { userPreferred romaji english } episodes seasonYear coverImage { large } }
                }
            }
        }""".trimIndent()
        val entries = mutableListOf<RemoteLibraryEntry>()
        for (page in 1..100) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val payload = JSONObject().put("query", query).put("variables", JSONObject().put("user", userId).put("page", page))
            val result = parseAniListPage(JSONObject(http.postJson(ENDPOINT, payload.toString(), token)))
            if (result.hasNext && result.entries.isEmpty()) throw TrackerError("Réponse de pagination AniList incohérente")
            entries += result.entries
            if (!result.hasNext) return@withContext entries.distinctBy { it.id }
        }
        throw TrackerError("Liste trop longue : import incomplet, ancien cache conservé")
    }

    override suspend fun details(account: dev.endlesssea.data.db.TrackerAccountEntity, remoteId: String): TrackerDetails? = withContext(Dispatchers.IO) {
        val query = """query (${'$'}id: Int!) { Media(id: ${'$'}id, type: ANIME) {
            title { userPreferred romaji } description(asHtml: false) bannerImage coverImage { extraLarge } seasonYear genres
        } }"""
        val payload = JSONObject().put("query",query).put("variables",JSONObject().put("id",remoteId.toInt()))
        val result = JSONObject(http.postJson(ENDPOINT,payload.toString(),account.accessToken))
        if (result.has("errors")) throw TrackerError("Métadonnées AniList indisponibles")
        val media = result.getJSONObject("data").getJSONObject("Media")
        fun text(key: String) = media.optString(key).takeUnless { it.isBlank() || it == "null" }
        val genres = media.optJSONArray("genres")
        TrackerDetails(posterUrl = media.optJSONObject("coverImage")?.optString("extraLarge"), bannerUrl = text("bannerImage"),
            synopsis = text("description"), year = media.optInt("seasonYear").takeIf { it > 0 },
            title = media.getJSONObject("title").optString("userPreferred").ifBlank { media.getJSONObject("title").getString("romaji") },
            genres = if (genres == null) emptyList() else (0 until genres.length()).map { genres.getString(it) })
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
        } else if (status == "WATCHING") "CURRENT" else status
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
 * pour le renouveler avec un client enregistré). La durée réelle est fournie
 * par le serveur OAuth ; un jeton collé seul ne permet pas de la connaître).
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

    override suspend fun readProgress(account: dev.endlesssea.data.db.TrackerAccountEntity, remoteId: String): RemoteTrackerProgress? = withContext(Dispatchers.IO) {
        val token = account.accessToken ?: throw TrackerError("Compte non connecté")
        require(remoteId.toLongOrNull() != null)
        val response = JSONObject(http.getJson("$API/anime/$remoteId?fields=my_list_status", token))
        require(response.has("id")) { "Réponse MAL invalide" }
        val entry = response.optJSONObject("my_list_status") ?: return@withContext null
        RemoteTrackerProgress(entry.getInt("num_episodes_watched"), when (entry.getString("status")) {
            "completed" -> "COMPLETED"
            "dropped" -> "DROPPED"
            "plan_to_watch" -> "PLANNING"
            "on_hold" -> "PAUSED"
            else -> "WATCHING"
        })
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
        } else when (status) {
            "COMPLETED" -> "completed"
            "DROPPED" -> "dropped"
            "PLANNING" -> "plan_to_watch"
            "PAUSED" -> "on_hold"
            else -> "watching"
        }
        val form = "num_watched_episodes=$progress&status=$effective"
        val request = Request.Builder()
            .url("$API/anime/$remoteId/my_list_status")
            .put(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "EndlessSea/0.23")
            .build()
        http.call(request)
        true
    }

    data class RefreshedToken(val accessToken: String, val refreshToken: String?, val expiresAt: Long)

    /** Renouvellement d'un client natif public, sans secret ; conserve les données du serveur. */
    suspend fun refresh(account: dev.endlesssea.data.db.TrackerAccountEntity): RefreshedToken? =
        withContext(Dispatchers.IO) {
            val clientId = account.clientId ?: return@withContext null
            val refresh = account.refreshToken ?: return@withContext null
            val form = "client_id=" + URLEncoder.encode(clientId, "UTF-8") + "&grant_type=refresh_token&refresh_token=" +
                URLEncoder.encode(refresh, "UTF-8")
            val request = Request.Builder()
                .url("https://myanimelist.net/v1/oauth2/token")
                .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            runCatching {
                val response = JSONObject(http.call(request))
                val token = response.optString("access_token").takeIf { it.isNotBlank() } ?: return@runCatching null
                val seconds = response.optLong("expires_in").coerceIn(0L, 365L * 24 * 3600)
                RefreshedToken(
                    accessToken = token,
                    refreshToken = response.optString("refresh_token").takeIf { it.isNotBlank() } ?: account.refreshToken,
                    expiresAt = if (seconds > 0L) System.currentTimeMillis() + seconds * 1000L else 0L,
                )
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
 * une application enregistrée avec une URI de retour correspondante. Aucun
 * secret client ne doit être embarqué dans cette application Android.
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

    override suspend fun readProgress(account: dev.endlesssea.data.db.TrackerAccountEntity, remoteId: String): RemoteTrackerProgress? = withContext(Dispatchers.IO) {
        val token = account.accessToken ?: throw TrackerError("Compte non connecté")
        require(remoteId.toLongOrNull() != null)
        val user = JSONObject(http.getJson("$API/users/whoami", token)).getLong("id")
        require(user > 0)
        val rates = JSONArray(http.getJson("$API/v2/user_rates?user_id=$user&target_id=$remoteId&target_type=Anime", token))
        val entry = (0 until rates.length()).map { rates.getJSONObject(it) }
            .firstOrNull { it.optString("target_id") == remoteId && it.optString("target_type") == "Anime" }
            ?: return@withContext null
        RemoteTrackerProgress(entry.getInt("episodes"), when (entry.getString("status")) {
            "completed" -> "COMPLETED"
            "dropped" -> "DROPPED"
            "planned" -> "PLANNING"
            "on_hold" -> "PAUSED"
            else -> "WATCHING"
        })
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
            status.equals("PAUSED", true) -> "on_hold"
            else -> "watching"
        }
        val user = JSONObject(http.getJson("$API/users/whoami", token)).optLong("id")
        if (user <= 0 || remoteId.toLongOrNull() == null) return@withContext false
        val rates = JSONArray(http.getJson("$API/v2/user_rates?user_id=$user&target_id=$remoteId&target_type=Anime", token))
        val rateId = (0 until rates.length()).mapNotNull { index -> rates.optJSONObject(index) }
            .firstOrNull { it.optString("target_id") == remoteId && it.optString("target_type") == "Anime" }
            ?.optLong("id")?.takeIf { it > 0 }
        val fields = JSONObject().put("episodes", progress).put("status", shikiStatus)
        if (rateId == null) fields.put("user_id", user).put("target_id", remoteId.toLong()).put("target_type", "Anime")
        val payload = JSONObject().put("user_rate", fields).toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(if (rateId != null) "$API/v2/user_rates/$rateId" else "$API/v2/user_rates")
            .method(if (rateId != null) "PATCH" else "POST", payload)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "EndlessSea/0.25")
            .build()
        http.call(request)
        true
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
    private fun JSONObject.text(key: String) = optString(key).takeUnless { it.isBlank() || it == "null" }


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
                title = r.text("name") ?: r.text("title") ?: return@mapNotNull null,
                totalEpisodes = r.optInt("number_of_episodes").takeIf { it > 0 },
                posterUrl = r.text("poster_path")
                    ?.let { IMAGE_BASE + it },
                year = (r.text("first_air_date") ?: r.text("release_date"))?.take(4)?.toIntOrNull(),
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
            posterUrl = root.text("poster_path")
                ?.let { IMAGE_BASE + it },
            bannerUrl = root.text("backdrop_path")
                ?.let { IMAGE_BASE + it },
            trailerUrl = trailer,
            synopsis = root.text("overview"),
            title = root.text("name") ?: root.text("title"),
            genres = root.optJSONArray("genres")?.let { genres ->
                (0 until genres.length()).mapNotNull { genres.optJSONObject(it)?.text("name") }
            }.orEmpty(),
            year = (root.text("first_air_date") ?: root.text("release_date"))?.take(4)?.toIntOrNull(),
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
        "ANILIST" to "Connexion par jeton obtenu via une application OAuth enregistrée sur AniList. " +
            "La connexion navigateur intégrée nécessite un client ID et une URI de retour enregistrée.",
        "MAL" to "Colle un access_token obtenu via OAuth2 PKCE avec un client natif enregistré sur MAL. " +
            "Aucun secret client n'est demandé ni stocké.",
        "SHIKIMORI" to "Colle un jeton autorisé par une application OAuth enregistrée. " +
            "L'échange nécessitant un secret doit se faire sur un serveur de confiance, pas dans l'app.",
        "TMDB" to "themoviedb.org/settings/api → clé gratuite (v3). Utilisée uniquement " +
            "pour compléter affiches et bandes-annonces manquantes.",
    )

    fun log(tag: String, message: String) = EsLog.e("Tracker", tag, message)
}
