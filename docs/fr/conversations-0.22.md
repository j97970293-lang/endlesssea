# Suivi des 11 conversations → état dans le code (0.22.0)

> Chaque ligne = une conversation DeepSeek fournie par l'auteur du projet.
> Le travail est **adapté au code réel** (Media3 1.5.1, Hilt, Room v5, Android 8+),
> pas recopié tel quel — et **chaque lot est compilé** (`:app:assembleDebug`).

| # | Conversation (thème) | État | Détail |
|---|---|---|---|
| 1 | `9jzz39v6665vbga065` — lecteur complet (formats, gestes, thèmes, upscaling, megaskip) | 🟡 Partiel | **Megaskip livré** (3 bases + cache + boutons perso) · gestes livrés (pincement 1–3×, luminosité/volume inversables, multi-tap, vitesse 0,25–4×, stats) · restent : upscaling OpenGL maison dédié, thèmes de lecteur supplémentaires |
| 2 | `r6mc3qqgrd75dpbpls` — code du lecteur (PlayerTheme, thèmes, GL, Megaskip, module Hilt) | 🟡 Partiel | Megaskip implémenté dans l'architecture existante (pas de réécriture `media3-ui-compose` 1.11) — 11 habillages lecteur déjà présents |
| 3 | `w8t4xz67uc6s6mjwqu` — thème dynamique style Anymex partout + Liquid Glass + Réglages | ✅ Déjà en place | Accent → palette complète (`Theme.kt`), `Glass.kt`/`LiquidBackground.kt` thémés, sélecteur d'accent, mise à jour instantanée — vérifié |
| 4 | `x6lik1e5op16k2mlo3` — `EndlessSeaTopBar` (accueil, bibliothèque, explorer, télécharger) | ⚠️ En attente | **Conflit** : la barre du haut a été **volontairement supprimée** (`§barre-haut` dans `MainActivity.kt`) au profit de boutons flottants — demander l'accord avant de la réintroduire |
| 5 | `csilzujjpixpbv8clw` — dossiers locaux = séries (cover.jpg, details.json, episodes.json) | 🟡 Partiel | `cover.jpg` + `details.json` déjà lus par `LocalVideos.kt` ; **`episodes.json` à ajouter** (titres/n° d'épisodes) |
| 6 | `4kfqk5qkgwis5l5xlz` — reconstruction de la bibliothèque (sources, onglets, sections) | ⬜ À faire | À brancher sur `LibraryScreen`/`LibraryViewModel` existants |
| 7 | `12i28xc39adg6x56ar` — épisodes téléchargés intégrés à la bibliothèque (même un seul) | ⬜ À faire | Table `download_tasks` déjà là ; manque le regroupement en « séries virtuelles » + badge |
| 8 | `yxxuc6nkoc7dak3eax` — paramètres d'extensions : installées / non installées / dépôts + filtres | ✅ **Livré (0.22.0)** | 3 onglets + compteurs, recherche, filtres par langue/type/statut/mises à jour |
| 9 | `35fnp9bnze6m25vg4e` — « Reprendre la lecture » en cartes d'historique | ✅ **Livré (0.22.0)** | Vignette 16:9, progression, épisode, temps restant, « hier / il y a 2 h » |
| 10 | `dzao16x9yrn40aipl0` — système de téléchargement (espace disque, backoff, intégrité, débit, nettoyage) | ⬜ À faire | `DownloadManager`/`SegmentEngine` déjà solides ; manquent les 5 garde-fous |
| 11 | `b8n1lyrfk3stmmhf7t` — trackers (AniList, MAL, TMDB, Shikimori…) + comptes + marquage auto + bandes-annonces | ⬜ À faire | Aucune table tracker aujourd'hui ; `media.externalIdsJson` sert de base |

## Légende

- ✅ **Livré** — compilé et poussé.
- 🟡 **Partiel** — une partie existe déjà dans le dépôt, le reste est identifié.
- ⬜ **À faire** — planifié pour le prochain lot.
- ⚠️ **En attente** — nécessite un arbitrage de l'auteur (risque de régression volontaire).

## Ce qui est vérifié à chaque lot

```bash
JAVA_HOME=/opt/jdk17 ANDROID_HOME=/opt/android-sdk ./gradlew :app:assembleDebug
# → BUILD SUCCESSFUL, app-debug.apk (~25 Mo)
```

Aucun lot n'est poussé sans compilation complète (Kotlin + KSP/Hilt + D8).
