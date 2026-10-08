# Verified cleanup and selectable logos

Baseline audited: `541764c`. No release/tag/version bump accompanies this work.

## Cleanup boundaries

- Removed unreferenced private helpers (`IsDark`, `getFirstEnabledService`, `MegaSkipPill`).
- Removed unreferenced player UI roots (`SkinSkipButton`, `AllTools`, `TrackIcons`, `SubtitleLine`, `MegaSkipCluster`) and their exclusively referenced children (`MegaskipRow`, `SkipSegmentPill`, long-press helper).
- Kept `CustomSkipDialog`: both PlayerActivity and SettingsScreen call it.
- Removed unused `holderKey` and `forceMarkEpisodeWatched` methods.
- Removed the unused SharedPreferences tracker-account/link accessors. Existing preference keys are **not erased**; active tracking still uses Room. No database schema/migration change.
- Unified individual/batch download creation, player duration formatting and local tracker +1 progression.
- Preserved Hilt providers, framework callbacks, Room migrations and the public extension API/ABI.
- Shared player bars now honor progress/tools top/bottom preferences; MegaSkip honors left/right. Theme-specific central controls and outer frames remain.

## Logo catalogue

Images were inspected, not selected by filenames: `logo_bright_blue.png` is the blue-and-white wave, whereas the old `logo_dark_blue.png` depicts the pirate/adventure variant.

Eight existing designs are retained, including Classic. Blue and white is the default. All artwork is normalized to 512×512 WebP (quality 88, alpha preserved), replacing the oversized source PNGs. The originals remain recoverable from Git history.

- Previous eight source images: **12,351,452 bytes**.
- Eight optimized drawable images: **396,844 bytes** (96.79% reduction).
- These are asset-file measurements, **not a measured APK-size reduction**. Launcher XMLs and density fallbacks are additional.

Settings → Interface et thème → Logo de l'application selects both the launcher icon and Compose splash logo. The logo in the top-left navigation menu remains the library icon, as previously requested. Original splash colors now mean no tint; an explicit splash tint remains available separately and does not recolor the launcher icon.

MainActivity stays enabled and retains video VIEW handling. Eight exported activity aliases target it. Only BlueWhite is enabled by default. Runtime alias selection uses atomic PackageManager updates on Android 13+, or enables the new alias before disabling old aliases on Android 8–12. Overrides are reconciled at application startup and rolled back on selection failure. Debug/release application IDs are handled via `context.packageName` with stable fully qualified alias names.

## Checks

- `python3 scripts/check_logo_assets.py`: catalogue/manifest/resource linkage, exactly one default alias, eight optimized images and total logo budget <600 kB.
- JVM regression tests: duration formatting, all four player-bar placement combinations, logo defaults/fallbacks and unique IDs/aliases.
- GitHub Android CI: existing full JVM suite and debug APK assembly.

## Device checks still required (not claimed as executed)

1. Update an existing installation: old launcher shortcut still opens MainActivity; only the default alias is discoverable after launcher refresh.
2. Select each logo on Android 8–12 and 13+: exactly one launcher entry, correct image, no loss of app data; allow time for launcher caches.
3. Restart, update and restore an Android backup: selected logo survives and components reconcile.
4. Check adaptive masks (circle/squircle), original/tinted Compose splash, portrait/landscape control placement in every player theme.
5. Verify external video VIEW intents and navigation into MainActivity are unaffected.
