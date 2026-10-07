# Endless Sea — état des exigences (11 conversations) → 0.24.0

Document de suivi : chaque conversation DeepSeek est confrontée au code réel du
dépôt. Une ligne = une conversation, avec son identifiant de partage, son état
et ce qui a été fait concrètement (adapté, jamais copié tel quel).

| # | Conversation | État | Réalisation dans le dépôt |
|---|---|---|---|
| 1 | `9jzz39v6665vbga065` — refonte complète du lecteur (formats, gestes, skins, upscaling, Megaskip) | ✅ **Livré (0.22 – 0.25)** | HLS/DASH/AV1/HEVC/HDR, gestes complets (double appui, glisser luminosité/volume, pincer 1×–3×), vitesse 0,25×–4× avec pitch, **8 thèmes complets** (`ui/player/themes/`, chacun sa mise en page), **sous-titres affichés** (SubtitleView) **et décalables ±0,5 s** (réécriture du fichier SRT/VTT), **boost audio jusqu'à 200 %** (LoudnessEnhancer), Megaskip TheIntroDB v2 + IntroDB + AniSkip avec cache Room et boutons personnalisés, surimpression de statistiques, **upscaling par niveaux** (OFF/AUTO/PERFORMANCE/QUALITY) et **garde thermique** (`ThermalMonitor`, réduction automatique en cas de chauffe) |
| 2 | `r6mc3qqgrd75dpbpls` — code du lecteur (PlayerTheme, thèmes, contrôles) | 🔄 **Repris et livré (0.25)** | `ui/player/themes/` : interface `PlayerTheme` (`TopControls` / `CenterControls` / `BottomControls`), `PlayerControlsState` + `PlayerControlsActions`, `ThemeProvider` (8 thèmes : Endless Sea, Netflix, Crunchyroll, YouTube, VLC, Plex, Apple TV+, Gaming), atomes partagés (SkinSeekBar, SkinPlayButton, MegaSkipCluster, AllTools) — chaque thème a sa propre disposition, ses formes et ses accents |
| 3 | `w8t4xz67uc6s6mjwqu` — thème dynamique style Anymex | ✅ **Livré (0.25)** | Une seule couleur d'accent pilote toute la palette Material 3 (`Theme.kt` §accent-partout : primary, secondaire, tertiaire, conteneurs, surfaceTint, outline), **accent par défaut `#4FC3F7`**, sélecteur d'accent dans les réglages, mise à jour instantanée ; AMOLED = noir pur + surfaces `#0A0A0A` ; les dernières couleurs figées des cartes et des placeholders d'image passent au thème |
| 4 | `x6lik1e5op16k2mlo3` — `EndlessSeaTopBar` réutilisable | ✅ **Livré (0.25)** | `ui/components/EndlessSeaTopBar.kt` : barre en verre liquide (coins 24 dp, cercle d'icône dégradé, titre, sous-titre, boutons recherche/menu). Posée sur **Accueil** (titre = source, menu = feuille des sources, loupe → recherche), **Bibliothèque** (titre + compteurs, menu = nouvelle catégorie), **Explorer** (loupe repliable) et **Télécharger** ; les anciens boutons flottants de l'accueil sont retirés |
| 5 | `csilzujjpixpbv8clw` — dossiers locaux = séries (façon Aniyomi) | ✅ **Livré (0.23)** | `LocalVideoScanner`, `Série/cover.jpg` + `details.json` (éditable, réécrit dans le dossier) + `episodes.json`, regex de numéro d'épisode, tri par épisode, fiche de série locale |
| 6 | `4kfqk5qkgwis5l5xlz` — reconstruction de la bibliothèque | ✅ **Livré (0.23)** | Sources multiples (puces), onglets Tout / En cours / Terminé, sections « Récemment ajoutés » et « Reprendre », badges de source, états vide/chargement |
| 7 | `12i28xc39adg6x56ar` — téléchargements intégrés à la bibliothèque | ✅ **Livré (0.23)** | Table `downloaded_episodes`, fusion par titre avec les séries locales, épisode isolé = « série » virtuelle, badge « Téléchargé », lecture hors-ligne |
| 8 | `yxxuc6nkoc7dak3eax` — écran extensions (3 onglets + filtres) | ✅ **Livré (0.23)** | Onglets Installées / Non installées / Dépôts, filtres langue, type, contenu, dépôt, mises à jour, recherche, activation/désactivation |
| 9 | `35fnp9bnze6m25vg4e` — cartes « Reprendre la lecture » | ✅ **Livré (0.22 – 0.23)** | Table `watch_history`, `HistoryCard` (vignette 16:9, barre de progression, temps restant, horodatage), filtre < 95 % |
| 10 | `dzao16x9yrn40aipl0` — gestionnaire de téléchargement | ✅ **Livré (0.23 – 0.24)** | Espace disque vérifié avant départ, reprise/backoff, limite de bande passante (Ko/s), service au premier plan + wake-lock, priorisation de la file, **vérification d'intégrité SHA-256**, purge automatique des fichiers anciens (3/7/30/90 jours) et des temporaires orphelins, bouton « Nettoyer maintenant » |
| 11 | `b8n1lyrfk3stmmhf7t` — trackers et services de suivi | ✅ **Livré (0.24)** | Comptes AniList / MyAnimeList / Shikimori / TMDB stockés en base locale (Room v7 : `tracker_accounts`, `tracker_links`), jetons rafraîchis automatiquement (MAL), écran « Comptes & suivi », bloc Suivi de la fiche (rattachement manuel, +1 épisode, statuts), marquage automatique à 90 % depuis le lecteur avec file d'attente hors ligne, affiches/bannières/bandes-annonces TMDB |

## Rappel des prochaines étapes

1. **Conversation 3** : une seule couleur d'accent pilote toute l'interface.
2. **Conversation 4** : `EndlessSeaTopBar` sur les quatre écrans principaux.

## Notes d'adaptation

- Media3 du dépôt : **1.5.1** (les conversations supposaient 1.11 — les API ont
  été traduites, pas copiées).
- Les jetons et clés d'API des services de suivi restent **sur l'appareil**
  (base locale), aucun compte n'est requis pour utiliser l'application.
- Megaskip : TheIntroDB v2 (id de la série + saison/épisode + durée), AniSkip v2
  (`episodeLength` obligatoire), cache en base, repli hors ligne sur les boutons
  de saut personnalisés.
