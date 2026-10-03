# Endless Sea — Résumé complet de la conception (FR)

Ce document résume en français les décisions détaillées dans `docs/en/01…12`. Il répond point par point à la section « Résultat attendu » du cahier des charges.

---

## 1. Analyse des projets de référence (doc 01)

- **Kotatsu** (GPL-3.0+) → contrat de parseurs propre + modèle hors-ligne Room.
- **Mihon/Tachiyomi** (Apache-2.0) → extensions en APK séparés + dépôts avec mises à jour ; leçon : ne jamais embarquer de sources dans le dépôt principal.
- **Aniyomi** → mpv-android, suivi par épisode, trackers (AniList/MAL).
- **Cloudstream** (GPL-3.0) → plugins Kotlin compilés chargés à l'exécution, `MainAPI` (search → load → loadLinks), extracteurs d'hébergeurs, WebView anti-bot.
- **Nuvio** (GPL-3.0) → providers JavaScript installés par URL, libass pour les sous-titres.
- **Stremio** → addons = services HTTP déclaratifs (aucun code exécuté) : modèle le plus sûr.
- **AB Download Manager** (Apache-2.0) → le plan directeur de notre moteur : segments multi-connexions, écritures positionnées, persistance, files.
- **AnymeX / AniZen** → preuve de portabilité du contrat provider, UX « anime-first ».

Aucun code n'est copié : nous réutilisons les **concepts** (compatibles licences) et ré-implémentons.

## 2. Architecture proposée (doc 02)

Projet Gradle **multi-modules** : `:app` (Compose/MVVM/Hilt) → moteurs (`:extensions-loader`, `:downloader`, `:player`, `:data`) → contrats (`:extensions-api`, `:core`). Chaque moteur est remplaçable sans réécrire l'application. Flux de données unidirectionnel, coroutines/Flow partout, aucune source codée en dur.

## 3. Arborescence du dépôt

Voir la section bilingue du [README](../../README.md) — elle contient l'arbre complet commenté.

## 4. Technologies choisies (doc 03)

Kotlin 2.1 · Jetpack Compose Material 3 · Hilt · Room · DataStore · OkHttp · jsoup (extensions) · kotlinx.serialization · **Media3/ExoPlayer** (mpv en backend optionnel futur) · Coil · WorkManager · minSdk **26** / target 35 · désucrage java.time.

## 5. Modèle des extensions (doc 04)

Trois saveurs, un seul contrat :
1. **Plugins Kotlin compilés `.esx`** (DEX + `assets/extension.json`), chargés en interne via `PathClassLoader` — pas d'installation système ;
2. **Providers déclaratifs JSON** (aucune exécution de code) pour les sites simples ;
3. **Addons HTTP distants** compatibles Stremio (futur).

Dépôts multiples par URL (`index.json`), vérification **SHA-256**, niveaux de confiance (officiel/dépôt/inconnu), refus des mises à jour au signataire différent, journal d'erreurs par extension.

## 6. API des extensions (doc 05)

Interface `EsExtension` : `getMainPage`, `search` (+`FilterSet`), `load` (fiche détaillée avec saisons/épisodes/serveurs), `loadLinks` (liens vidéo + qualités + sous-titres), `extractors()`, erreurs typées (`SourceException` : CAPTCHA requis, source indisponible, aucun résultat…), flux **CAPTCHA via WebView** avec persistance des cookies, pagination `PagedResult`, sélection de serveurs/qualités normalisée.

## 7. Système de téléchargement (doc 06)

Sonde HEAD → plan de segments (1–8, adaptatif) → connexions `Range` parallèles → **fichier `.part` unique à écriture positionnée** → vérification de taille/somme → renommage final + **sous-titres en fichiers séparés** (SRT/ASS/VTT). Reprise après interruption/crash/redémarrage (recalcul depuis les octets réels), HLS via le parseur Media3, recul de débit sur 429/503, file avec priorités (2 tâches × 4 segments par défaut), service FG `dataSync` + notifications, feuille « Télécharger en un clic » (serveur × qualité × sous-titres), téléchargement d'une saison entière.

## 8. Lecteur (doc 07)

`PlayerEngine` (interface) + implémentation **Media3** : lecture locale + flux extension, HLS/DASH, vitesse, pistes audio, sous-titres (style, taille, couleur, synchro), gestes (luminosité/volume/double-tape), verrouillage, PiP, reprise de position toutes les 5 s, libass en feuille de route pour les ASS.

## 9. Stockage (doc 08)

Scoped storage + SAF : dossier par défaut `/Téléchargements/EndlessSea/`, carte SD via sélecteur SAF (URI persistée), arborescence automatique Anime/Films/Séries/Saison, template de nommage, fichier `.part` caché, fallback par-parties sur SD lente, vérification d'espace, nettoyage des orphelins.

## 10. Base de données (doc 09)

Room `endless-sea.db` : `media`, `episodes`, `library`, `watch_history`, `download_tasks`, `download_segments`, `genres` (ajout/renommage/réorganisation/masquage — véritable édition utilisateur), `repos`, `extensions`, `categories`. Requêtes `Flow` réactives, migrations testées.

## 11. Permissions Android (doc 10)

`INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE(_DATA_SYNC)`, `POST_NOTIFICATIONS` (demandée au 1er téléchargement), `WRITE_EXTERNAL_STORAGE` **plafonnée API 28**, `WAKE_LOCK`. **Pas de** `REQUEST_INSTALL_PACKAGES` (les `.esx` ne passent pas par l'installeur — leçon Mihon). Secrets d'authentification → EncryptedSharedPreferences. Permissions **des extensions** affichées et révocables.

## 12. Compatibilité Android 8→15 (doc 11)

Tableau API 26→35 : canaux de notification, scoped storage (on n'y touche jamais directement), PendingIntents mutables, services FG typés, edge-to-edge. Garanties bas de gamme : décodage d'images limité, requêtes paginées, plafond de segments sur `isLowRamDevice`, WebView paresseuse.

## 13. Tests (doc 12)

Unitaires JVM (maths de segmentation, parseurs, manifestes) avec MockWebServer ; intégration Room + chargeur ; instrumentés Compose API 26/34 ; `FakeProvider` de référence pour tout tester sans réseau ; CI GitHub Actions (tests + APK debug en artefact), suite émulateur de nuit.

## 14/15. Implémentation commencée

Le squelette livré ici contient : configuration Gradle complète, contrat `extensions-api` complet, moteur de téléchargement segmenté fonctionnel (noyau), schéma Room complet, chargeur d'extensions (DEX + JSON), wrapper lecteur Media3, application Compose navigable (accueil avec grande bannière défilante, explorer, recherche, bibliothèque, téléchargements, extensions, paramètres), deux extensions de démonstration **légales** (Internet Archive — domaine public ; films Blender — CC) et un dépôt officiel d'exemple.

**Prochaines étapes (feuille de route)** :
1. `HomeViewModel` branché sur le moteur de recherche agrégée + cache Room.
2. Écran fiche média + feuille « Télécharger » câblée au `DownloadManager`.
3. `PlayerActivity` complète (gestes, verrouillage) branchée sur `EsPlayer`.
4. Repo officiel publié (GitHub Pages) + signatures réelles des `.esx` de démo.
5. Addons HTTP distants (saveur Stremio), backend mpv, profils de base de perf.
