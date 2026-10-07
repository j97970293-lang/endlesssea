# EndlessSea 0.26.0 — Refonte du lecteur et suivi local

## Nouveautés

- Lecteur Défaut réorganisé : titre et retour en haut, recul de 10 secondes / lecture / avance de 10 secondes au centre, mégasauts et paramètres au-dessus de la progression.
- Six thèmes originaux en plus de Défaut : Zen, Orbit, Compact Bar, Neon Frame, Split Controls et Floating Cards. Les anciennes préférences de thèmes tiers reviennent à Défaut.
- Barre de progression personnalisable, playlist enrichie et lecture locale prioritaire pour les épisodes téléchargés.
- Bandes-annonces intégrées à l'application : vidéos HTTPS directes et intégrations YouTube, Vimeo ou Dailymotion, avec prise en charge du plein écran.
- Matching des fiches streaming et des séries locales : titre recherchable, résultats avec année et vignette, puis confirmation du rattachement.
- Correspondance automatique des épisodes facultative et limitée à la saison choisie. À 90 % de lecture, le numéro entier devient la progression ; une relecture ne rajoute pas un épisode. Les spéciaux, numéros inconnus et autres saisons sont ignorés.
- « Tout marquer vu » avec confirmation sur les fiches streaming, locales et téléchargées. Cette action ne modifie que l'historique local, pas le tracker.
- Suppression par balayage dans l'historique, avec confirmation ; suppression d'une entrée, d'une série, des épisodes vus ou des anciennes entrées.
- Accueil simplifié et recherche directe dans Explorer avec suggestions locales.

## Corrections et fiabilité

- Les chemins de lecture locale partagent une seule préparation de playlist et un identifiant de série stable pour la fiche, l'historique et le suivi.
- Les téléchargements récupèrent la saison, le numéro exact, la durée et la vignette depuis les épisodes enregistrés lorsqu'ils sont disponibles.
- Shikimori : création avec user_id et modification d'une entrée existante par PATCH, au lieu d'une création répétée.
- MyAnimeList : renouvellement après HTTP 401, conservation du refresh token renouvelé et utilisation de l'expiration réelle renvoyée par le serveur.
- Les synchronisations sont sérialisées ; une réponse tardive ne doit pas effacer une progression plus récente en attente.
- Migration de la base Room v7 vers v8. Les rattachements existants sont conservés, avec correspondance automatique désactivée par défaut.

## Installation

Télécharger **endless-sea-0.26.0-debug.apk**.

- Android 8.0 / API 26 minimum.
- versionName : `0.26.0-debug` ; versionCode : `26`.
- Application : `dev.endlesssea.app.debug`.
- APK signé avec la même clé de debug publique que celle du dépôt et des distributions précédentes. Il permet une mise à jour par-dessus une installation utilisant le même identifiant et la même signature.
- Il s'agit d'un **APK debug**, pas d'une version de production signée avec une clé privée de distribution.

Pour vérifier le téléchargement :

```sh
sha256sum -c SHA256SUMS.txt
```

## Validation et limites

- 107 tests JVM réussis, aucun échec, sur six modules disposant de tests ; le module data n'a pas de tests JVM.
- Compilation Android et assemblage de l'APK debug validés localement avec JDK 17 et SDK 35.
- Vérification SQLite des SQL de migration v7→v8 et de la conservation des rattachements réussie. Elle ne remplace pas un test instrumenté Android/Room.
- Aucun test visuel sur téléphone ou émulateur dans cet environnement : gestes, plein écran WebView et mise à jour de base sur appareil restent à vérifier. Voir `docs/VERIFICATION-REFONTE.md` dans les sources.
- La connexion OAuth navigateur complète n'est pas incluse : elle nécessite des clients et URI de retour enregistrés auprès des services. Connexion par jeton existante conservée ; aucun identifiant OAuth fictif ni secret client embarqué.
- Un rattachement par fiche ; pas de lecture de la progression déjà présente sur le service lors du rattachement. Vérifier le titre et la saison distante avant d'activer le suivi automatique.
- Les hébergeurs peuvent refuser l'intégration d'une bande-annonce. Aucun contournement ni extraction YouTube.
