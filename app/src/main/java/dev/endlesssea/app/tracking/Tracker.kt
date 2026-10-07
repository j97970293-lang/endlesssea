package dev.endlesssea.app.tracking

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * §suivi (conversation 11) — un service de suivi gratuit derrière une interface
 * unique : vérification du compte, recherche de titre, mise à jour de la
 * progression. Chaque implémentation ne parle QUE son propre protocole ; le
 * reste de l'application (fiche, lecteur, Paramètres) ne connaît que ce
 * contrat — ajouter un service = ajouter une classe.
 */
interface Tracker {
    val id: TrackerId

    /** Vérifie les identifiants et renvoie le pseudo du compte (null = refusé). */
    suspend fun verify(creds: TrackerCredentials): TrackerAccount?

    /** Recherche un titre (rattachement manuel d'une fiche). */
    suspend fun search(creds: TrackerCredentials, title: String): List<TrackerMediaHit>

    /** Écrit la progression sur le service. */
    suspend fun updateProgress(
        creds: TrackerCredentials,
        remoteId: String,
        progress: Int,
        totalEpisodes: Int?,
        status: TrackerStatus,
    ): Boolean

    /**
     * URL d'autorisation à ouvrir dans le navigateur (OAuth). Vide pour TMDB,
     * qui n'utilise qu'une clé d'API.
     */
    fun authorizeUrl(creds: TrackerCredentials, redirectUri: String): String = ""

    /**
     * Échange un code d'autorisation contre des jetons (OAuth « authorization
     * code »). Null quand le service ne fonctionne pas ainsi.
     */
    suspend fun exchangeCode(
        creds: TrackerCredentials,
        code: String,
        redirectUri: String,
    ): TrackerCredentials? = null
}

/** Erreur réseau/HTTP remontée telle quelle à l'utilisateur (message en français). */
class TrackerException(message: String) : Exception(message)

internal val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/** Client dédié : les services de suivi sont lents ou hors ligne, on ne bloque pas. */
internal fun trackerClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(25, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

internal fun OkHttpClient.postJson(url: String, json: String, headers: Map<String, String>): String {
    val req = Request.Builder()
        .url(url)
        .post(json.toRequestBody(JSON_MEDIA))
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .build()
    return exec(req)
}

internal fun OkHttpClient.postForm(url: String, form: Map<String, String>, headers: Map<String, String>): String {
    val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
    val req = Request.Builder()
        .url(url)
        .post(body)
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .build()
    return exec(req)
}

internal fun OkHttpClient.get(url: String, headers: Map<String, String>): String {
    val req = Request.Builder().url(url)
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .get()
        .build()
    return exec(req)
}

private fun OkHttpClient.exec(req: Request): String = newCall(req).execute().use { res ->
    val text = res.body?.string().orEmpty()
    if (res.code == 401 || res.code == 403) {
        throw TrackerException("Identifiants refusés (HTTP ${res.code}) — vérifie le jeton ou la clé.")
    }
    if (res.code == 429) {
        throw TrackerException("Trop de requêtes (HTTP 429) — réessaie dans une minute.")
    }
    if (res.code !in 200..299) {
        throw TrackerException("Réponse inattendue du service (HTTP ${res.code}).")
    }
    text
}

internal fun jsonArray(text: String, key: String): JSONArray {
    val obj = runCatching { JSONObject(text) }.getOrNull() ?: return JSONArray()
    return obj.optJSONArray(key) ?: obj.optJSONObject("data")?.optJSONArray(key) ?: JSONArray()
}

/** Extrait le titre principal d'un objet AniList (`title { romaji english native }`). */
internal fun anilistTitle(o: JSONObject): String {
    val t = o.optJSONObject("title") ?: return ""
    return listOf("romaji", "english", "native")
        .firstNotNullOfOrNull { t.optString(it).takeIf { s -> s.isNotBlank() } }
        .orEmpty()
}
