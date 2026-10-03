package dev.endlesssea.extensions.loader

import dev.endlesssea.extensions.api.EsExtension
import dev.endlesssea.extensions.api.ExtensionHttpClient
import dev.endlesssea.extensions.api.EsRequest
import dev.endlesssea.extensions.api.error.SourceException
import dev.endlesssea.extensions.api.model.Episode
import dev.endlesssea.extensions.api.model.FilterSet
import dev.endlesssea.extensions.api.model.LinkRequest
import dev.endlesssea.extensions.api.model.MainPageRequest
import dev.endlesssea.extensions.api.model.MediaDetails
import dev.endlesssea.extensions.api.model.MediaType
import dev.endlesssea.extensions.api.model.PagedResult
import dev.endlesssea.extensions.api.model.Quality
import dev.endlesssea.extensions.api.model.SearchItem
import dev.endlesssea.extensions.api.model.Season
import dev.endlesssea.extensions.api.model.StreamType
import dev.endlesssea.extensions.api.model.SubtitleFormat
import dev.endlesssea.extensions.api.model.SubtitleTrack
import dev.endlesssea.extensions.api.model.VideoLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Interprets DECLARATIVE JSON providers — no code execution (docs/en/04 §2.2).
 * Rules = URL templates ({base},{query},{page}) + jsoup selectors + regex link resolvers.
 */
class JsonProviderEngine(
    private val http: ExtensionHttpClient,
) {

    @Serializable
    data class Rules(
        val manifest: Manifest,
        val search: SearchRule? = null,
        val details: DetailsRule? = null,
        val links: LinksRule? = null,
        val catalog: List<CatalogEntry> = emptyList(),   // fully static catalogs (demo)
    )

    @Serializable
    data class Manifest(
        val id: String, val name: String, val version: Int,
        @SerialName("apiVersion") val apiVersion: Int = 1,
        val languages: List<String> = emptyList(),
        val types: List<String> = emptyList(),
        val baseUrl: String = "",
        val permissions: List<String> = listOf("INTERNET"),
    )

    @Serializable
    data class SearchRule(
        val url: String, val items: String, val title: String,
        val url2: String? = null, @SerialName("url") val itemUrl: String? = null,
        val poster: String? = null, val type: String? = null,
    )

    @Serializable
    data class DetailsRule(
        val synopsis: String? = null, val genres: String? = null,
        val banner: String? = null, val episodes: String? = null,
        val episodeUrl: String? = null, val episodeTitle: String? = null,
    )

    @Serializable
    data class LinksRule(
        val servers: String? = null,
        val direct: String? = null,           // selector resolving to the video URL
        val regex: String? = null,            // or group(1) of regex on the HTML
        val subtitles: String? = null,        // selector "track" → @src + @srclang
    )

    @Serializable
    data class CatalogEntry(
        val id: String, val title: String, val type: String, val year: Int? = null,
        val posterUrl: String? = null, val bannerUrl: String? = null,
        val synopsis: String? = null, val genres: List<String> = emptyList(),
        val streams: List<StreamEntry> = emptyList(),
    )

    @Serializable
    data class StreamEntry(
        val url: String, val server: String = "Serveur 1", val quality: String? = null,
        @SerialName("streamType") val streamType: String = "DIRECT_FILE",
        val subtitles: List<SubEntry> = emptyList(),
    )

    @Serializable
    data class SubEntry(val url: String, val lang: String, val format: String = "VTT")

    private val json = Json { ignoreUnknownKeys = true }

    /** Builds a runtime [EsExtension] from a rules JSON document. */
    fun fromJson(raw: String): EsExtension {
        val rules = json.decodeFromString<Rules>(raw)
        return DeclarativeExtension(rules, http)
    }

    private class DeclarativeExtension(
        private val rules: Rules,
        private val http: ExtensionHttpClient,
    ) : EsExtension {

        override val info = dev.endlesssea.extensions.api.model.ExtensionInfo(
            id = rules.manifest.id,
            name = rules.manifest.name,
            version = rules.manifest.version,
            apiVersion = rules.manifest.apiVersion,
            languages = rules.manifest.languages,
            types = rules.manifest.types.mapNotNull { runCatching { MediaType.valueOf(it) }.getOrNull() }.toSet(),
            permissions = rules.manifest.permissions.mapNotNull {
                runCatching { dev.endlesssea.extensions.api.permission.ExtensionPermission.valueOf(it) }.getOrNull()
            }.toSet(),
        )

        override suspend fun getMainPage(request: MainPageRequest): PagedResult<SearchItem> =
            PagedResult(rules.catalog.map { it.toSearchItem() }, 1, hasNextPage = false)

        override suspend fun search(query: String, page: Int, filters: FilterSet): PagedResult<SearchItem> {
            val rule = rules.search ?: return PagedResult(
                rules.catalog.filter { it.title.contains(query, true) }.map { it.toSearchItem() },
                page, hasNextPage = false,
            )
            val url = fill(rule.url, mapOf("query" to query, "page" to page.toString()))
            val doc = Jsoup.parse(get(url).body, rules.manifest.baseUrl)
            val items = doc.select(rule.items).map { el ->
                SearchItem(
                    id = el.absUrlOrAttr(rule.itemUrl) ?: el.text(),
                    title = el.selectFirst(rule.title)?.text() ?: return@map null,
                    url = el.absUrlOrAttr(rule.itemUrl) ?: "",
                    posterUrl = rule.poster?.let { el.selectFirst(it)?.absUrlOrAttr("@src") },
                    type = runCatching { MediaType.valueOf(rule.type ?: "OTHER") }.getOrDefault(MediaType.OTHER),
                )
            }.filterNotNull()
            if (items.isEmpty() && page == 1 && rules.catalog.isEmpty()) throw SourceException.NoResults
            return PagedResult(items, page, hasNextPage = items.isNotEmpty())
        }

        override suspend fun load(url: String): MediaDetails {
            val static = rules.catalog.firstOrNull { it.id == url || it.title == url }
            if (static != null) return static.toDetails()
            val rule = rules.details ?: throw SourceException.ParseError("no details rule")
            val doc = Jsoup.parse(get(url).body, rules.manifest.baseUrl)
            val episodes = rule.episodes?.let { sel ->
                doc.select(sel).mapIndexed { i, el ->
                    Episode(
                        id = "$url#e$i",
                        number = (i + 1).toFloat(),
                        title = rule.episodeTitle?.let { el.selectFirst(it)?.text() } ?: el.text(),
                        data = el.absUrlOrAttr(rule.episodeUrl) ?: el.absUrl("href"),
                    )
                }
            } ?: emptyList()
            return MediaDetails(
                id = url, url = url,
                title = doc.title().ifBlank { url },
                synopsis = rule.synopsis?.let { doc.selectFirst(it)?.text() },
                bannerUrl = rule.banner?.let { doc.selectFirst(it)?.absUrl("src") },
                type = info.types.firstOrNull() ?: MediaType.OTHER,
                genres = rule.genres?.let { doc.select(it).eachText().map(String::trim) } ?: emptyList(),
                seasons = if (episodes.isEmpty()) emptyList else listOf(Season(1, "Saison 1", episodes)),
            )
        }

        override suspend fun loadLinks(data: LinkRequest): List<VideoLink> {
            val static = rules.catalog.firstOrNull { it.id == data.mediaId || it.id == data.episode.data }
            if (static != null) {
                return static.streams.map { it.toVideoLink() }
            }
            val rule = rules.links ?: throw SourceException.VideoUnavailable("no links rule")
            val page = get(data.episode.data)
            val videoUrl = when {
                rule.regex != null -> Regex(rule.regex).find(page.body)?.groupValues?.get(1)
                rule.direct != null -> Jsoup.parse(page.body, page.finalUrl)
                    .selectFirst(rule.direct)?.absUrlOrAttr("@src")
                else -> null
            } ?: throw SourceException.VideoUnavailable("no stream resolved")
            val subs = rule.subtitles?.let { sel ->
                Jsoup.parse(page.body, page.finalUrl).select(sel).map { el ->
                    SubtitleTrack(
                        url = el.absUrl("src"),
                        lang = el.attr("srclang").ifBlank { "und" },
                        format = SubtitleFormat.VTT,
                    )
                }
            } ?: emptyList()
            return listOf(
                VideoLink(
                    url = videoUrl,
                    streamType = if (videoUrl.endsWith(".m3u8")) StreamType.HLS else StreamType.DIRECT_FILE,
                    quality = Quality.UNKNOWN,
                    server = info.name,
                    subtitles = subs,
                )
            )
        }

        // ------------------------------------------------- helpers

        private suspend fun get(url: String) = http.execute(EsRequest(url)).also {
            if (it.code == 403 || it.code == 503) {
                throw SourceException.CaptchaRequired(it.finalUrl)
            }
            if (it.code !in 200..299) throw SourceException.SourceUnavailable(it.code)
        }

        private fun fill(template: String, tokens: Map<String, String>): String {
            var out = template.replace("{base}", rules.manifest.baseUrl)
            tokens.forEach { (k, v) -> out = out.replace("{$k}", java.net.URLEncoder.encode(v, "UTF-8")) }
            return out
        }

        private fun Element.absUrlOrAttr(rule: String?): String? = when {
            rule == null -> null
            rule.startsWith("@") -> absUrl(rule.removePrefix("@"))
            else -> selectFirst(rule)?.let { it.absUrlOrAttr("@href") ?: it.absUrl("href") }
        }

        private fun CatalogEntry.toSearchItem() = SearchItem(
            id = id, title = title, url = id, posterUrl = posterUrl,
            type = runCatching { MediaType.valueOf(type) }.getOrDefault(MediaType.OTHER), year = year,
        )

        private fun CatalogEntry.toDetails() = MediaDetails(
            id = id, url = id, title = title, posterUrl = posterUrl, bannerUrl = bannerUrl,
            synopsis = synopsis, genres = genres, year = year,
            type = runCatching { MediaType.valueOf(type) }.getOrDefault(MediaType.OTHER),
            seasons = listOf(Season(1, null, listOf(Episode(id = "$id:main", number = 1f, title = title, data = id)))),
        )

        private fun StreamEntry.toVideoLink() = VideoLink(
            url = url, server = server,
            quality = Quality.fromLabel(quality),
            streamType = runCatching { StreamType.valueOf(streamType) }.getOrDefault(StreamType.DIRECT_FILE),
            subtitles = subtitles.map {
                SubtitleTrack(it.url, it.lang, format = runCatching { SubtitleFormat.valueOf(it.format) }
                    .getOrDefault(SubtitleFormat.VTT))
            },
        )
    }
}
