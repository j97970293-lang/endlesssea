# 11 — Android 8.0+ Compatibility Matrix (API 26 → 35)

> 🇫🇷 **Résumé** : exigences de compatibilité (point 12) : tout fonctionne dès l'API 26, les nouveautés se dégradent proprement. Tableau par niveau d'API, politique de désucrage, tests sur émulateurs bas de gamme, cible de performance appareils à 2 Go de RAM.

`minSdk = 26`, `targetSdk = 35`, `coreLibraryDesugaring = true` (java.time, streams).

| API | Feature gate | Our behavior |
|---|---|---|
| 26–27 (8.0–8.1) | Notification channels mandatory; FG service rules; adaptive icons | Channels `downloads/general` created at first launch; icon PNG fallback; PiP supported |
| 28 (9) | Legacy `WRITE_EXTERNAL_STORAGE` last version; TLS 1.3 off by default | Permission prompt (doc 10); Conscrypt via Play Services not required |
| 29 (10) | Scoped storage onset | We never rely on raw paths on shared storage — SAF/MediaStore only |
| 30 (11) | Package visibility | Not needed: extension containers are inside our own private dir |
| 31 (12) | PendingIntent mutability; splash screen API; Bluetooth etc. | Explicit mutability flags; `core-splashscreen` compat backport |
| 33 (13) | `POST_NOTIFICATIONS` runtime | Contextual prompt at first download; graceful "no notifications" mode |
| 34 (14) | Typed FG services mandatory (`dataSync`) | Declared + started via `ServiceCompat.startForeground` compat |
| 35 (15) | Edge-to-edge default enforcement | Insets-aware Compose scaffold (tested) |

## Hard guarantees on API 26 (low-end profile)

- Compose baseline profile (`baselineprofile` later) + R8 full mode; lazy lists everywhere; images ≤ 512 px decode in lists (Coil size-aware).
- Room queries paged; no N+1 joins on the library screen.
- Segments per task capped at 4 on ≤2 GB RAM devices (engine reads `ActivityManager.isLowRamDevice`).
- WebView (CAPTCHA screen) created lazily and destroyed on dismiss.

## CI coverage

Unit tests (JVM) on every PR; instrumented smoke suite on API 26 & 34 emulator images nightly (doc 12). Desugared `java.time` used in history/backup code paths only.

## Upgrade discipline

- `targetSdk` bumped deliberately (never automatically), with the gating table above re-checked each bump.
- Deprecations fixed on sight; zero `// TODO(api)` debt allowed into `main`.
