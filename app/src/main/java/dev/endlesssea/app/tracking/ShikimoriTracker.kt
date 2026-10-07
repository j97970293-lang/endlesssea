package dev.endlesssea.app.tracking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder

/**
 * §suivi — Shikimori (OAuth2 « authorization code », pas de PKCE).
 *
 * Utile pour les utilisateurs hispanophones/russes et pour les fiches que MAL
 * référence mal. L'utilisateur déclare une application sur
 * https://shikimori.one/oauth/applications, colle l'ID + le secret, ouvre la
 * page d'autorisation puis colle le code reçu.
 */
class ShikimoriTracker(base: OkHttpClient) : Tracker {

    private val http = trackerClient(base)
    override val id = TrackerId.SHIKIMORI

    override suspend fun verify(creds: TrackerCredentials): TrackerAccount? =
        withContext(Dispatchers.IO) {
            val text = runCatching {
                http.get("$API/users/whoami", auth(creds))
            }.getOrNull() ?: return@withContext null
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext null
            TrackerAccount(
                id = TrackerId.SHIKIMORI,
                userName = o.optString("nickname").ifBlank { "Shikimori" },
                avatarUrl = o.optString("image").takeIf { it.isNotBlank() }
                    ?.let { if (it.startsWith("http")) it else "https://shikimori.one$it" },
            )
        }

    override suspend fun search(creds: TrackerCredentials, title: String): List<TrackerMediaHit> =
        withContext(Dispatchers.IO) {
            val url = "$API/animes?search=" + URLEncoder.encode(title, "UTF-8") +
                "&limit=6&kind=tv&order=popularity"
            val text = runCatching { http.get(url, auth(creds)) }.getOrNull()
                ?: return@withContext emptyList()
            val arr = runCatching { org.json.JSONArray(text) }.getOrNull()
                ?: return@withContext emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val poster = o.optJSONObject("image")?.optString("original").orEmpty()
                TrackerMediaHit(
                    remoteId = o.optInt("id").toString(),
                    title = o.optString("russian").ifBlank { o.optString("name") },
                    episodes = o.optInt("episodes").takeIf { it > 0 },
                    posterUrl = if (poster.isBlank()) null
                    else if (poster.startsWith("http")) poster else "https://shikimori.one$poster",
                    year = o.optString("aired_on").take(4).toIntOrNull(),
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
        // Shikimori n'accepte qu'un « user_rate » par anime : on crée puis on
        // met à jour si l'entrée existe déjà (HTTP 422 → PUT).
        val rate = JSONObject()
            .put("user_rate", JSONObject()
                .put("target_id", remoteId)
                .put("target_type", "Anime")
                .put("episodes", progress)
                .put("status", shikiStatus(status, progress, totalEpisodes)))
            .toString()
        val created = runCatching { http.postJson("$API/v2/user_rates", rate, auth(creds) + jsonHeader) }
        if (created.isSuccess) return@withContext true
        // entrée existante : on cherche son identifiant puis on la met à jour
        val existing = runCatching {
            http.get("$API/v2/user_rates?target_id=$remoteId&target_type=Anime", auth(creds))
        }.getOrNull() ?: return@withContext false
        val id = runCatching { org.json.JSONArray(existing).optJSONObject(0)?.optInt("id") }
            .getOrNull() ?: return@withContext false
        runCatching {
            http.postJson("$API/v2/user_rates/$id", rate, auth(creds) + jsonHeader)
        }.isSuccess
    }

    override fun authorizeUrl(creds: TrackerCredentials, redirectUri: String): String {
        if (creds.clientId.isBlank()) return ""
        return "https://shikimori.one/oauth/authorize?client_id=${creds.clientId}" +
            "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") +
            "&response_type=code&scope="
    }

    override suspend fun exchangeCode(
        creds: TrackerCredentials,
        code: String,
        redirectUri: String,
    ): TrackerCredentials? = withContext(Dispatchers.IO) {
        val form = mapOf(
            "grant_type" to "authorization_code",
            "client_id" to creds.clientId,
            "client_secret" to creds.clientSecret,
            "code" to code.trim(),
            "redirect_uri" to redirectUri,
        )
        val text = runCatching { http.postForm(TOKEN, form, emptyMap()) }.getOrNull()
            ?: return@withContext null
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext null
        val token = o.optString("access_token")
        if (token.isBlank()) return@withContext null
        creds.copy(
            token = token,
            refreshToken = o.optString("refresh_token"),
            connectedAt = System.currentTimeMillis(),
        )
    }

    private fun auth(creds: TrackerCredentials): Map<String, String> =
        mapOf("Authorization" to "Bearer ${creds.token}")

    private val jsonHeader = mapOf("Content-Type" to "application/json")

    private fun shikiStatus(status: TrackerStatus, progress: Int, total: Int?): String = when {
        total != null && progress >= total -> "completed"
        else -> when (status) {
            TrackerStatus.WATCHING -> "watching"
            TrackerStatus.COMPLETED -> "completed"
            TrackerStatus.PLANNING -> "planned"
            TrackerStatus.DROPPED -> "dropped"
        }
    }

    companion object {
        private const val API = "https://shikimori.one/api"
        private const val TOKEN = "https://shikimori.one/oauth/token"
        const val SUGGESTED_REDIRECT = "https://localhost/endlesssea"
    }
}
