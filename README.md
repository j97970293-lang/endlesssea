<div align="center">

# 🌊 Endless Sea

**Plateforme Android open source — anime, films & séries : streaming, téléchargement avancé et bibliothèque locale, pilotée par des extensions.**

*An open-source Android platform for anime, movies & series — streaming, advanced downloading and a local library, powered by extensions.*

[![Android CI](https://github.com/j97970293-lang/endlesssea/actions/workflows/android-ci.yml/badge.svg)](https://github.com/j97970293-lang/endlesssea/actions/workflows/android-ci.yml)

Android 8.0+ (API 26, testé jusqu'à Android 15) • Kotlin • Jetpack Compose • GPL-3.0 • version **0.25.0**

</div>

---

## 🇫🇷 Français

### Qu'est-ce qu'Endless Sea ?

Endless Sea est une application Android moderne et légère inspirée des meilleures idées de **Kotatsu, Mihon/Aniyomi, Cloudstream, Nuvio, Stremio et AB Download Manager/IDM**, conçue comme une **plateforme extensible** :

> **L'application ne contient aucune source en dur.** L'utilisateur installe les extensions/providers qu'il souhaite depuis des dépôts. Le dépôt principal ne dépend d'aucune liste fixe de sites.

- 🔌 **Système d'extensions** indépendant (plugins compilés `.esx`, providers déclaratifs JSON), dépôts multiples, permissions et signatures vérifiées
- ⬇️ **Gestionnaire de téléchargement puissant** : multi-connexions segmentées, pause/reprise, file d'attente, arrière-plan, HLS/DASH, sous-titres en fichiers séparés, reprise après redémarrage
- 🔗 **Suivi des épisodes vus** : AniList, MyAnimeList, Shikimori (rattachement manuel + marquage automatique à 90 %) et TMDB pour les affiches et bandes-annonces manquantes — identifiants stockés sur l'appareil, aucun compte requis
- ▶️ **Lecteur moderne** (Media3/ExoPlayer) : **8 thèmes complets** (Endless Sea, Netflix, Crunchyroll, YouTube, VLC, Plex, Apple TV+, Gaming — chacun sa mise en page), vitesse 0,25×–4× (pitch corrigé), pistes audio, **sous-titres affichés et décalables ±0,5 s**, **boost audio jusqu'à 200 %**, upscaling par niveaux (OFF/AUTO/PERFORMANCE/QUALITY) avec garde thermique, gestes complets (double appui, glisser luminosité/volume — inversable), zoom au pincement 1×–3×, verrouillage, reprise de position, surimpression de statistiques
- ⏭️ **Megaskip** : saut d'intro/récap/générique/aperçu alimenté par **TheIntroDB**, **IntroDB** et **AniSkip** (identifiants résolus automatiquement via AniZip/Jikan), **cache hors-ligne** en base, boutons de saut personnalisés (« +85 s », « Opening », « Filler ») et réglages fins par type de segment
- 📚 **Bibliothèque locale hors-ligne** : anime, films, séries, OVA, ONA, catégories et genres personnalisables, filtres par emplacement (interne / carte SD) et onglets « En cours / Terminés »
- 📂 **Dossiers vidéo façon Aniyomi** : `Série/cover.jpg` + `details.json` (éditable depuis l'app, écrit dans le dossier) + `episodes.json` (titres d'épisodes), tri par numéro d'épisode
- 🔎 **Entretien des téléchargements** : vérification d'intégrité SHA-256, limite de bande passante, purge automatique des fichiers anciens et des temporaires orphelins
- 📥 **Téléchargements dans la bibliothèque** : un épisode téléchargé — même isolé — forme sa propre « série » (badge, nombre d'épisodes, emplacement) et se lit hors-ligne
- 🔍 **Recherche multi-extensions** agrégée avec filtres
- 🛡️ **CAPTCHA/anti-bot** : écran de vérification WebView déclenché quand le site l'autorise, cookies conservés
- 📱 Android 8.0+ (API 26 → 15), stockage interne ou carte SD (SAF)

### État des lieux & audit

Un audit vivant, par composant, est maintenu dans [`docs/fr/AUDIT.md`](docs/fr/AUDIT.md) (compilation, mise à jour, WebView, images d'extensions, téléchargements, erreurs, Android 8, DNS — + fondations Phase 2).

### Avertissement légal

Endless Sea est un **logiciel neutre**, distribué sans aucune source de contenu pirate. Les extensions de démonstration fournies utilisent uniquement des sources légales et libres (Internet Archive — domaine public, films Blender Foundation — Creative Commons, fichiers d'exemple). **L'utilisateur est seul responsable des extensions tierces qu'il installe** et doit respecter les lois de son pays ainsi que les conditions d'utilisation des sources.

### Arborescence complète du dépôt

```
endless-sea/
├── README.md                        ← ce fichier (FR + EN)
├── LICENSE                          ← GPL-3.0
├── CONTRIBUTING.md                  ← guide de contribution
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml           ← catalogue de versions (source unique de vérité)
├── .github/
│   └── workflows/android-ci.yml     ← CI : build + tests unitaires
│
├── docs/                            ← documentation complète (EN, résumés FR)
│   ├── en/
│   │   ├── 01-reference-projects-analysis.md   ← analyse des projets de référence
│   │   ├── 02-architecture.md                  ← architecture générale
│   │   ├── 03-technology-choices.md            ← technologies + justification
│   │   ├── 04-extension-model.md               ← modèle des extensions
│   │   ├── 05-extension-api.md                 ← API complète des extensions
│   │   ├── 06-download-system.md               ← moteur de téléchargement
│   │   ├── 07-player.md                        ← lecteur vidéo
│   │   ├── 08-storage.md                       ← stockage & SAF
│   │   ├── 09-database-model.md                ← schéma de base de données
│   │   ├── 10-android-permissions.md           ← permissions Android
│   │   ├── 11-android-8-compatibility.md       ← compatibilité Android 8→15
│   │   └── 12-testing-strategy.md              ← stratégie de tests
│   └── fr/
│       ├── 00-resume.md                        ← résumé complet en français
│       ├── AUDIT.md                            ← audit vivant par composant
│       ├── conversations-0.22.md               ← suivi des 11 conversations (ligne par ligne)
│       └── ETAT-0.24.md                        ← état livré 0.24.0 (suivi, téléchargements, lecteur)
│
├── core/                            ← modèles partagés, erreurs, réseau commun
│   └── src/main/java/dev/endlesssea/core/
│       ├── model/Models.kt          ← catégories, statuts de téléchargement…
│       ├── net/HttpClients.kt       ← OkHttp commun (UA, cookies, logs)
│       └── util/FileNames.kt        ← sanitisation + templates de nommage
│
├── extensions-api/                  ← CONTRAT des extensions (publié pour les devs tiers)
│   └── src/main/java/dev/endlesssea/extensions/api/
│       ├── EsExtension.kt           ← interface principale d'un provider
│       ├── ExtractorApi.kt          ← résolveurs d'hébergeurs vidéo
│       ├── manifest/                ← ExtensionManifest + RepositoryIndex (JSON)
│       └── captcha/                 ← CaptchaChallenge / extensionAuth
│
├── extensions-loader/               ← chargeur : dépôts, signature, isolation, WebView
│   └── src/main/java/dev/endlesssea/extensions/loader/
│       ├── ExtensionLoader.kt       ← chargement DEX + vérification SHA-256
│       ├── RepoManager.kt           ← ajout/sync des dépôts JSON
│       ├── JsonProviderEngine.kt    ← interpréteur des providers déclaratifs
│       └── captcha/CaptchaActivity.kt
│
├── downloader/                      ← moteur de téléchargement segmenté
│   └── src/main/java/dev/endlesssea/downloader/
│       ├── segment/SegmentEngine.kt ← multi-connexions + reprise + HLS
│       ├── DownloadManager.kt       ← file d'attente, pause/reprise, priorités
│       └── DownloadService.kt       ← service de premier plan + notifications
│
├── player/                          ← lecteur vidéo (backend interchangeable)
│   └── src/main/java/dev/endlesssea/player/
│       └── EsPlayer.kt              ← wrapper Media3 (vitesse, pistes, sous-titres)
│
├── data/                            ← Room (bibliothèque, téléchargements, historique)
│   └── src/main/java/dev/endlesssea/data/
│       └── db/                      ← EsDatabase, Entities, DAOs
│
├── app/                             ← application Android (Compose, MVVM, Hilt)
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/dev/endlesssea/app/
│           ├── EndlessSeaApp.kt
│           ├── MainActivity.kt
│           ├── navigation/NavGraph.kt
│           ├── ui/ (home·explore·search·library·downloads·extensions·settings·theme)
│           └── di/AppModule.kt
│
├── demo-extensions/                 ← extensions de démonstration LÉGALES
│   ├── archive-org/                 ← provider Internet Archive (domaine public) — Kotlin
│   └── static-json/                 ← provider déclaratif + JSON (Blender® open movies, CC)
│
└── repos/official/index.json        ← exemple de dépôt d'extensions officiel
```

### Compiler

```bash
# Prérequis : JDK 17+, Android SDK 35
git clone https://github.com/<org>/endless-sea.git
cd endless-sea
./gradlew :app:assembleDebug
```

Ouvrez ensuite le projet dans **Android Studio** (Hedgehog+) pour le développement.

---

## 🇬🇧 English

### What is Endless Sea?

Endless Sea is a modern, lightweight Android app inspired by the best ideas of **Kotatsu, Mihon/Aniyomi, Cloudstream, Nuvio, Stremio and AB Download Manager/IDM**, designed as an **extensible platform**:

> **No hardcoded sources.** Users install the extensions/providers they want from pluggable repositories. The main repo has zero mandatory streaming sources.

- 🔌 Independent **extension system** (compiled `.esx` plugins, declarative JSON providers), multiple repositories, verified permissions & checksums
- ⬇️ **Powerful download manager**: segmented multi-connection downloads, pause/resume, queue, background, HLS/DASH, subtitles as sidecar files, crash/restart recovery
- 🔗 **Episode tracking**: AniList, MyAnimeList, Shikimori (manual linking + automatic marking at 90%) plus TMDB for missing posters and trailers — credentials stored on device, no account required
- ▶️ **Modern player** (Media3/ExoPlayer): **8 full themes** (Endless Sea, Netflix, Crunchyroll, YouTube, VLC, Plex, Apple TV+, Gaming — each with its own control layout), 0.25×–4× speed with pitch correction, audio tracks, **subtitles displayed and delayable ±0.5 s**, **audio boost up to 200 %**, tiered upscaling (OFF/AUTO/PERFORMANCE/QUALITY) with thermal guard, gestures (double-tap, brightness/volume drag — swappable), 1×–3× pinch zoom, lock, position resume, stats overlay
- ⏭️ **Megaskip**: intro/recap/credits/preview skipping powered by **TheIntroDB**, **IntroDB** and **AniSkip** (IDs resolved automatically through AniZip/Jikan), **offline cache** in the local database, custom skip buttons (“+85 s”, “Opening”, “Filler”) and per-type settings
- 📚 **Offline local library**: anime, movies, series, OVA, ONA, customizable categories & genres, storage filters (internal / SD card) and “Watching / Completed” tabs
- 📂 **Aniyomi-style video folders**: `Series/cover.jpg` + `details.json` (editable in-app, written into the folder) + `episodes.json` (episode titles), sorted by episode number
- 📥 **Downloads inside the library**: any downloaded episode — even a single one — becomes its own “series” (badge, episode count, storage) and plays offline
- 🔎 **Download hygiene**: SHA-256 integrity check, bandwidth limit, automatic cleanup of old files and orphan parts, “Clean now” action, foreground service with wake-lock
- 🔍 Aggregated **multi-extension search** with filters
- 🛡️ **CAPTCHA/anti-bot**: user-driven WebView verification screen, per-extension cookie persistence
- 📱 Android 8.0+, internal or SD-card storage (SAF)

### Legal notice

Endless Sea is **neutral software**, shipped without any pirated content source. Demo extensions use only legal, freely redistributable sources (Internet Archive — public domain, Blender Foundation open movies — Creative Commons, sample files). **Users are solely responsible for third-party extensions they install** and must respect their local laws and each source's terms of use.

### Documentation

Full design docs live in [`docs/en`](docs/en) (a French summary is in [`docs/fr/00-resume.md`](docs/fr/00-resume.md)):

1. [Reference projects analysis](docs/en/01-reference-projects-analysis.md)
2. [Architecture](docs/en/02-architecture.md) · 3. [Technology choices](docs/en/03-technology-choices.md)
4. [Extension model](docs/en/04-extension-model.md) · 5. [Extension API](docs/en/05-extension-api.md)
6. [Download system](docs/en/06-download-system.md) · 7. [Player](docs/en/07-player.md)
8. [Storage](docs/en/08-storage.md) · 9. [Database model](docs/en/09-database-model.md)
10. [Android permissions](docs/en/10-android-permissions.md) · 11. [Android 8+ compatibility](docs/en/11-android-8-compatibility.md)
12. [Testing strategy](docs/en/12-testing-strategy.md)

🇫🇷 Documentation complémentaire : [résumé complet](docs/fr/00-resume.md) ·
[audit par composant](docs/fr/AUDIT.md) · [suivi des 11 conversations](docs/fr/conversations-0.22.md) ·
[état livré 0.24.0](docs/fr/ETAT-0.24.md).

### Build

```bash
# Requires: JDK 17+, Android SDK 35
git clone https://github.com/j97970293-lang/endlesssea.git
cd endlesssea
./gradlew :app:assembleDebug
```

Version courante : **0.24.0** (versionCode 24) — APK de débogage :
`app/build/outputs/apk/debug/app-debug.apk`.

### License

**GPL-3.0** — see [LICENSE](LICENSE). Third-party extension repositories may pick their own license.
