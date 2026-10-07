package dev.endlesssea.app.tracking

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * §suivi — MyAnimeList (OAuth2 + PKCE).
 *
 * MAL ne permet pas de « coller un jeton » : il faut un code d'autorisation
 * échangé contre des jetons. L'application refait donc le parcours complet :
 *  1. l'utilisateur déclare une application sur https://myanimelist.net/apiconfig
 *     avec l'URL de redirection affichée par l'application, et colle son Client ID ;
 *  2. l'application ouvre la page d'autorisation (le code_verifier PKCE est
 *     généré ici et gardé localement) ;
 *  3. MAL redirige vers l'URL choisie avec `?code=…` — l'utilisateur colle le
 *     code, l'application l'échange contre un jeton d'accès + jeton de
 *     rafraîchissement.
 */
class MyAnimeListTracker(base: OkHttpClient) : Tracker {

    private val http = trackerClient(base)
    override val id = TrackerId.MAL

    override suspend fun verify(creds: TrackerCredentials): TrackerAccount? =
        withContext(Dispatchers.IO) {
            val text = runCatching {
                http.get(API + "/users/@me", auth(creds))
            }.getOrNull() ?: return@withContext null
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext null
            TrackerAccount(
                id = TrackerId.MAL,
                userName = o.optString("name").ifBlank { "MyAnimeList" },
                avatarUrl = o.optString("picture").takeIf { it.isNotBlank() },
            )
        }

    override suspend fun search(creds: TrackerCredentials, title: String): List<TrackerMediaHit> =
        withContext(Dispatchers.IO) {
            val url = API + "/anime?q=" + URLEncoder.encode(title, "UTF-8") +
                "&limit=6&fields=id,title,num_episodes,start_season,main_picture"
            val text = runCatching { http.get(url, auth(creds)) }.getOrNull()
                ?: return@withContext emptyList()
            val data = runCatching { JSONObject(text).optJSONArray("data") }.getOrNull()
                ?: return@withContext emptyList()
            (0 until data.length()).mapNotNull { i ->
                val node = data.optJSONObject(i)?.optJSONObject("node") ?: return@mapNotNull null
                TrackerMediaHit(
                    remoteId = node.optInt("id").toString(),
                    title = node.optString("title").ifBlank { "Fiche MAL" },
                    episodes = node.optInt("num_episodes").takeIf { it > 0 },
                    posterUrl = node.optJSONObject("main_picture")?.optString("medium"),
                    year = node.optJSONObject("start_season")?.optInt("year")?.takeIf { it > 0 },
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
        val form = mutableMapOf(
            "num_watched_episodes" to progress.toString(),
            "status" to malStatus(status, progress, totalEpisodes),
        )
        runCatching {
            http.postForm(API + "/anime/$remoteId/my_list_status", form, auth(creds))
        }.isSuccess
    }

    override fun authorizeUrl(creds: TrackerCredentials, redirectUri: String): String {
        if (creds.clientId.isBlank() || creds.codeVerifier.isBlank()) return ""
        return "https://myanimelist.net/v1/oauth2/authorize?response_type=code" +
            "&client_id=${creds.clientId}" +
            "&code_challenge=${pkceChallenge(creds.codeVerifier)}" +
            "&code_challenge_method=S256" +
            "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
    }

    override suspend fun exchangeCode(
        creds: TrackerCredentials,
        code: String,
        redirectUri: String,
    ): TrackerCredentials? = withContext(Dispatchers.IO) {
        val form = mapOf(
            "client_id" to creds.clientId,
            "grant_type" to "authorization_code",
            "code" to code.trim(),
            "code_verifier" to creds.codeVerifier,
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
            codeVerifier = "",
            connectedAt = System.currentTimeMillis(),
        )
    }

    /** Renouvelle le jeton d'accès (MAL expire en ~1 h). */
    suspend fun refresh(creds: TrackerCredentials): TrackerCredentials? = withContext(Dispatchers.IO) {
        if (creds.refreshToken.isBlank()) return@withContext null
        val form = mapOf(
            "client_id" to creds.clientId,
            "grant_type" to "refresh_token",
            "refresh_token" to creds.refreshToken,
        )
        val text = runCatching { http.postForm(TOKEN, form, emptyMap()) }.getOrNull()
            ?: return@withContext null
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext null
        val token = o.optString("access_token")
        if (token.isBlank()) return@withContext null
        creds.copy(
            token = token,
            refreshToken = o.optString("refresh_token").ifBlank { creds.refreshToken },
            connectedAt = System.currentTimeMillis(),
        )
    }

    private fun auth(creds: TrackerCredentials): Map<String, String> =
        mapOf("Authorization" to "Bearer ${creds.token}")

    private fun malStatus(status: TrackerStatus, progress: Int, total: Int?): String = when {
        total != null && progress >= total -> "completed"
        else -> when (status) {
            TrackerStatus.WATCHING -> "watching"
            TrackerStatus.COMPLETED -> "completed"
            TrackerStatus.PLANNING -> "plan_to_watch"
            TrackerStatus.DROPPED -> "dropped"
        }
    }

    companion object {
        private const val API = "https://api.myanimelist.net/v2"
        private const val TOKEN = "https://myanimelist.net/v1/oauth2/token"

        /** URL de redirection conseillée (à déclarer telle quelle chez MAL). */
        const val SUGGESTED_REDIRECT = "https://localhost/endlesssea"

        /** code_verifier aléatoire (RFC 7636) — 64 caractères base64url. */
        fun newCodeVerifier(): String {
            val bytes = ByteArray(48)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
                .take(64)
        }

        fun pkceChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
            return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        }
    }
}
