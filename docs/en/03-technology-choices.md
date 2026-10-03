# 03 — Technology Choices & Justification

> 🇫🇷 **Résumé** : choix techniques justifiés (point 4 du cahier des charges) : Kotlin 2.1 + Compose Material 3, Hilt, Room, OkHttp, Media3/ExoPlayer, Coil, WorkManager, kotlinx.serialization. `minSdk 26 / target 35`. Catalogue de versions unique dans `gradle/libs.versions.toml`.

| Area | Choice | Version | Why (justification) |
|---|---|---|---|
| Language | **Kotlin** | 2.1.0 | Ecosystem standard for all reference projects (Kotatsu, CS3, ABDM); coroutines/Flow are the backbone of engines. |
| UI toolkit | **Jetpack Compose + Material 3** | BOM 2024.12.01 | Modern, declarative, fast on low-end (no XML inflation); Material 3 dynamic theming (light/dark spec §24); TV-compatible focus model later. |
| Architecture | **MVVM + UDF, multi-module** | — | Proven by Mihon/Kotatsu; module isolation = replaceability (spec §25). |
| DI | **Hilt** | 2.52 | Standard, compile-time safe, works with ViewModels + WorkManager. |
| DB | **Room** (+ Flow) | 2.6.1 | Offline-first library/history/downloads; Kotatsu/ABDM-style persistence; kapt→KSP. |
| Settings | **DataStore Preferences** | 1.1.1 | Typed, async, replaces SharedPreferences. |
| HTTP | **OkHttp** | 4.12.0 | Range downloads, interceptors (cookies, retries, logging), per-extension clients; battle-tested by every reference project. |
| HTML parsing (extensions) | **jsoup** | 1.18.1 | The de-facto scraper toolkit of the extension world (kotatsu-parsers, CS3 docs recommend it). |
| JSON | **kotlinx.serialization** | 1.7.3 | Reflection-free, small, KSP-generated — fast on low-end; used for manifests, repo index, DTOs. |
| Player | **Media3 / ExoPlayer** | 1.5.1 | Official, HLS/DASH built-in, subtitle styling API, low RAM footprint; mpv-android (+20–40 MB native) kept as future backend behind `PlayerEngine`. |
| Images | **Coil** | 2.7.0 | Coroutine-native, small, memory/disk cache; used by Mihon/Kotatsu. |
| Background work | **Foreground Service (+ WorkManager for maintenance)** | WM 2.10.0 | Downloads need a user-visible FG service (`dataSync`); WorkManager only for update checks/library sync. |
| Desugaring | `coreLibraryDesugaring` | 2.1.4 | `java.time` on API 26+ (spec §15). |
| Rich subtitles (roadmap) | **libass-android** | future | Nuvio proves ASS/SSA fidelity matters to anime users; plug-in subtitle renderer interface keeps Media3 default. |
| Build | **Gradle KTS + version catalog** | AGP 8.7.3 | `libs.versions.toml` = single source of truth; KSP (no kapt slowdowns); config-cache on. |
| Tests | JUnit4, Truth, Turbine, MockWebServer, Compose UI Test | see catalog | Full matrix in doc 12. |

## Explicitly rejected / postponed
- **Flutter/React Native** (AnymeX, older Nuvio): extension ecosystem lives in Kotlin/Java; native perf on low-end devices; single-platform focus (priorité Android).
- **mpv-android as default player**: best subtitle fidelity but native size/memory cost; offered later as optional backend.
- **JS runtime for extensions (QuickJS)**: Nuvio's approach is attractive for sandboxing, but debugging/perf are worse; Kotlin plugins + declarative JSON cover the spectrum. Remote-HTTP addons (Stremio model) may join as the safest third flavor.
- **SQLDelight**: Room wins on ecosystem familiarity and ksp tooling here.
- **`DownloadManager` system service**: too limited (no multi-connection, weak resume semantics, opaque progress) — we ship our own engine (ABDM blueprint).
