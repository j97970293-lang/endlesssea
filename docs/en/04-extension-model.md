# 04 — Extension Model

> 🇫🇷 **Résumé** : modèle d'extensions inspiré de Cloudstream (plugins compilés chargés à l'exécution), Mihon (dépôts + versions), Stremio (manifeste déclaratif) et Nuvio (installation par URL), avec sécurité renforcée : checksums SHA-256, permissions déclarées, niveaux de confiance explicites, aucune source imposée.

---

## 1. Design goals (spec §2, §17, §18, §19)

1. The app ships **zero** content sources. Extensions come from user-added repositories.
2. Third-party developers can build extensions **without touching** the app (spec §27).
3. Mono or multiple repositories: any HTTPS URL serving an `index.json` works (spec §18).
4. Extensions are installable, uninstallable, enable/disable-able, updatable, versionable; each exposes author, description, permissions, source repo, compatibility (spec §17).
5. Users see exactly what an extension may do **before** installing it (spec §19).

## 2. Extension flavors

### 2.1 Compiled Kotlin plugins (`.esx`)
The full-power flavor (Cloudstream/Kotatsu-style), re-implemented with our own contract.

- **Container**: an `.esx` file is a standard APK zip containing `classes.dex` + `assets/extension.json`. It is **not installed** on the system; it is stored in the app's private `extensions/` dir and classes are loaded with `dalvik.system.PathClassLoader`. No `REQUEST_INSTALL_PACKAGES` needed (unlike Mihon).
- **Compilation**: extension projects `implementation(project(":extensions-api"))`-style against the **published** `dev.endlesssea:extensions-api` artifact; the API jar is marked `compileOnly` in the extension build (the app already contains these classes; the loader uses the app's ClassLoader as parent so contracts are shared).
- **Entry point**: `assets/extension.json` declares `entryClass`, which must implement `dev.endlesssea.extensions.api.EsExtension`.
- **Distribution**: repository `index.json` → `apkUrl` + `sha256`.

### 2.2 Declarative JSON providers
No code execution — inspired by Stremio's manifest philosophy. A JSON bundle describes:

```jsonc
{
  "manifest": { "id": "org.example.simple", "name": "SimpleSite", "version": 3,
    "apiVersion": 1, "languages": ["fr"], "types": ["MOVIE", "SERIES"],
    "permissions": ["INTERNET"], "baseUrl": "https://example.org" },
  "search":   { "url": "{base}/recherche?q={query}&page={page}", "items": ".film-list .item",
                "title": ".title", "url": "a@href", "poster": "img@src" },
  "details":  { "synopsis": ".resume p", "genres": ".tags a", "episodes": ".episodes a", "episodeUrl": "@href" },
  "links":    { "servers": ".player-tabs button", "iframe": "#player@src",
                "resolve": [{ "match": "https://cdn\\\\.example\\\\.org/.*", "regex": "file:\\\\s*'([^']+)'" }] }
}
```

Interpreted by `JsonProviderEngine` (module `:extensions-loader`). Great for simple HTML sites and for teaching the model. Generated `EsExtension` instances are memory-only.

### 2.3 Remote HTTP addons *(future)*
Stremio-compatible (`/manifest.json`, `/catalog`, `/meta`, `/stream`, `/subtitles`). Zero on-device code; the safest flavor. Lands after v1.0; wire-compatible with the Stremio ecosystem.

## 3. Manifest schema (`assets/extension.json`)

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string (reverse-DNS) | ✅ | Unique extension id, e.g. `dev.endlesssea.demo.archiveorg` |
| `name` | string | ✅ | Display name |
| `version` | int | ✅ | Monotonic version code (updates compare this) |
| `versionName` | string | ✅ | Human version, e.g. `1.2.0` |
| `apiVersion` | int | ✅ | Targeted `:extensions-api` major (see §5) |
| `description` | string | ✅ | Short description (may be localized map `{"fr":…,"en":…}`) |
| `author` | object | ✅ | `{ "name":…, "url":… }` |
| `languages` | string[] | ✅ | ISO 639-1 content languages, `["fr","en"]` |
| `types` | string[] | ✅ | `ANIME` `MOVIE` `SERIES` `OVA` `ONA` `SPECIAL` |
| `iconUrl` | url | ➖ | Remote icon (or bundled `assets/icon.png`) |
| `entryClass` | string | ✅ (.esx) | Fully-qualified class implementing `EsExtension` |
| `permissions` | string[] | ✅ (may be `[]`) | See §6 |
| `capabilities` | object | ➖ | `{ "search": true, "servers": true, "subtitles": true, "downloads": true, "auth": false }` |
| `sourceUrl` | url | ➖ | Extension's source repository (open-source encouraged) |
| `nsfw` | bool | ➖ default `false` | Content flag |
| `sha256` | string | repo-level | In repo index; verified at install/update |

## 4. Repository index (`index.json`, spec §18)

```jsonc
{
  "name": "Endless Sea Official Demos",
  "description": "Legal demo providers maintained by the project",
  "url": "https://raw.githubusercontent.com/<org>/endless-sea/main/repos/official/index.json",
  "extensions": [
    {
      "id": "dev.endlesssea.demo.archiveorg",
      "name": "Internet Archive — Public Domain",
      "version": 1, "versionName": "1.0.0", "apiVersion": 1,
      "author": { "name": "Endless Sea" },
      "description": { "fr": "Films du domaine public (archive.org)", "en": "Public-domain movies (archive.org)" },
      "languages": ["en","fr"], "types": ["MOVIE"],
      "permissions": ["INTERNET"],
      "minAppVersion": 1,
      "size": 41280,
      "iconUrl": "https://…/icon.png",
      "apkUrl": "https://…/archiveorg-1.0.0.esx",
      "sha256": "ab12…9f"
    }
  ]
}
```

- Users may add **any number** of repositories (URL or pasted index).
- Repos are refreshed on pull-to-refresh and every 12 h; diffs produce "Update available" badges.
- A repository can be disabled without being deleted.

## 5. Compatibility & API versioning

- `extensions-api` uses **major API versions**. The app carries `API_VERSION = 1`.
- Install-time check: `extension.apiVersion == app.apiVersion` and `extension.minAppVersion <= app.versionCode`, else the extension is listed as *Incompatible* with an explanation.
- The API evolves additively: default interface methods keep old extensions running.

## 6. Permission model (spec §19)

| Permission | Meaning | Enforcement today |
|---|---|---|
| `INTERNET` | Network requests to the declared source domains | All extension HTTP goes through the app-provided `ExtensionHttpClient` which logs domains; users can revoke per-extension network access at runtime |
| `DOWNLOAD` | Hand direct file URLs to the download engine | Extensions without it can only stream |
| `FILES` | Read/write inside the extension's own sandbox dir only | Loader passes a scoped directory; no paths outside are exposed |
| `WEBVIEW` | May request interactive verification windows | Required for anti-bot flows |

Displayed as a plain-language sheet before install (FR/EN). Unknown-source installs (sideloaded `.esx`) show an additional warning banner.

> **Honesty note (parity with CS3/Mihon)**: DEX plugins execute in-process; Android offers no full sandbox for them. Endless Sea therefore layers defense in depth: (1) checksum verification from the repo, (2) explicit permissions, (3) per-extension network visibility, (4) disable/kill-switch, (5) removal-from-repo propagation, (6) the declarative-JSON flavor which executes **no** code at all.

## 7. Trust levels & signature

1. **Official** — built by the project, signed by the project key; demo extensions only (legal sources).
2. **Repository-verified** — `sha256` pinned by the repo the user added; updates must keep the same id and carry matching hashes.
3. **Unknown** — sideloaded `.esx`; runs with a warning chip in the Extensions screen and auto-disabled `DOWNLOAD` permission until granted.

Certificates: when the extension APK is signed, its signing-certificate digest is stored at install; an update whose signer changes is rejected explicitly (user must reinstall intentionally).

## 8. Lifecycle

`Available → Downloading → Installing → Installed(Enabled|Disabled) → UpdateAvailable → Updating → Uninstalled`

Each transition is persisted in `ExtensionEntity` (doc 09), with `lastError` for diagnostics (spec §17 journal des erreurs). Installed `.esx` packages live under `filesDir/extensions/<id>/<version>/` so a failed update never kills the working copy (atomic swap + rollback).

## 9. Demo providers (spec §21)

| Provider | Flavor | Content | Legality |
|---|---|---|---|
| `demo.archiveorg` | compiled Kotlin | Public-domain/CC films via Internet Archive `advancedsearch` + `metadata` APIs | ✅ Archive.org ToS-compliant API |
| `demo.staticjson` | declarative JSON | Blender open movies (Big Buck Bunny, Sintel, Tears of Steel, Elephants Dream) + sample subtitles | ✅ CC-BY / CC0 sample assets |
