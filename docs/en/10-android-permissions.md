# 10 — Android Permissions

> 🇫🇷 **Résumé** : liste exhaustive des permissions du manifeste avec justification (point 11), politique de minimisation : aucune permission dangereuse exigée au premier lancement, `WRITE_EXTERNAL_STORAGE` plafonné à l'API 28, notifications demandées au runtime (API 33+), et correspondance avec les permissions *des extensions* (qui sont affichées et révocables).

---

## 1. App manifest permissions

| Permission | Since | Justification | Runtime flow |
|---|---|---|---|
| `INTERNET` | always | streaming, extensions, metadata | manifest only |
| `ACCESS_NETWORK_STATE` | always | Wi-Fi-only download policy, adaptive segments | manifest only |
| `FOREGROUND_SERVICE` | API 28+ | download service | manifest only |
| `FOREGROUND_SERVICE_DATA_SYNC` | API 34+ | typed FG service for downloads | manifest only |
| `POST_NOTIFICATIONS` | API 33+ | download progress (spec §5) | asked when the user starts their **first download** |
| `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`) | API ≤ 28 | default public Downloads dir on Android 8–9 | asked only if user keeps default dir on API 26–28 |
| `WAKE_LOCK` | always | keep segments alive during transfer | manifest only |
| `RECEIVE_BOOT_COMPLETED` | always (off by default) | optional queue restore after reboot | behind a disabled-by-default setting |

**Deliberately NOT requested**: `READ_MEDIA_VIDEO`/`READ_EXTERNAL_STORAGE` (we use SAF picks), `QUERY_ALL_PACKAGES` (extensions are side-loaded containers inside our own dir), `REQUEST_INSTALL_PACKAGES` (unlike Mihon: our `.esx` never touches the package installer — smaller attack surface), location, contacts, account permissions. Legitimate site auth secrets live in `EncryptedSharedPreferences` (Keystore).

## 2. Runtime UX map

```
First download ──► POST_NOTIFICATIONS rationale sheet (API 33+)
Change folder to SD ──► ACTION_OPEN_DOCUMENT_TREE (SAF) → persistable URI
API 26–28 + default folder ──► WRITE_EXTERNAL_STORAGE (single prompt, skippable → private dir)
```

## 3. Extension permissions (declared in extension manifest, UI-enforced)

| Extension permission | Shown to the user as (« Cette extension demande : ») |
|---|---|
| `INTERNET` | « accès Internet » |
| `DOWNLOAD` | « accès au téléchargement » |
| `FILES` | « accès aux fichiers (dossier privé de l'extension) » |
| `WEBVIEW` | « vérifications interactives possibles » |

Users can revoke `INTERNET`/`DOWNLOAD` per extension at any time (network kills switch in the Extensions screen). All four documented in [doc 04 §6](04-extension-model.md).

## 4. Network security

`android:usesCleartextTraffic="false"` by default. Extensions needing an `http://` source must declare it in capability `cleartextHosts` — reflected in the permission sheet — and the app applies a scoped `networkSecurityConfig` rather than opening cleartext globally.
