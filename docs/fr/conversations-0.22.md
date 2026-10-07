# Suivi des 11 conversations → état dans le code (0.23.0)

> Chaque ligne = une conversation DeepSeek fournie par l'auteur du projet.
> Le travail est **adapté au code réel** (Media3 1.5.1, Hilt, Room v5, Android 8+),
> pas recopié tel quel — et **chaque lot est compilé** (`:app:assembleDebug`).

| # | Conversation (thème) | État | Détail |
|---|---|---|---|
| 1 | `9jzz39v6665vbga065` — lecteur complet (formats, gestes, thèmes, upscaling, megaskip) | 🟡 Partiel | **Megaskip livré** (3 bases + cache + boutons perso) · gestes livrés (pincement 1–3×, luminosité/volume inversables, multi-tap, vitesse 0,25–4×, stats) · restent : upscaling OpenGL maison dédié, thèmes de lecteur supplémentaires |
| 2 | `r6mc3qqgrd75dpbpls` — code du lecteur (PlayerTheme, thèmes, GL, Megaskip, module Hilt) | 🟡 Partiel | Megaskip implémenté dans l'architecture existante (pas de réécriture `media3-ui-compose` 1.11) — 11 habillages lecteur déjà présents |
| 3 | `w8t4xz67uc6s6mjwqu` — thème dynamique style Anymex partout + Liquid Glass + Réglages | ✅ Déjà en place | Accent → palette complète (`Theme.kt`), `Glass.kt`/`LiquidBackground.kt` thémés, sélecteur d'accent, mise à jour instantanée — vérifié |
| 4 | `x6lik1e5op16k2mlo3` — `EndlessSeaTopBar` (accueil, bibliothèque, explorer, télécharger) | ⚠️ En attente | **Conflit** : la barre du haut a été **volontairement supprimée** (`§barre-haut` dans `MainActivity.kt`) au profit de boutons flottants — demander l'accord avant de la réintroduire |
| 5 | `csilzujjpixpbv8clw` — dossiers locaux = séries (cover.jpg, details.json, episodes.json) | ✅ **Livré (0.23.0)** | `episodes.json` lu (titres par numéro, tolérant aux deux écritures), tri par numéro d'épisode, libellés « Ép. 3 · Le début », **écriture de `details.json` dans le dossier** (titre, synopsis, auteur, genres) depuis la fiche locale |
| 6 | `4kfqk5qkgwis5l5xlz` — reconstruction de la bibliothèque (sources multiples, onglets, sections, badges) | 🟡 Presque | Sources **séparées** (Interne / Carte SD / Téléchargements) avec puces filtrantes, onglets d'état de visionnage **Tout / En cours / Terminé**, cartes enrichies (nombre d'épisodes, emplacement, numéro d'épisode), sections « Reprendre » + « Récemment ajoutés » déjà livrées côté Accueil en 0.22 (conv 9) — il manque la recherche dans la bibliothèque et les sections *dans* la grille |
| 7 | `12i28xc39ag6x56ar` — téléchargements intégrés à la bibliothèque (même un épisode) | ✅ **Livré (0.23.0)** | Groupe = fiche si connue, sinon dossier de téléchargement (un épisode isolé = une série d'un épisode), puce « Téléchargés (n) », fiche du groupe avec lecture hors-ligne et **suppression par épisode**, pastille « téléchargé » alimentée depuis la base dès le démarrage |
| 8 | `yxxuc6nkoc7dak3eax` — paramètres d'extensions : installées / non installées / dépôts + filtres | ✅ **Livré (0.22.0)** | 3 onglets + compteurs, recherche, filtres par langue/type/statut/mises à jour |
| 9 | `35fnp9bnze6m25vg4e` — « Reprendre la lecture » en cartes d'historique | ✅ **Livré (0.22.0)** | Vignette 16:9, progression, épisode, temps restant, « hier / il y a 2 h » |
| 10 | `dzao16x9yrn40aipl0` — fiabilisation du gestionnaire de téléchargement | ✅ **Livré (0.23.0)** | Espace disque vérifié avant départ (+64 Mo de marge), SHA-256 du fichier final (colonne `sha256`, migration v5→v6), limitation de bande passante partagée (seau à jetons) réglable dans les Paramètres, nettoyage auto (3/7/30/90 jours) + purge des `.part` orphelins au démarrage, **service au premier plan réellement démarré** (wake-lock + notifications, arrêt automatique au repos) |
| 11 | `b8n1lyrfk3stmmhf7t` — services de suivi gratuits (AniList, MAL, TMDB, Shikimori) | ✅ **Livré (0.23.0)** | Paquet `app/tracking/` : 4 services (AniList GraphQL + OAuth implicite, MyAnimeList OAuth2 **PKCE** complet, Shikimori OAuth2, TMDB clé v3/v4), écran « Comptes & suivi », rattachement manuel d'une fiche (recherche + vignettes), marquage **automatique** des épisodes vus à 90 % (une seule fois par épisode), statuts En cours/Terminé/À voir/Abandonné, **bandes-annonces + affiches TMDB** quand la source ne les fournit pas |

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

## Journal des lots

- **0.22.0** — Megaskip (3 bases + cache hors-ligne + boutons perso) · gestes du lecteur · cartes d'historique (conv. 1, 2, 9)
- **0.22.0** — écran Extensions en 3 onglets + filtres (conv. 8)
- **0.23.0** — `episodes.json`/`details.json` dans les dossiers locaux (conv. 5) · épisodes téléchargés dans la bibliothèque (conv. 7) · sources + statuts de la bibliothèque (conv. 6, sections « Reprendre » + « Récemment ajoutés ») · fiabilisation du téléchargement : espace disque, SHA-256, débit, nettoyage auto, service au premier plan (conv. 10) · services de suivi AniList/MAL/Shikimori/TMDB : comptes, marquage auto des épisodes vus, bandes-annonces (conv. 11)
