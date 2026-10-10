# 05 — Extension API (developer reference)

> 🇫🇷 **Résumé** : référence complète de l'API que les développeurs tiers implémentent (point 6 du cahier des charges). Le code source miroir est dans le module `:extensions-api`. Version d'API : **1**.

The API is a published artifact: `dev.endlesssea:extensions-api:1.0.0` (GPL-3.0). Extensions compile against it as `compileOnly`.

A walkthrough with a Gradle template and a JSON example is in [extension-dev-guide.md](extension-dev-guide.md).

---

## 1. Contract overview

```kotlin
interface EsExtension {
    val info: ExtensionInfo

    /** Home rows: "Recently added", "Trending", ... (spec §10) */
    suspend fun getMainPage(request: MainPageRequest): PagedResult<SearchItem>

    /** Full-text + filtered search (spec §12) */
    suspend fun search(query: String, page: Int, filters: FilterSet): PagedResult<SearchItem>

    /** Details sheet of a media: synopsis, seasons, episodes, servers... (spec §13) */
    suspend fun load(url: String): MediaDetails

    /** Video links for one episode/movie, possibly per server (spec §14) */
    suspend fun loadLinks(data: LinkRequest): List<VideoLink>

    /** Optional: shared host resolvers (upstream-style extractor concept) */
    fun extractors(): List<ExtractorApi> = emptyList()

    /** Optional: per-extension settings screen descriptor */
    suspend fun settings(): List<ExtensionSetting> = emptyList()
}
```

Everything is `suspend` (coroutines). The loader dispatches extension calls on a dedicated `Dispatchers.IO`-style context with a per-extension `ExtensionHttpClient` injected via `ExtensionContext`.

## 2. Core DTOs

```kotlin
enum class MediaType { ANIME, MOVIE, SERIES, OVA, ONA, SPECIAL, OTHER }
enum class MediaStatus { ONGOING, COMPLETED, UPCOMING, HIATUS, CANCELLED, UNKNOWN }
enum class Quality { Q360, Q480, Q720, Q1080, Q1440, Q4K, UNKNOWN }           // spec §5
enum class AudioLang(val iso: String) { VOSTFR("vostfr"), VF("vf"), VO("vo"), MULTI("multi"), OTHER("?") }
enum class StreamType { DIRECT_FILE, HLS, DASH, TORRENT, EMBED }

data class SearchItem(
    val id: String,            // opaque to the app, stable per extension
    val title: String,
    val altTitles: List<String> = emptyList(),
    val url: String,           // details page key passed to load()
    val posterUrl: String? = null,
    val type: MediaType,
    val year: Int? = null,
)

data class PagedResult<T>(val items: List<T>, val page: Int, val hasNextPage: Boolean)

data class Season(val number: Int, val name: String?, val episodes: List<Episode>)

data class Episode(
    val id: String, val number: Float, val season: Int? = null,
    val title: String? = null, val thumbnailUrl: String? = null,
    val durationMs: Long? = null, val data: String,   // opaque payload for loadLinks()
)

data class ServerRef(val id: String, val name: String)  // "Serveur 1..N", spec §14

data class MediaDetails(
    val id: String, val url: String,
    val title: String, val altTitles: List<String> = emptyList(),
    val synopsis: String? = null, val posterUrl: String? = null, val bannerUrl: String? = null,
    val type: MediaType, val year: Int? = null, val status: MediaStatus = MediaStatus.UNKNOWN,
    val genres: List<String> = emptyList(), val studios: List<String> = emptyList(),
    val episodeCount: Int? = null, val durationMin: Int? = null,
    val seasons: List<Season> = emptyList(),         // movies → one pseudo-season
    val servers: List<ServerRef> = emptyList(),      // known servers if listed on the page
    val languages: List<AudioLang> = emptyList(),    // VF/VOSTFR/MULTI…
    val externalIds: Map<String, String> = emptyMap()// "anilist", "tmdb", "mal", "imdb"
)

data class VideoLink(
    val url: String,
    val streamType: StreamType,
    val quality: Quality = Quality.UNKNOWN,
    val server: String,                       // display label, spec §14
    val headers: Map<String, String> = emptyMap(),  // Referer/User-Agent/cookies if required
    val subtitles: List<SubtitleTrack> = emptyList(),
    val audioLang: AudioLang = AudioLang.OTHER,
)

data class SubtitleTrack(
    val url: String, val lang: String,        // BCP-47 ("fr", "en"…)
    val label: String = lang,
    val format: SubtitleFormat,               // SRT / ASS / SSA / VTT
)

enum class SubtitleFormat { SRT, ASS, SSA, VTT, UNKNOWN }

data class FilterSet(
    val genres: List<String> = emptyList(), val years: IntRange? = null,
    val types: Set<MediaType> = emptySet(), val languages: Set<String> = emptySet(),
    val status: MediaStatus? = null, val qualities: Set<Quality> = emptySet(),
    val extra: Map<String, String> = emptyMap(),   // extension-specific
)
```

## 3. Errors — all failures are typed (spec §22)

```kotlin
sealed class SourceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkError(cause: Throwable)              // "Connexion impossible"
    class SourceUnavailable(val httpCode: Int?)       // "Source indisponible"
    object NoResults                                  // "Aucun résultat"
    class CaptchaRequired(val url: String, val headers: Map<String,String>) // → WebView flow
    class VideoUnavailable(val reason: String?)       // "Vidéo non disponible"
    object SubtitleNotFound                           // "Sous-titre introuvable"
    class AuthRequired(val loginUrl: String?)         // legitimate auth only
    class RateLimited(val retryAfterSec: Int?)
    class ParseError(val what: String, cause: Throwable? = null)
}
```

UI maps every subtype to a FR/EN explanation + action (retry, open verification, pick another server).

## 4. CAPTCHA / anti-bot flow (spec §3)

1. Extension throws `SourceException.CaptchaRequired(pageUrl, headers)` (detected via status 403/503 + challenge markers).
2. App shows **CaptchaActivity**: system WebView → the user performs the verification **manually** (interactive checks only; we never auto-solve third-party CAPTCHAs).
3. Cookies/User-Agent gathered by the WebView are written into that extension's persistent `CookieJar`.
4. The original call is retried automatically; sessions persist until cookies expire (spec §3.5).
5. If the user cancels, the UI surfaces "Verification required" with a retry action.

Non-interactive legitimate mechanisms (delays, JS redirects solvable without user input) may be handled inside the extension itself.

## 5. Pagination

Every listing returns `PagedResult<T>`. `page` starts at 1. `hasNextPage = false` ends infinite scroll. Extensions that need cursor tokens stash them inside `extra` of `FilterSet` or their own state — the contract stays page-oriented for simplicity (spec §2 pagination).

## 6. Servers & qualities (spec §14)

- `loadLinks()` returns **every** playable variant; the app groups by `server`, then `quality`.
- *Auto-server* mode: user-defined rules (ordered server names/preferred quality); the player walks the list until one resolves.
- Quality values are normalized (`Quality.Q480`…) but the raw label is kept in `server`/link display.

## 7. ExtractorApi (host resolvers)

```kotlin
abstract class ExtractorApi {
    abstract val name: String               // e.g. "GenericMp4Host"
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean
    open val hosts: List<String> = listOf(mainUrl)

    /** @return video links (+ subtitles) found at [url]. Never throw raw; wrap in SourceException. */
    abstract suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleTrack) -> Unit = {},
        callback: (VideoLink) -> Unit = {},
    ): List<VideoLink>
}
```

Extensions register their extractors via `EsExtension.extractors()`; the loader tries every extractor whose `hosts` match an embedded/player iframe URL before handing results back.

## 8. The ExtensionContext (injected by the app)

```kotlin
class ExtensionContext(
    val http: ExtensionHttpClient,  // OkHttp wrapper w/ cookies, UA, per-extension logs
    val filesDir: java.io.File,     // sandboxed private dir (FILES permission)
    val locale: String,             // app locale for localized content
)
```

`ExtensionHttpClient` enforces the extension's declared permissions, records request stats, applies polite defaults (timeouts 15 s, 2 retries w/ backoff) and shares nothing across extensions.

## 9. Minimal extension skeleton (Kotlin flavor)

```kotlin
class ArchiveOrgProvider(ctx: ExtensionContext) : EsExtension {
    override val info = ExtensionInfo(
        id = "dev.endlesssea.demo.archiveorg",
        name = "Internet Archive — Public Domain",
        version = 1, apiVersion = 1,
        languages = listOf("en", "fr"),
        types = setOf(MediaType.MOVIE),
        permissions = setOf(ExtensionPermission.INTERNET),
    )

    override suspend fun search(query: String, page: Int, filters: FilterSet): PagedResult<SearchItem> { … }
    override suspend fun load(url: String): MediaDetails { … }
    override suspend fun loadLinks(data: LinkRequest): List<VideoLink> { … }
    override suspend fun getMainPage(request: MainPageRequest): PagedResult<SearchItem> { … }
}
```

Full working example: `demo-extensions/archive-org/` in this repository. Declarative example: `demo-extensions/static-json/`.

## 10. Rules for extension authors (enforced by review for the official repo)

1. Only sources you are **allowed** to use (ToS/robots/licenses) — the official repo accepts exclusively legal sources.
2. Never return raw stack traces — map failures to `SourceException`.
3. Respect rate limits; use the shared `ExtensionHttpClient` so users can inspect traffic.
4. No ads, no trackers, no coin miners, no dynamic code downloads beyond the packaged DEX.
5. Version bumps must be monotonic (`version` + 1) with changelog in the repo index.
