# Extension developer guide

> Step-by-step for a third-party `.esx` or a declarative JSON provider. The contract is API version **1** (`dev.endlesssea.extensions.api.EsExtension`). A shorter reference lives in [05-extension-api.md](05-extension-api.md).

The app does not ship content sources. Users get extensions from a repository `index.json`. The French catalog is already known to the app:

`https://github.com/j97970293-lang/endlesssea-plugins-fr/releases/latest/download/index.json`

They should not have to paste that address again after the first install. Another repository can still be added from Extensions → Repositories.

## 1. Pick a flavor

| Flavor | When | Artifact |
|---|---|---|
| Compiled `.esx` | Search, pages or hosts need Kotlin | APK-shaped zip: `classes.dex` + `assets/extension.json` |
| Declarative JSON | A site can be described with URL templates and CSS/regex | One `provider.json`, `kind: DECLARATIVE` |

Working examples in this repository:

- compiled: `demo-extensions/archive-org`
- declarative: `demo-extensions/static-json/provider.json`
- copy-paste Gradle project: [extension-template](extension-template)

## 2. Implement `EsExtension`

Every function is `suspend`. Failures must be a `SourceException`, not a raw stack trace. Only use sources whose terms allow this client.

| Callback | Required | Role |
|---|---|---|
| `info` | yes | Name, language, permissions shown before install |
| `search(query, page, filters)` | yes | Catalog search. `page` starts at 1 |
| `load(url)` | yes | Details, seasons, episodes, external ids (`anilist`, `mal`, `tmdb`) |
| `loadLinks(data)` | yes | Playable `VideoLink`s for one episode |
| `loadLinksFlow(data)` | no | Same links, emitted as each host answers. Default replays `loadLinks` |
| `getMainPage(request)` | no | Home row. Default: empty |
| `categories()` | no | Named catalog rows. Default: one main row |
| `extractors()` | no | Shared host resolvers tried on embed URLs |
| `settings()` | no | Fields rendered in the app |

`VideoLink.streamType` may be `DIRECT_FILE`, `HLS` or `DASH`. Static DASH can be downloaded. Live DASH and DRM are playback-only. Put sidecar subtitles on `VideoLink.subtitles` (`SRT`, `VTT`, `ASS`, `SSA`); the app downloads them next to the video. ASS/SSA dialogue is shown as SRT. Drawing, fonts and animations are not rendered, so low-RAM devices stay responsive.

A constructor may take `ExtensionContext` or nothing. `ExtensionContext.http` is the only HTTP client an extension should use.

## 3. `assets/extension.json`

```json
{
  "id": "org.example.mysource",
  "name": "My Source",
  "version": 1,
  "versionName": "1.0.0",
  "apiVersion": 1,
  "description": { "fr": "…", "en": "…" },
  "author": { "name": "Your name", "url": "https://example.com" },
  "languages": ["fr"],
  "types": ["SERIES", "MOVIE"],
  "entryClass": "org.example.mysource.MyProvider",
  "permissions": ["INTERNET", "DOWNLOAD"],
  "nsfw": false
}
```

`entryClass` is omitted for a declarative provider. `apiVersion` must be `1` or the loader rejects the package. An update signed by a different certificate is also rejected.

## 4. Build an `.esx`

From [extension-template](extension-template), or from `demo-extensions/archive-org` inside this repo:

```bash
./gradlew :demo-extensions:archive-org:assembleRelease
cp demo-extensions/archive-org/build/outputs/apk/release/*-release.apk my-source-1.0.0.esx
sha256sum my-source-1.0.0.esx
```

The API must be `compileOnly`. The app already contains those classes; bundling them into the `.esx` breaks loading.

## 5. Publish an `index.json`

```json
{
  "name": "My repository",
  "description": "Optional",
  "url": "https://example.com/index.json",
  "extensions": [
    {
      "id": "org.example.mysource",
      "name": "My Source",
      "version": 1,
      "versionName": "1.0.0",
      "apiVersion": 1,
      "languages": ["fr"],
      "types": ["SERIES"],
      "permissions": ["INTERNET", "DOWNLOAD"],
      "minAppVersion": 1,
      "size": 120000,
      "apkUrl": "https://example.com/my-source-1.0.0.esx",
      "sha256": "64 lowercase hex characters",
      "kind": "COMPILED",
      "nsfw": false
    }
  ]
}
```

`apkUrl` for a declarative provider points at the raw `provider.json` and `kind` is `DECLARATIVE`. The app checks the SHA-256 before install. Host the file over HTTPS. Users add that URL once; the app remembers it.

## 6. Declarative provider, minimal

See `demo-extensions/static-json/provider.json` for a full legal catalog. The shape is `manifest`, `catalog` or `search` / `details` / `links`. Unknown JSON fields are ignored. A provider that needs a session, a captcha or more than URL templates should be a compiled extension instead.
