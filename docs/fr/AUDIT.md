# Audit Endless Sea — Phase 1 (stabilisation) + Phase 2 (fondations)

> Audit historique. Pour les corrections actuelles de reprise locale, métadonnées, scan et sauvegarde, consulter [Fiabilité et sauvegardes](../maintenance/reliability-and-backups.md). Ne pas assimiler les conclusions de cette archive à des tests appareil récents.


*Dernière révision : 2026-10-05 — post v0.8.0.*

Légende : ✅ terminé · 🟡 partiel · 🔴 cassé/absent.

## COMPOSANTS — Phase 1

### 1. Compilation — ✅
- Modules : app, core, data, downloader, extensions-api, extensions-loader, player.
- CI GitHub Actions : `assembleDebug` vert sur v0.8.0 (commit 7c2c642).
- Risque : build local non exécuté dans l'environnement agent (Gradle lourd) ; CI = seule source de vérité.

### 2. Crash de mise à jour (§5/§16) — ✅ (v0.8.0)
- `AppUpdateInstaller` : destination `ExternalFilesDir` (plus de permission stockage),
  garde `canRequestPackageInstalls` → écran de réglage système, runCatching → Toast +
  repli navigateur. Permission `REQUEST_INSTALL_PACKAGES` ajoutée.
- Non vérifié : installation réelle sur appareil physique Android 8 (pas d'appareil
  dans l'environnement). Flow validé en revue de code + CI uniquement.

### 3. WebView (§3) — 🟡
- Fait (v0.8.0) : barre de progression, page d'erreur + Réessayer, dialogue SSL
  explicite (Annuler par défaut), destroy en onDestroy, back interne.
- Reste : état « timeout » dédié, état « page vide » explicite, test réel de rotation.
  Les cookies sont synchronisés via CookieManager (inchangé, fonctionnel).

### 4. Images des extensions (§4) — 🟡 → ✅ (lot A)
- Cause identifiée : Coil par défaut — pas d'UA honnête, pas de DNS durci,
  aucun visuel de chargement/erreur. Une URL morte = carré vide silencieux.
- Fait (lot A) : `EsImages` (ImageLoader global : OkHttp durci + cache 512 Mo +
  crossfade), URL purgée anti-`javascript:`, `SafeAsyncImage` (placeholder « vague »
  + croix rouge), icônes des dépôts/entrées/extensions sur l'écran Sources,
  vignettes MediaCard avec placeholders. Champ `iconUrl` ajouté à RepositoryIndex
  (additif, compat ascendante).
- Non vérifié : aucun dépôt réel avec icônes n'existe pour les tests visuels ; le
  repli placeholder a été raisonné, pas photographié.

### 5. Téléchargements « 36 Ko » (§6) — ✅ (v0.8.0)
- Garde taille minimale + refus explicite FR + journal EsLog.
- Non vérifié : sans appareil, pas de téléchargement réel instrumenté.

### 6. Gestion des erreurs (§7) — 🟡
- Fait : EsLog (journal persistant, 200 entrées) + écran 🩺 Diagnostic
  (rafraîchir/copier/exporter/effacer) + points de log : downloads, updater, captcha.
- Reste : branches de log sur les moteurs d'extraction (loader) et le lecteur.

### 7. Compatibilité Android 8 / API 26 — 🟡
- Fait : minSdk 26, pas d'API > 26 sans garde, ExternalFilesDir (A8-safe),
  fonts téléchargeables (Play Services), pas de desugaring lourd.
- Non vérifié : exécution émulateur API 26 — hors de portée de l'environnement.

### 8. DNS (§9) — ✅ (v0.8.0)
- DoH Cloudflare/Google/Système + repli silencieux ; réglage dans Réseau & DNS.

## COMPOSANTS — Phase 2 (fondations)

### 9. Modèle Media — 🟡
- Existant solide : DTO API (MediaType/Status/SearchItem/MediaDetails/Episode/Quality),
  MediaEntity Room (titleKey normalisé, externalIdsJson), LibraryEntity (watchlist §29).
- Action recommandée (prochains lots) : rapprochement inter-sources via titleKey
  + année + type ; fiche « fichiers locaux associés ».

### 10-14. Métadonnées / matching / trackers — 🔴 (différé, assumé)
- Aucun service tiers sans clé n'est câblé (AniList envisagé, gratuit + GraphQL).
- Risque assumé : ne pas coupler le cœur à un service ; préférer une « extension
  de métadonnées » (point 15) quand l'API le permettra.

## Problèmes restants (honnêteté)
- Blur « verre » = translucidité simulée (pas de blur GPU cross-version).
- Deband lecteur : partiel selon GPU.
- Pas de tests sur appareil réel — CI assembleDebug seulement.
