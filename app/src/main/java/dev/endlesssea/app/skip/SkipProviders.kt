package dev.endlesssea.app.skip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §megaskip — fournisseurs de segments communautaires, adaptés au projet :
 *
 *  1. **TheIntroDB** (principal) — `api.theintrodb.org/v2/media` (tmdb_id / tvdb_id / imdb_id).
 *  2. **IntroDB** (repli) — `api.introdb.app/intro` (imdb_id / tmdb_id).
 *  3. **AniSkip** (anime) — `api.aniskip.com/v2/skip-times` (mal_id + durée obligatoire).
 *
 * Les identifiants manquants sont résolus automatiquement :
 *  - IMDb → MAL via **AniZip** (`api.ani.zip/mappings`) ;
 *  - Titre → MAL via **Jikan** (`api.jikan.moe/v4/anime`) — indispensable car les
 *    extensions Endless Sea ne fournissent pas toujours d'identifiants externes.
 *
 * Chaque appel est isolé (timeout court, `runCatching`) : une base indisponible
 * ne bloque jamais la lecture, elle est simplement ignorée.
 */
@Singleton
class SkipProviders @Inject constructor(http: OkHttpClient) {

    private val client: OkHttpClient = http.newBuilder()
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------------ appels

    suspend fun theIntroDb(target: SkipTarget): List<SkipSegment> {
        val id = when {
            !target.tmdbId.isNullOrBlank() -> "tmdb_id=${target.tmdbId}"
            !target.tvdbId.isNullOrBlank() -> "tvdb_id=${target.tvdbId}"
            !target.imdbId.isNullOrBlank() -> "imdb_id=${target.imdbId}"
            else -> return emptyList()
        }
        val url = buildString {
            append("https://api.theintrodb.org/v2/media?").append(id)
            if (target.season > 0) append("&season=${target.season}")
            if (target.episode > 0) append("&episode=${target.episode}")
            if (target.durationMs > 0) append("&duration_ms=${target.durationMs}")
        }
        return parse(get(url), target.durationMs, "theintrodb")
    }

    suspend fun introDb(target: SkipTarget): List<SkipSegment> {
        val id = when {
            !target.imdbId.isNullOrBlank() -> "imdb_id=${target.imdbId}"
            !target.tmdbId.isNullOrBlank() -> "tmdb_id=${target.tmdbId}"
            else -> return emptyList()
        }
        val url = "https://api.introdb.app/intro?$id" +
            "&season=${target.season.coerceAtLeast(1)}&episode=${target.episode.coerceAtLeast(1)}"
        return parse(get(url), target.durationMs, "introdb")
    }

    suspend fun aniSkip(target: SkipTarget): List<SkipSegment> {
        val mal = resolveMalId(target) ?: return emptyList()
        val episode = target.episode.coerceAtLeast(1)
        // AniSkip EXIGE la durée de l'épisode (sinon 400/404) : on envoie ce qu'on a.
        val length = if (target.durationMs > 0) target.durationMs / 1000.0 else 0.0
        val url = "https://api.aniskip.com/v2/skip-times/$mal/$episode" +
            "?types=op&types=ed&types=recap&episodeLength=" + "%.3f".format(length)
        return parse(get(url), target.durationMs, "aniskip")
    }

    /** MAL id : direct, sinon AniZip (IMDb) puis Jikan (titre). */
    suspend fun resolveMalId(target: SkipTarget): String? {
        target.malId?.takeIf { it.isNotBlank() }?.let { return it }
        target.imdbId?.takeIf { it.isNotBlank() }?.let { imdb ->
            get("https://api.ani.zip/mappings?imdb_id=$imdb")
                ?.optJSONObject("mappings")?.optInt("mal_id", 0)
                ?.takeIf { it > 0 }?.let { return it.toString() }
        }
        val title = target.title?.takeIf { it.isNotBlank() } ?: return null
        val clean = cleanTitle(title)
        if (clean.isBlank()) return null
        val url = "https://api.jikan.moe/v4/anime?limit=1&q=" +
            URLEncoder.encode(clean, "UTF-8")
        val data = get(url)?.optJSONArray("data") ?: return null
        if (data.length() == 0) return null
        return data.optJSONObject(0)?.optInt("mal_id", 0)?.takeIf { it > 0 }?.toString()
    }

    // ------------------------------------------------------------------ outillage

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("(?i)\\.[a-z0-9]{2,4}$"), " ")
        .replace(Regex("(?i)\\b(s\\d{1,2}[ ._-]*e\\d{1,3}|\\d{1,2}x\\d{1,3})\\b"), " ")
        .replace(Regex("(?i)\\b(1080p|720p|2160p|480p|4k|web-?dl|webrip|bluray|hdtv|x264|x265|hevc|aac|multi)\\b"), " ")
        .replace(Regex("[_.]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private suspend fun get(url: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "EndlessSea/0.22 (+android)")
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@runCatching null
                JSONObject(body)
            }
        }.getOrNull()
    }

    /**
     * Parse défensivement les TROIS formes possibles d'une réponse :
     *  - TheIntroDB / IntroDB : `{"intro":[{"start_ms":null,"end_ms":90000}], "credits":[…]}` ;
     *  - AniSkip : `{"found":true,"results":[{"interval":{"startTime":…,"endTime":…},"skipType":"op"}]}` ;
     *  - IntroDB (variante) : `{"segments":[{"type":"intro","start":12.5,"end":75}]}`.
     */
    private fun parse(json: JSONObject?, durationMs: Long, provider: String): List<SkipSegment> {
        json ?: return emptyList()
        val out = mutableListOf<SkipSegment>()

        fun add(type: SkipType, startMs: Long, endMs: Long) {
            if (endMs <= startMs) return
            out += SkipSegment(type, startMs.coerceAtLeast(0L), endMs, provider)
        }

        // Forme 1 : tableaux par type, bornes `start_ms` / `end_ms` (null = bornes du média).
        listOf(
            "intro" to SkipType.INTRO,
            "op" to SkipType.INTRO,
            "recap" to SkipType.RECAP,
            "credits" to SkipType.CREDITS,
            "ed" to SkipType.CREDITS,
            "outro" to SkipType.CREDITS,
            "preview" to SkipType.PREVIEW,
        ).forEach { (key, type) ->
            val array = json.optJSONArray(key) ?: return@forEach
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val start = if (entry.isNull("start_ms")) 0L
                else entry.optLong("start_ms", 0L)
                val end = if (entry.isNull("end_ms")) durationMs
                else entry.optLong("end_ms", 0L)
                add(type, start, end)
            }
        }

        // Forme 2 : `results[]` avec `interval` en SECONDES (AniSkip, IntroDB v2).
        json.optJSONArray("results")?.let { results ->
            for (i in 0 until results.length()) {
                val entry = results.optJSONObject(i) ?: continue
                val interval = entry.optJSONObject("interval") ?: continue
                val start = (interval.optDouble("startTime", -1.0) * 1000.0).toLong()
                val end = (interval.optDouble("endTime", -1.0) * 1000.0).toLong()
                val type = typeOf(entry.optString("skipType")) ?: continue
                add(type, start, end)
            }
        }

        // Forme 3 : `segments[]` générique.
        json.optJSONArray("segments")?.let { segments ->
            for (i in 0 until segments.length()) {
                val entry = segments.optJSONObject(i) ?: continue
                val type = typeOf(entry.optString("type").ifBlank { entry.optString("skipType") }) ?: continue
                val start: Long
                val end: Long
                if (entry.has("start_ms") || entry.has("end_ms")) {
                    start = if (entry.isNull("start_ms")) 0L else entry.optLong("start_ms", 0L)
                    end = if (entry.isNull("end_ms")) durationMs else entry.optLong("end_ms", 0L)
                } else {
                    start = (entry.optDouble("start", 0.0) * 1000.0).toLong()
                    end = (entry.optDouble("end", 0.0) * 1000.0).toLong()
                }
                add(type, start, end)
            }
        }

        return out.distinctBy { it.type to it.startMs }
    }

    private fun typeOf(raw: String?): SkipType? = when (raw?.lowercase()?.trim()) {
        "intro", "op", "opening", "mixed-op" -> SkipType.INTRO
        "recap", "recapitulation" -> SkipType.RECAP
        "credits", "outro", "ed", "ending", "mixed-ed" -> SkipType.CREDITS
        "preview", "next" -> SkipType.PREVIEW
        else -> null
    }
}
