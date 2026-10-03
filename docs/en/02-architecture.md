# 02 — Architecture

> 🇫🇷 **Résumé** : architecture multi-modules **MVVM + flux de données unidirectionnel** (Kotlin/Compose/Hilt/Room). Le cœur ne connaît aucune source : tout passe par le contrat `extensions-api`. Chaque moteur (téléchargement, lecteur, chargeur) est remplaçable sans réécrire l'application — l'exigence du §25 du cahier des charges.

---

## 1. Module graph

```
                 ┌───────────────┐
                 │     :app      │  Compose UI, navigation, ViewModels, Hilt wiring
                 └──┬───┬───┬───┬┘
                    │   │   │   │
      ┌─────────────┘   │   │   └─────────────┐
      ▼                 ▼   ▼                 ▼
 :extensions-loader  :downloader  :player   :data
 (repos, DEX load,   (queue,      (Media3   (Room,
  JSON engine,        segments,    wrapper,  entities,
  captcha WebView)    FG service)  subtit.)  DAOs, repos)
      │                 │           │         │
      └────────┬────────┴─────┬─────┴─────────┘
               ▼              ▼
        :extensions-api     :core
        (EsExtension, DTOs, (models, errors,
         manifest, captcha)  net, utils)
```

Rules:
- `:extensions-api` and `:core` depend on **nothing** project-internal (they are published artifacts for third-party extension developers).
- `:app` never calls OkHttp directly for content — only through `:extensions-loader`.
- Engines expose Kotlin APIs + `StateFlow`s; the UI binds to them via ViewModels.

## 2. Layers & patterns

- **Presentation**: Jetpack Compose, Material 3, single-activity + Navigation-Compose. One `ViewModel` per screen exposing `StateFlow<UiState>`; events up, state down (UDF). No AI-driven/generated UI — classic, fast, boring (spec §10).
- **Domain**: use-case classes inside each engine module (`EnqueueDownload`, `SearchAllExtensions`, `ResolveEpisodeLinks`).
- **Data**: Room database + repositories in `:data`; DataStore for settings; Coil image cache; HTTP cache per extension.
- **DI**: Hilt. Modules define `@Module` bindings for their public interfaces.
- **Concurrency**: Kotlin coroutines + Flow everywhere; structured concurrency with module-scoped `SupervisorJob`s.

## 3. Extension system overview (details in docs 04 & 05)

```
Repository JSON ──► RepoManager (add/list/update)
        │
        ▼
ExtensionLoader ──► verify SHA-256 ──► install .esx (DEX+assets) into private dir
        │
        ▼
PathClassLoader ──► instantiate declared entry class : EsExtension
        │
        ▼
ExtensionRegistry ──► enabled providers ──► UI (search/home/details/links)
        │
        └── captcha/CookieJar per extension, error mapping (SourceException)
```

Three flavors, one contract:
1. **Compiled Kotlin plugins** (`.esx` — APK-shaped container with `classes.dex` + `assets/extension.json`). Full power (jsoup, custom logic, extractors).
2. **Declarative JSON providers** — interpreted by `JsonProviderEngine` (search URLs, selectors, link regexes). No code execution; perfect for simple sites.
3. **Remote HTTP addons** (future) — Stremio-compatible wire format; zero on-device code.

## 4. Download pipeline (details in doc 06)

```
UI "Télécharger" ─► DownloadManager.enqueue(task)
                       │
        ┌──────────────┼───────────────┐
        ▼              ▼               ▼
     probe()      queue/priority   FG service (dataSync)
   (HEAD meta)        │            + notification
        │             ▼
        │      SegmentEngine: N Range connections
        │      single .part positioned writes
        │      progress persisted to Room
        │             │
        └──► HLS? playlist parse → sequential .ts parts
                       │
                verify size/checksum
                       ▼
        rename .part → final path + sidecar subtitles (.srt/.ass/.vtt)
```

## 5. Playback pipeline (details in doc 07)

```
Episode click ─► Extension.loadLinks() ─► server picker (manual/auto rules)
        │                 │ on CaptchaRequired → CaptchaActivity (WebView) → retry
        ▼
   EsPlayer (Media3): url + headers + subtitle tracks
        │
   position persisted every 5 s ─► HistoryRepository (resume/continue-watching)
```

## 6. Search aggregation (spec §12)

```
query ─► for each enabled extension: async search(page, filters) ─►
merge+dedupe by (normalized title, year, type) ─► grouped UiState
errors isolated per extension (Source unavailable / No results …)
```

## 7. Replaceability guarantees (spec §25)

| Component | Swappable via | Public interface |
|---|---|---|
| Player | implement `PlayerEngine` | `player/EsPlayer.kt` header |
| Download engine | implement `DownloadEngine` | `downloader/DownloadManager.kt` |
| Extension runtime | new flavor in loader | `EsExtension` (`:extensions-api`) |
| DB | Room is hidden behind repositories | `data/db/*Dao` |
| Image loading | Coil singleton bound in `:app` DI | — |

## 8. Non-functional targets
- Cold start < 1.5 s on 2 GB RAM devices; home screen never blocks on network (skeletons + cached rows).
- Downloads: crash-safe (`.part` + Room checkpoints), battery-friendly (throttled progress writes, wake locks only during active segments).
- APK size target ≤ 12 MB (Media3 core only, no mpv by default).
```