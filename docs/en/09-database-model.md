# 09 — Database Model (Room)

> 🇫🇷 **Résumé** : modèle Room complet (point 10 du cahier des charges) : médias, épisodes, bibliothèque, favoris, historique, téléchargements + segments, genres personnalisables, dépôts, extensions, catégories. Types énumérés partagés, requêtes `Flow` pour l'UI réactive, migrations documentées.

DB name: `endless-sea.db` · Room 2.6.1 · KSP · version 1 (migrations foldered from day one).

---

## 1. ER overview

```
EXTENSIONS (pkg PK) ─┐
REPOS (url PK) ───────┤  independent of content
                      │
MEDIA (id PK) ──────────────┐
  │ 1..n                    │ 1 (optional)
  ▼                         ▼
EPISODES (id PK, FK)     LIBRARY (mediaId PK+FK, category, favorite)
  │ 1..n                    │
  ▼                         │ n..n
WATCH_HISTORY (episodeId)  CATEGORY / GENRE links
  │
  ▼ (0..n)
DOWNLOAD_TASKS (id PK) ──1..n──► DOWNLOAD_SEGMENTS (taskId+idx PK)
GENRES (id PK, custom order/visibility)
```

## 2. Tables (fields, keys, notes)

### `media`
| col | type | notes |
|---|---|---|
| id | TEXT PK | `"<extensionId>:<mediaKey>"` — collision-free across providers |
| extensionId | TEXT | FK → extensions.pkg |
| type | TEXT | `ANIME/MOVIE/SERIES/OVA/ONA/SPECIAL` |
| title / altTitlesJson | TEXT | search normalization column added (lowercase, accents folded) |
| synopsis / posterUrl / bannerUrl | TEXT? |
| year / status / rating | INT/TEXT/REAL? |
| genresJson | TEXT | last-known provider genres (display only — user genres are in `genres`+link) |
| languagesJson | TEXT | VF/VOSTFR/MULTI… |
| studiosJson / episodeCount / durationMin | TEXT?/INT?/INT? |
| externalIdsJson | TEXT? | anilist/tmdb/mal/imdb pairs |
| cachedAt | LONG | for refresh policy |

### `episodes`
`id TEXT PK ("<mediaId>:S1:E3")` · `mediaId TEXT FK→CASCADE` · season/`number` (REAL, specials = .5…)/title/thumbnailUrl/durationMs/airDate · `data TEXT` (opaque provider payload)

### `library` (media in the user's library — spec §8)
`mediaId TEXT PK FK` · `category TEXT` (ANIME/FILMS/SERIES/OVA/ONA/CUSTOM_x) · `favorite INT(0/1)` · `addedAt LONG` · `customGenresJson TEXT?` · `sortOverride TEXT?`

### `watch_history`
`episodeId TEXT PK FK` · `mediaId TEXT` · `positionMs LONG` · `durationMs LONG` · `watched INT` (≥90 % auto) · `updatedAt LONG` (serves "recently watched", spec §10)

### `download_tasks`
`id TEXT PK` · `mediaId/episodeId TEXT?` · `url TEXT` · `headersJson TEXT` · `server TEXT` · `quality TEXT` · `streamType TEXT` (DIRECT/HLS/DASH) · `targetUri TEXT` (SAF or file:) · `fileName TEXT` · `subtitlesJson TEXT` (chosen tracks + their local uris) · `totalBytes LONG` · `status TEXT` · `error TEXT?` · `priority INT` · `createdAt/updatedAt LONG` · `etag TEXT?`

### `download_segments`
`taskId TEXT FK→CASCADE` + `idx INT` (composite PK) · `startByte/endByte LONG` · `downloadedBytes LONG` · `done INT` — the resume table (doc 06).

### `genres` (spec §9 — fully user-managed)
`id INTEGER PK AUTOINCREMENT` · `name TEXT UNIQUE` · `visible INT=1` · `position INT` · `builtin INT` (the suggested starter set: Action, Romance, Comédie, Fantasy, Isekai, Thriller, Horreur, Sport, Science-fiction…) — everything is editable: rename, reorder (position), hide, delete, add.

### `repos`
`url TEXT PK` · `name TEXT` · `enabled INT` · `addedAt/lastSyncAt LONG` · `etag TEXT?` (conditional refetch)

### `extensions`
`pkg TEXT PK` (= manifest `id`) · `name/version/versionName/apiVersion` · `author/description` · `repoUrl TEXT? FK` · `status TEXT` (ENABLED/DISABLED/INCOMPATIBLE) · `trust TEXT` (OFFICIAL/REPO/UNKNOWN) · `permissionsJson TEXT` · `certSha256 TEXT?` · `packageSha256 TEXT` · `capabilitiesJson` · `installedAt/lastUsedAt/updatesJson?` · `lastError TEXT?` (journal des erreurs, spec §17)

### `categories` (spec §8/§9 user categories)
`id TEXT PK` (ANIME/FILMS/SERIES/… or `CUSTOM_*`) · `label TEXT` · `position INT` · seeded with the five defaults; customizable.

## 3. Key queries (DAOs — Flow-returning)

- `LibraryDao.observeByCategory(category)` → posters grid
- `HistoryDao.observeContinueWatching(limit)` → home "recently watched" (position < 90 %)
- `DownloadsDao.observeActive()` / `observeQueueOrdered()` → Downloads screen, engine scheduler
- `MediaDao.searchLocal(query)` → offline library search
- `GenreDao.observeVisible()` → chips row; `reorder(id, position)`, `setVisible(id, 0/1)`
- `ExtensionsDao.observeInstalled()` / `observePendingUpdates()`

## 4. Migrations & testing

`schemas/` JSON exported (Room `exportSchema=true`) into version control; `AutoMigration` where trivial, manual SQL otherwise; every migration covered by `MigrationTest` (doc 12).

## 5. Privacy note

The DB never stores credentials. Legitimate per-site auth (when an extension implements login) goes into EncryptedSharedPreferences/Keystore-scoped storage — see doc 10.
