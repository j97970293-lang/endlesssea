package dev.endlesssea.app.ui.details

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

/**
 * Bande-annonce sans compte : AniList, Kitsu et Jikan sont des catalogues publics.
 * TMDB reste un complément quand une clé est déjà enregistrée.
 */
internal object TrailerLookup {
    private val JSON = "application/json".toMediaType()
    private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

    fun youtubeWatch(id: String): String? =
        id.trim().takeIf { it.matches(YOUTUBE_ID) }?.let { "https://www.youtube.com/watch?v=$it" }

    /** N'accepte qu'un identifiant YouTube ou une URL https d'un hébergeur déjà lu par l'app. */
    fun normalize(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return null
        if (!value.startsWith("https://")) return youtubeWatch(value)
        val host = runCatching { URI(value).host?.lowercase() }.getOrNull() ?: return null
        val known = host == "youtube.com" || host == "www.youtube.com" || host == "m.youtube.com" ||
            host == "youtu.be" || host == "vimeo.com" || host == "www.vimeo.com" ||
            host == "player.vimeo.com" || host == "dailymotion.com" || host == "www.dailymotion.com" ||
            host == "dai.ly"
        val direct = value.substringAfterLast('.').substringBefore('?').lowercase() in
            setOf("mp4", "webm", "m3u8", "mkv", "mpd")
        return value.takeIf { known || direct }
    }

    fun fromAniList(body: String): String? = runCatching {
        val root = JSONObject(body)
        val media = root.optJSONObject("data")?.optJSONObject("Media")
            ?: root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media")?.optJSONObject(0)
            ?: return null
        val trailer = media.optJSONObject("trailer") ?: return null
        val site = trailer.optString("site")
        val id = trailer.optString("id")
        if (site.equals("youtube", true) || site.isBlank()) normalize(id) else normalize(id)
    }.getOrNull()

    fun fromKitsu(body: String): String? = runCatching {
        val first = JSONObject(body).optJSONArray("data")?.optJSONObject(0) ?: return null
        normalize(first.optJSONObject("attributes")?.optString("youtubeVideoId"))
    }.getOrNull()

    fun fromJikan(body: String): String? = runCatching {
        val first = JSONObject(body).optJSONArray("data")?.optJSONObject(0) ?: return null
        val trailer = first.optJSONObject("trailer") ?: return null
        normalize(trailer.optString("youtube_id").ifBlank { trailer.optString("url") })
    }.getOrNull()

    fun aniListPayload(title: String): String {
        val query = """
            query (${'$'}search: String) {
              Page(perPage: 1) {
                media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                  trailer { id site }
                }
              }
            }
        """.trimIndent()
        return JSONObject().put("query", query)
            .put("variables", JSONObject().put("search", title))
            .toString()
    }

    suspend fun find(http: OkHttpClient, title: String): String? = withContext(Dispatchers.IO) {
        val query = title.trim()
        if (query.length < 2) return@withContext null
        val encoded = URLEncoder.encode(query, "UTF-8")
        runCatching {
            fromAniList(post(http, "https://graphql.anilist.co", aniListPayload(query)))
        }.getOrNull()
            ?: runCatching {
                fromKitsu(get(http, "https://kitsu.io/api/edge/anime?filter[text]=$encoded&page[limit]=1"))
            }.getOrNull()
            ?: runCatching {
                fromJikan(get(http, "https://api.jikan.moe/v4/anime?q=$encoded&limit=1"))
            }.getOrNull()
    }

    private fun get(http: OkHttpClient, url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", "EndlessSea/0.32 (+https://github.com/j97970293-lang/endlesssea)")
            .header("Accept", "application/vnd.api+json, application/json")
            .get().build()
        return execute(http, request)
    }

    private fun post(http: OkHttpClient, url: String, json: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", "EndlessSea/0.32 (+https://github.com/j97970293-lang/endlesssea)")
            .header("Accept", "application/json")
            .post(json.toRequestBody(JSON))
            .build()
        return execute(http, request)
    }

    private fun execute(http: OkHttpClient, request: Request): String =
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("http ${response.code}")
            response.body?.string().orEmpty()
        }
}
