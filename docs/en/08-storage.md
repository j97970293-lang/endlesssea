# 08 — Storage

> 🇫🇷 **Résumé** : stockage Android moderne (scoped storage + SAF). L'utilisateur choisit l'emplacement affiché (« /Téléchargements/EndlessSea/ » par défaut, carte SD ou dossier SAF). Organisation automatique par type/titre/saison, template de nommage configurable, détection des doublons, écriture positionnée pour la reprise, gestion des cartes SD lentes (fallback par-parties).

---

## 1. Storage options & priority (spec §16)

1. **Default**: app-specific public-ish path via MediaStore: `/Téléchargements (Download)/EndlessSea/…` — no permission needed on API 29+; visible in file managers on API 26–28 with legacy `WRITE_EXTERNAL_STORAGE` (maxSdkVersion 28).
2. **SD card / custom folder**: user picks a directory once via **SAF** (`ACTION_OPEN_DOCUMENT_TREE`); we persist the URI permission (`takePersistableUriPermission`) and write through `DocumentFile`/`ParcelFileDescriptor`.
3. **Private app storage** fallback when SAF grants fail.

The Settings screen shows the resolved human path: `Emplacement des téléchargements : "/Téléchargements/EndlessSea/"` with a "Changer" action (spec §16).

## 2. Folder organization (spec §8, §24)

```
<root>/Endless Sea/
├── Anime/<Titre normalisé>/Saison 01/<Titre> - S01E03 [1080p][VOSTFR].mkv
├── Films/<Titre> (<année>)/<Titre> (<année>).mp4 + .fr.srt
├── Séries/<Titre>/Saison 02/…
├── OVA/… · ONA/… · (custom categories mirror/ their own dirs)
└── .tmp/            ← active .part files + segment tables (hidden from gallery)
```

Rules:
- Sanitized names (no `\/:*?"<>|`, Unicode-aware ellipsis, ≤ 100 chars per component → FAT/SD safe; ABDM's `IncompleteFileUtil` lesson).
- Naming template (Settings): `{title} - S{season:00}E{episode:00} [{quality}][{lang}].{ext}` with editable tokens (spec §24 "renommage automatique").
- Category folders follow the media's *library category*, user-overridable per download.

## 3. Writing strategy per target

| Target | Strategy | Why |
|---|---|---|
| Internal / adoptable | Single `.part` + positioned writes (`FileChannel`) | Fastest resume, no merge, FAT-safe sizes |
| SAF on SD (DocumentFile) | Per-part temp files then sequential concat through the SAF output stream | Random access is slow/broken on many SD stacks (legacy lesson) |
| HLS | Growing `.part.ts` + cursor checkpoint | Segments are sequential anyway |

`.part` artifacts live under `.tmp/` and are excluded from MediaStore scans (trailing `.part` + `.nomedia`).

## 4. MediaStore integration

- Completed movies/episodes are inserted into `MediaStore.Video` (display name, duration, size) so gallery/players can see them; playlist metadata kept in Room (authoritative).
- Deleting from the library offers "delete files too"; orphans scan (clean stored files whose DB rows vanished) offered in Settings.

## 5. Capacity & safety

- Pre-download space check (`StatFs` / SAF `DocumentsContract` query) → clear error "Stockage insuffisant (il manque 1,2 Go)".
- `.tmp` janitor: purge stale parts older than 30 days (toggleable).
- Import/export of the library (spec §24) = JSON + references to file URIs (SAF-safe across reinstalls when the tree is re-granted).

## 6. Privacy

No telemetry on user files. Backup (spec §24) is an explicit user action to a user-chosen location.
