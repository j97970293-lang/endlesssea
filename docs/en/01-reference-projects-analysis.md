# 01 — Reference Projects Analysis

> 🇫🇷 **Résumé** : étude comparative des projets de référence exigée par le cahier des charges (§26) avant tout code. Pour chaque projet : licence, architecture d'extensions, méthodes de téléchargement, formats, stockage, bonnes idées à reprendre, problèmes connus et leçons pour Endless Sea.

*Research snapshot: October 2026. Licenses and architectures verified against the official repositories.*

---

## 1. Summary table

| Project | License | Extension architecture | Download engine | Player | Key idea to borrow |
|---|---|---|---|---|---|
| **Kotatsu** | GPL-3.0+ | Kotlin parsers; library `kotatsu-parsers`, sources also loadable as separate APKs | Built-in multi-threaded downloader | n/a (reader) | Clean parser contract + offline-first DB |
| **Mihon / Tachiyomi** | Apache-2.0 | Extensions = **separate APKs** compiled against a stub lib (`extension-lib` / `tachiyomix`); installed & updated via repos | Chapter downloader with queue | ExoPlayer ( forks ) | Repository-indexed extension updates; source factories per language |
| **Aniyomi** | Apache-2.0 (fork) | Same model as Mihon, anime sources | Episode queue | **mpv-android** | Tracker integrations (AniList/MAL…), per-episode status |
| **Cloudstream (CS3)** | GPL-3.0 | **Compiled Kotlin plugins (`.cs3` = DEX) loaded at runtime** from repos; `MainAPI` + `ExtractorApi` + `CloudflareKiller`/WebView helpers | Stream & file downloader, HLS extraction | ExoPlayer | The richest provider API model: search → load → loadLinks + host extractors |
| **Nuvio** | GPL-3.0 | **JavaScript provider modules** fetched from repo URLs (no app rebuild); Kotlin Multiplatform app | Stream downloads | libass-android subtitle stack | Providers as sandboxable scripts; subtitle rendering quality (libass) |
| **Stremio** | SDK MIT-ish / mixed | **Zero code execution**: addons are remote HTTP services (`manifest.json` + `/catalog /meta /stream /subtitles`) | Stream + cache | own engine | Addon *protocol* instead of code: maximal safety, language-agnostic |
| **AB Download Manager** | Apache-2.0 | n/a | **Own Kotlin engine**: multi-connection segmented HTTP, HLS parts, sparse/positioned writes, queues, speed limit, persistence | n/a | The blueprint for our `:downloader` module |
| **AnymeX** | open source | Dart extensions (Cloudstream-like API ported to Flutter) | Built-in | media_kit/mpv | CS3 concepts are portable across stacks |
| **AniZen** | open source | Dantotsu-style extensions (shared ecosystem) | Built-in | ExoPlayer | Light anime-first UX |

Sources: [Cloudstream repo](https://github.com/recloudstream/cloudstream) · [Cloudstream wiki](https://cloudstream.miraheze.org/wiki/CloudStream) · [kotatsu-parsers (GPL-3.0)](https://github.com/YakaTeam/kotatsu-parsers) · [Mihon / tachiyomix (Apache-2.0)](https://github.com/mihonapp/tachiyomix) · [Aniyomi](https://github.com/aniyomiorg/aniyomi) · [Stremio addon SDK & protocol](https://github.com/Stremio/stremio-addon-sdk) · [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile) · [nuvio-providers](https://github.com/yoruix/nuvio-providers) · [AB Download Manager (Apache-2.0)](https://github.com/amir1376/ab-download-manager) · [ABDM engine deep-dive](https://codewiki.google/github.com/amir1376/ab-download-manager) · [gowaru-nuvio-providers (FR)](https://github.com/Gowaru/gowaru-nuvio-providers)

Directory/community resources studied: [miyomi.app](https://miyomi.app/), [everythingmoe.com](https://everythingmoe.com/), [wotaku.wiki](https://wotaku.wiki/) — used as landscape maps of which features users actually value (trackers, debrid, subtitle fidelity, repos).

---

## 2. Per-project findings (spec §26: what to take, problems, limits)

### 2.1 Kotatsu (GPL-3.0+)
- **Tech**: Kotlin, MVVM, Room, Coil; parsers as a separate JVM/Android library (`kotatsu-parsers`), sources also packaged as APKs; minSdk 26+ on recent versions.
- **Good ideas**: strict separation *parsers ↔ app*; every source implements a tiny interface (search/pages/details); strong offline model (favorites/history/downloads in Room); batch download with retry.
- **Problems/limits**: image-oriented (pages), not video; parser APK ecosystem fragmented across forks; no subtitle notion.
- **For Endless Sea**: the *contract-first* approach and Room offline model. We cannot reuse code directly without GPL obligations — fine, we are GPL-3.0 anyway; still, our API is designed **from scratch** (video-first).

### 2.2 Mihon / Tachiyomi (Apache-2.0)
- **Tech**: Kotlin, Compose (migrated), Room/SQLDelight history; **extensions are separate signed APKs** using a stub library; extension repos publish a JSON/index update feed; source factories group languages.
- **Good ideas**: extension-as-APK with the system installer managing versions; per-source enable/NSFW flags; deep backup format (`.tachibk`).
- **Problems/limits**: legal takedown pressure killed the official extension list (a warning about shipping sources in-repo!); APK extensions require `REQUEST_INSTALL_PACKAGES` distribution channel outside Play; extension APKs share the app process trust model (code is not sandboxed).
- **For Endless Sea**: repo-driven updates + explicit "no sources bundled" policy. We avoid auto-installing APKs (no `REQUEST_INSTALL_PACKAGES`) — plugins load as DEX inside our sandbox-ish loader instead.

### 2.3 Aniyomi (Apache-2.0)
- **Tech**: Mihon fork for anime; player built on **mpv-android**; torrent add-ons exist in forks; trackers (AniList, MAL, Kitsu…).
- **Good ideas**: per-episode watch states, mpv's unmatched format/subtitle support, tracker sync.
- **Problems/limits**: mpv native binaries ≈ +20–40 MB APK; heavier on low-RAM devices (our ≤ low-end constraint).
- **For Endless Sea**: Media3 as default (small, official), `PlayerEngine` interface keeps an mpv backend possible later (doc 07).

### 2.4 Cloudstream 3 (GPL-3.0)
- **Tech**: Kotlin; **runtime-loaded compiled plugins** (`.cs3`, DEX) — no app reinstall per source; `MainAPI` contract: `mainPage / search / load / loadLinks`; `ExtractorApi` per host (upstream helper layers like NiceHttp/CloudflareKiller patterns); repos = JSON index; WebView fallback for JS/anti-bot pages; TV support.
- **Good ideas**: this is the closest model to our spec §2–§4 and §14 (multi-server `loadLinks`, quality parsing, subtitle links, no bundled providers). Cloudflare/anti-bot flow: detect challenge → show WebView → reuse cookies.
- **Problems/limits**: plugin code runs unsandboxed in-process (trust = repos + community); quality of plugins varies; GPL means derivative app code must be GPL (acceptable for us).
- **For Endless Sea**: our `EsExtension` interface mirrors this proven call-graph with our own naming and error model; **no CS3 code copied** — API law, not code reuse: we re-implement, respecting GPL anyway.

### 2.5 Nuvio (GPL-3.0)
- **Tech**: Kotlin Multiplatform (androidApp/composeApp/iosApp), **providers are JavaScript modules** downloaded from repo URLs; bundled `libass-android` for subtitle fidelity.
- **Good ideas**: scriptable providers are easier to write for non-Android devs and can't be shipped as native code; libass-grade ASS/SSA rendering.
- **Problems/limits**: JS engine (QuickJS/Rhino-class) is slower and harder to debug than Kotlin plugins; no true sandbox guarantees either.
- **For Endless Sea**: we support **two** extension flavors (compiled Kotlin + declarative JSON). A JS flavor is documented as a future third flavor; ASS styling is on the player roadmap.

### 2.6 Stremio (addon SDK/protocol)
- **Tech**: addons are **remote services**: `manifest.json` + REST-ish resources `catalog`, `meta`, `stream`, `subtitles`, `addon_catalog`; the client executes no addon code; CORS-enabled HTTP; big third-party ecosystem.
- **Good ideas**: safest possible model (nothing executable on-device), trivial to develop/publish an addon; the manifest's `types`/`idPrefixes` filtering is elegant.
- **Problems/limits**: needs always-on remote servers; scraping logic can't run client-side; heavier latency; not suitable for site-scraping providers.
- **For Endless Sea**: our **declarative JSON providers** borrow the manifest philosophy (`resources`, `types`), and a future "remote addon" flavor can adopt a Stremio-compatible wire format.

### 2.7 AB Download Manager (Apache-2.0)
- **Tech**: Kotlin multiplatform; own engine (`downloader/core`): `HttpDownloaderClient` abstraction (OkHttp impl with Range headers, auth), **multi-part segmented downloads**, `SegmentedDownloadDestination` with **positioned writes** (no merge step), HLS part downloading (segments resolved from playlists, relative URLs, per-part speed limits), queue/priority/concurrency control, persistence, error recovery, global speed limit; sparse file handling.
- **Good ideas**: *the* reference architecture for a modern download engine; Apache-2.0 is compatible with our GPL-3.0 (reused concepts; code we write ourselves; if we ever lift code, attribution + license notice in module).
- **Problems/limits** (for us): desktop-first threading assumptions; Android needs FG-service lifecycle, wakelocks, storage SAF and `DownloadManager`-free background policy — we design for that from scratch.
- **For Endless Sea**: our `:downloader` module follows this blueprint (doc 06).

### 2.8 AnymeX / AniZen (Flutter & Kotlin anime apps)
- **Good ideas**: AniList-first metadata (rich titles/relations), CS3-style extension ports proving the provider contract is stack-agnostic; light "anime-first" home UX.
- **For Endless Sea**: metadata normalization layer in `core` (extensions may expose AniList/TMDB ids).

---

## 3. Download methods observed across the ecosystem (spec §5 inputs)

| Method | Where seen ending | Endless Sea support |
|---|---|---|
| Direct file HTTP(S), Range multi-connection | ABDM, IDM, 1DM+ | ✅ SegmentEngine (4–8 parts, adaptive) |
| HLS (m3u8 → .ts parts, variant pick) | Cloudstream, ABDM, Nuvio | ✅ Media3 HLS parser + sequential part writer |
| DASH (MPD) | Nuvio (ExoPlayer), CS3 | ✅ playback; DASH download currently rejected by DownloadManager |
| Torrent (debrid/torrent add-ons) | Aniyomi forks, Stremio | 🔜 extension-provided `infoHash` links, external handler |
| WebView-resolved links (JS challenges) | CS3 CloudflareKiller | ✅ Captcha/anti-bot flow (doc 05, §CAPTCHA) |
| External subtitles sidecar (.srt/.vtt/.ass) | CS3, Stremio, Nuvio | ✅ first-class (doc 06 §subtitles) |

## 4. Storage mechanisms observed
- Mihon/Kotatsu: app-specific dirs + SAF user-picked folders; backups as files.
- CS3: internal storage default, optional custom paths.
- ABDM: sparse/positioned file writes, path-length safety, temp `.part` naming.
- **Endless Sea decision**: scoped storage + SAF, positioned writes on a single `.part` (internal) with per-part fallback on exFAT/FAT SD cards; full detail in doc 08.

## 5. Feature opportunities (improvements over references)

1. **Unified server picker** (CS3-style `loadLinks`) + user-ranked *auto-server* rules (spec §14).
2. **One-click download sheet**: server × quality matrix before enqueue (spec §5) — most rivals bury this in settings.
3. **Per-extension cookie jar persistence** resumed across anti-bot checks (CS3 does it implicitly; we make it explicit+inspectable).
4. **Declarative JSON providers** for simple sites — lower the contribution bar below "write Kotlin" (neither CS3 nor Mihon offer this).
5. **Offline library as a first-class citizen** (Kotatsu-grade) bound to the queue engine (ABDM-grade) — the rare combination that is our raison d'être.
6. **Local-only recommendations** (genre affinity from watch history) — no account, no tracking (spec §10).
