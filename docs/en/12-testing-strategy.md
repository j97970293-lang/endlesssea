# 12 — Testing Strategy

> 🇫🇷 **Résumé** : stratégie de tests (point 13) : tests unitaires JVM pour les moteurs (maths de segmentation, parsing JSON, modèle d'extension), tests Room + migrations, tests instrumentés Compose sur API 26/34, fausse extension de référence (`FakeProvider`) pour tester toute l'application sans réseau, CI GitHub Actions.

---

## 1. Pyramid

```
        ┌────────────┐   few: instrumented Compose UI (API 26/34 emulators)
        │     UI     │   home renders, search aggregates fake providers,
        ├────────────┤   downloads screen state changes, player intent fires
        │ Integration│   Room DAOs, migrations, repo sync, loader install
        ├────────────┤   flow with MockWebServer + temp dirs
        │    Unit    │   SegmentEngine math, JsonProviderEngine selectors,
        └────────────┘   manifest parsing, error mapping, naming templates
```

## 2. Unit tests (JVM — fast, mandatory per PR)

| Module | Target cases |
|---|---|
| `:downloader` | `plan()` boundaries (7 bytes, 8 MiB edge, 1 GiB); resume recomputation from partial `.part`; 429/503 → segment step-down; Range-less server fallback; size-mismatch → `IntegrityError`; HLS variant pick by quality |
| `:extensions-api` | manifest JSON parse (valid/missing fields/localized description); repo index parse w/ checksums; `SourceException` serialization-safe mapping |
| `:extensions-loader` | `JsonProviderEngine` against recorded HTML fixtures (search/details/links); selector mistakes → `ParseError` (not crash) |
| `:data` | genre reorder/hide logic; dedup search aggregation by (title, year, type); "already downloaded" detection |
| `:core` | file-name sanitizer; naming-template rendering |
| `:player` | SRT/VTT subtitle shift (both directions, clamped at zero, timestamps/markup untouched); upscaling levels (order, unknown id → off, thermal down-shift step by step, never below off) |

Tools: JUnit4 + Truth + kotlinx-coroutines-test + Turbine (Flow assertions) + **MockWebServer** for all HTTP.

Instrumented coverage that does not need a video network (`Media3` buffer profiles, download-service commands, a Compose click) runs in `.github/workflows/android-instrumented.yml` on an API 29 emulator. It is intentionally not a step of the release workflow: a 30-minute emulator must not delay an APK. Full navigation through the Hilt graph is not automated; launching the whole app in a headless emulator is brittle and was left out.

## 3. FakeProvider (test doubles, no network)

`:data` test fixture `FakeProvider : EsExtension` serving a canned 2-anime/1-movie catalog from in-memory lists + `MockWebServer` streams. It drives every integration/UI test — the design rule "the app must work with zero real sources" (spec §21) doubles as a testability guarantee.

## 4. Instrumented (AndroidTest)

- Room: `MigrationTestHelper` v1→latest; DAO Flow emissions.
- Loader: installs `.esx` built by the `archive-org` demo module in `androidTest` variant; sha256 mismatch rejected; cert-change update rejected.
- Downloads: end-to-end against a local MockWebServer serving Range requests, into a temp dir; pause→resume byte-exactness asserted.
- Compose: `createAndroidComposeRule` — home banner pager auto/advance, search result grouping, downloads list actions; run on API 26 & 34.

## 5. Manual QA checklist (per release)

1. Fresh install on Android 8.1 image → add demo repo → install both demo extensions → search → play → download (pause, kill app, reopen → resume).
2. SD-target download via SAF on a real device.
3. CAPTCHA-path simulation via a fixture extension that always throws `CaptchaRequired` once.
4. Import/export library; genre add/rename/hide/reorder persistence.
5. Battery: 30 min download w/ screen off (no throttle killers on AOSP image).

## 6. CI

GitHub Actions (`android-ci.yml`): JDK 17 · unit tests (`:core :extensions-api :downloader :data :player :extensions-loader :app`) · `assembleDebug` · APK artifact.

**Implemented and green (0.25)** — every test below is plain JVM, no device/emulator required:
`FileNames` (sanitize/normalisation/gabarits de renommage, `:core`) · `ManifestParser` manifestes et index de dépôt (`:extensions-api`) · `SegmentEngine.plan` (plages, plafond de segments, réglage « 1 segment »), `HlsEngine` (variante la mieux servie, AES-128, init fMP4, erreurs) et SHA-256 des fichiers (`:downloader`) · fournisseur déclaratif + empreinte de paquet (`:extensions-loader`) · modèles Megaskip et conventions de nommage de la bibliothèque locale (`:app`) · décalage SRT/VTT et niveaux d'upscaling (`:player`). Nightly: API 26/34 instrumentation on emulator runner. Static analysis: `lint` + Detekt (next milestone).
