# EndlessSea 0.31.0 — Lecture, partage et découverte

Cette version rassemble les changements livrés sur `main` depuis la 0.30.0. Android CI a réussi sur le commit parent ; le workflow de publication relance les tests JVM et assemble l'APK avant de créer la release.

## Lecteur

- Reprise après mort du processus : seuls l'identité stable du média, l'épisode et la position sont conservés. Les liens de flux, en-têtes et cookies ne sont pas stockés.
- Quatre cadrages explicites, dont un ajustement du cadre entier sur un fond léger, avec zoom et déplacement bornés.
- Les anciens habillages du lecteur sont retirés. Le thème conservé est Cinéma ; les préférences obsolètes y sont migrées.
- Mode Surface sans effets GPU, pour laisser le chemin HDR natif de l'appareil lorsque la source, le décodeur et l'écran le permettent.
- Export d'un sous-titre sidecar, sans réencoder la vidéo.

## Lecture, partage et fiches

- Les serveurs sont regroupés par langue audio, et la qualité ainsi que la langue préférées sont prises en compte au lancement.
- La rangée « Continuer » reprend l'épisode exact, pas seulement la fiche.
- Liens de partage d'un média et ouverture externe.
- Actions groupées sur les épisodes d'une fiche.
- Préférence de langue pour les métadonnées TMDB.
- Galerie de logos dédiée dans les paramètres.

## Explorer

- Sections Programmes et Actualités, uniquement lorsque une extension déclare une catégorie dont la clé ou le titre correspond (planning, sorties, actualités). Aucun contenu n'est inventé.

## Installation

Télécharger **endless-sea-0.31.0-debug.apk**.

- Android 8.0 / API 26 minimum.
- `versionName = 0.31.0-debug` ; `versionCode = 31`.
- Identifiant : `dev.endlesssea.app.debug`.
- APK **debug**, signé avec la clé debug fixe publique du dépôt ; ce n'est pas une version de production signée avec une clé privée.
- La mise à jour peut s'installer par-dessus les versions debug précédentes de même signature. Ne pas désinstaller ; sauvegarder les données importantes avant mise à jour.
- Vérifier l'archive avec `sha256sum -c SHA256SUMS.txt`.

## Limites

- Aucun essai sur téléphone ou émulateur n'est revendiqué. Le HDR, le cadrage, la reprise après mort du processus et les rubriques d'extension doivent encore être vérifiés sur appareil.
- Les rubriques Programmes et Actualités restent vides si l'extension active ne déclare pas ces catégories.
- La connexion navigateur AniList, MAL ou TMDB n'est pas ajoutée dans cette version.
