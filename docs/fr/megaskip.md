# Megaskip — saut intelligent des segments

> Livré en **0.22.0**. Adapté à l'architecture réelle d'Endless Sea (Media3 1.5.1,
> Hilt, Room v5, Android 8+) — pas une réécriture théorique.

## 1. Ce que fait le Megaskip

| Élément | Détail |
|---|---|
| **Segments en ligne** | Intro, récap, générique, aperçu — récupérés auprès de bases communautaires |
| **Bases interrogées** | **TheIntroDB** (`api.theintrodb.org/v2/media`), **IntroDB** (`api.introdb.app/intro`), **AniSkip** (`api.aniskip.com/v2/skip-times`, anime) |
| **Résolution d'identifiants** | `tmdb` / `imdb` / `tvdb` / `mal` / `anilist` depuis les métadonnées de la fiche ; sinon IMDb → MAL via **AniZip**, titre → MAL via **Jikan** |
| **Cache** | Table Room `skip_cache` (fraîcheur 30 j) → un épisode vu une fois se saute **hors-ligne** |
| **Boutons personnalisés** | Table Room `skip_buttons` : libellé + durée (ex. « Opening » 85 s, « Filler » 120 s) — toujours disponibles hors-ligne |
| **Saut automatique** | Réglable par type (intro ✓, récap ✗, générique ✓, aperçu ✗ par défaut) avec délai 0–10 s |
| **UI lecteur** | Pastille « Passer l'intro · 12 s » (côté opposé au mégaskip) + rangée de boutons personnalisés ; appui long sur la pastille « +85 s » = éditeur |
| **Réglages** | Réglages → Lecteur → **Megaskip (segments en ligne)** + **Gestes et surimpressions** |

## 2. Fichiers ajoutés / modifiés

```
app/src/main/java/dev/endlesssea/app/skip/
├── SkipModels.kt        SkipType, SkipSegment, SkipTarget, CustomSkipButton, SkipSettings
├── SkipProviders.kt     TheIntroDB · IntroDB · AniSkip · AniZip · Jikan
└── SkipRepository.kt    hors-ligne d'abord : cache → réseau → fusion → cache

app/src/main/java/dev/endlesssea/app/ui/player/
├── SkipUi.kt            SkipSegmentPill · MegaskipRow · CustomSkipDialog
└── PlayerActivity.kt    pastille, rangée, stats, zoom au pincement, inverseur volume/luminosité

data/src/main/java/dev/endlesssea/data/db/
├── SkipEntities.kt      skip_cache · skip_buttons
├── Daos.kt              SkipDao
└── EsDatabase.kt        v4 → v5

app/.../di/AppModule.kt  MIGRATION_4_5 + provider SkipDao
app/.../di/AppPrefs.kt   réglages Megaskip + gestes + statistiques
player/.../EsPlayer.kt   PlayerStats (analytics), vitesse 0,25×–4× à pitch corrigé
```

## 3. Fusion et priorité

1. Lecture du **cache** (immédiat, hors-ligne).
2. Sinon, interrogation **parallèle** des bases activées (timeout 8 s, jamais bloquante).
3. **Fusion par type** : un seul segment par type, le fournisseur le plus prioritaire
   gagne (`theintrodb` → `introdb` → `aniskip` → `local`).
4. Mise en cache pour les lectures suivantes.

## 4. Pièges évités

- **Media3 1.5.1**, pas 1.11 : on garde `EsPlayer` + overlays Compose maison (pas de
  `media3-ui-compose`), donc aucune montée de version risquée d'AndroidX.
- **AniSkip exige `episodeLength`** : la requête attend (au plus 6 s) une durée exploitable
  avant d'être envoyée, sinon 400/404.
- **Bornes nulles** (`start_ms: null` / `end_ms: null` chez TheIntroDB) = début / fin du média.
- **Anti-boucle** : un segment sauté est mémorisé (`type:startMs`) — le saut ne se répète pas.
- **Aucune télémétrie** : les appels partent du client, sans identifiant utilisateur.

## 5. Réglages correspondants

- Réglages → Lecteur → **Megaskip (segments en ligne)** : saut auto par type, délai,
  choix des bases, pastille « Passer », boutons personnalisés, vidage du cache.
- Réglages → Lecteur → **Gestes et surimpressions** : zoom au pincement,
  inversion volume/luminosité, statistiques de lecture.
