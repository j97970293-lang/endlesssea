package dev.endlesssea.extension.archiveorg

import dev.endlesssea.extensions.api.EsExtension
import dev.endlesssea.extensions.api.EsRequest
import dev.endlesssea.extensions.api.ExtensionContext
import dev.endlesssea.extensions.api.error.SourceException
import dev.endlesssea.extensions.api.model.API_VERSION
import dev.endlesssea.extensions.api.model.Episode
import dev.endlesssea.extensions.api.model.ExtensionInfo
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
import dev.endlesssea.extensions.api.model.VideoLink
import dev.endlesssea.extensions.api.permission.ExtensionPermission
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Official demo provider: Internet Archive public-domain films.
 *
 * APIs used (official, stable, ToS-compliant):
 *  - search:   GET https://archive.org/advancedsearch.php?q=…&fl[]=identifier&fl[]=title&fl[]=year&rows=25&page=N&output=json
 *  - details:  GET https://archive.org/metadata/<identifier>
 *  - poster:   https://archive.org/services/img/<identifier>
 *  - streams:  https://archive.org/download/<identifier>/<file>
 */
class ArchiveOrgProvider(private val ctx: ExtensionContext) : EsExtension {

    override val info = ExtensionInfo(
        id = "dev.endlesssea.demo.archiveorg",
        name = "Internet Archive — Domaine public",
        version = 1, apiVersion = API_VERSION,
        languages = listOf("en", "fr"),
        types = setOf(MediaType.MOVIE),
        permissions = setOf(ExtensionPermission.INTERNET, ExtensionPermission.DOWNLOAD),
        author = "Endless Sea",
        description = "Films du domaine public / public-domain films (archive.org)",
    )

    override suspend fun getMainPage(request: MainPageRequest): PagedResult<SearchItem> =
        searchPublicDomain("", request.page)

    override suspend fun search(query: String, page: Int, filters: FilterSet): PagedResult<SearchItem> =
        searchPublicDomain(query, page)

    private suspend fun searchPublicDomain(query: String, page: Int): PagedResult<SearchItem> {
        val q = if (query.isBlank()) {
            "collection:(feature_films) AND mediatype:(movies)"
        } else {
            "(${URLEncoder.encode(query, "UTF-8")}) AND mediatype:(movies) AND collection:(opensource_movies OR feature_films)"
        }
        val url = "https://archive.org/advancedsearch.php?q=$q" +
            "&fl[]=identifier&fl[]=title&fl[]=year&fl[]=runtime" +
            "&rows=25&page=$page&sort[]=downloads+desc&output=json"
        val res = ctx.http.execute(EsRequest(url))
        if (res.code != 200) throw SourceException.SourceUnavailable(res.code)

        val response = JSONObject(res.body).getJSONObject("response")
        val docs = response.getJSONArray("docs")
        val numFound = response.getInt("numFound")
        val items = buildList {
            for (i in 0 until docs.length()) {
                val d = docs.getJSONObject(i)
                val id = d.getString("identifier")
                add(
                    SearchItem(
                        id = id,
                        title = d.optString("title", id),
                        url = "https://archive.org/metadata/$id",
                        posterUrl = "https://archive.org/services/img/$id",
                        type = MediaType.MOVIE,
                        year = d.optString("year").take(4).toIntOrNull(),
                    )
                )
            }
        }
        val hasNext = page * 25 < numFound
        if (items.isEmpty() && page == 1) throw SourceException.NoResults
        return PagedResult(items, page, hasNext)
    }

    override suspend fun load(url: String): MediaDetails {
        val res = ctx.http.execute(EsRequest(url))
        if (res.code != 200) throw SourceException.SourceUnavailable(res.code)
        val root = JSONObject(res.body)
        val meta = root.optJSONObject("metadata") ?: throw SourceException.ParseError("metadata missing")
        val id = meta.optString("identifier")
        if (id.isBlank()) throw SourceException.ParseError("identifier missing")

        val files = root.getJSONArray("files")
        val videoFiles = buildList {
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val name = f.optString("name")
                if (name.endsWith(".mp4", true) || name.endsWith(".mkv", true) || name.endsWith(".ogv", true)) {
                    val height = f.optString("height").toIntOrNull()
                        ?: f.optJSONObject("metadata")?.optString("height")?.toIntOrNull()
                    add(
                        VideoLink(
                            url = "https://archive.org/download/$id/$name",
                            streamType = StreamType.DIRECT_FILE,
                            quality = Quality.fromLabel(height?.toString()),
                            server = name.substringAfterLast('.')
                                .replaceFirstChar { it.uppercase() } +
                                " · " + formatMb(f.optLong("size")),
                        )
                    )
                }
            }
        }

        val episode = Episode(
            id = "$id:film",
            number = 1f,
            title = meta.optString("title", id),
            data = id,                                   // loadLinks() resolves files again
            durationMs = parseRuntime(meta.optString("runtime")),
        )

        return MediaDetails(
            id = id, url = url,
            title = meta.optString("title", id),
            synopsis = meta.optString("description").takeIf { it.isNotBlank() },
            posterUrl = "https://archive.org/services/img/$id",
            type = MediaType.MOVIE,
            year = meta.optString("year").take(4).toIntOrNull()
                ?: meta.optString("publicdate").take(4).toIntOrNull(),
            genres = meta.optJSONArray("subject")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            studios = listOfNotNull(meta.optString("creator").takeIf { it.isNotBlank() }),
            seasons = listOf(Season(1, null, listOf(episode))),
            servers = videoFiles.mapIndexed { i, _ ->
                dev.endlesssea.extensions.api.model.ServerRef("f$i", "Serveur ${i + 1}")
            },
            externalIds = mapOf("archiveorg" to id),
        )
    }

    override suspend fun loadLinks(data: LinkRequest): List<VideoLink> {
        // Reuse load(): metadata JSON already lists every file variant (spec §14 servers).
        val details = load("https://archive.org/metadata/${data.episode.data}")
        val res = ctx.http.execute(EsRequest(details.url))
        val files = JSONObject(res.body).getJSONArray("files")
        val id = data.episode.data
        return buildList {
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val name = f.optString("name")
                if (name.endsWith(".mp4", true) || name.endsWith(".mkv", true) || name.endsWith(".ogv", true)) {
                    add(
                        VideoLink(
                            url = "https://archive.org/download/$id/$name",
                            streamType = StreamType.DIRECT_FILE,
                            quality = Quality.fromLabel(f.optString("height", "")),
                            server = "Archive.org · ${name.substringAfterLast('.').uppercase()}",
                        )
                    )
                }
            }
            if (isEmpty()) throw SourceException.VideoUnavailable("no playable file on this item")
        }
    }

    private fun parseRuntime(raw: String): Long? =
        raw.substringBefore('.').toLongOrNull()?.times(1000)
            ?: Regex("(\\d+):(\\d+):(\\d+)").find(raw)
                ?.destructured?.let { (h, m, s) -> (h.toLong() * 3600 + m.toLong() * 60 + s.toLong()) * 1000 }

    private fun formatMb(sizeBytes: Long): String =
        if (sizeBytes >= 1L shl 30) "%.1f Go".format(sizeBytes / (1L shl 30).toDouble())
        else "%.0f Mo".format(sizeBytes / (1L shl 20).toDouble().coerceAtLeast(1.0))
}
