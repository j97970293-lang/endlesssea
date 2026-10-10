# EndlessSea 0.30.0 — Bibliothèque, fiches locales et lecteur

Cette version poursuit le chantier Bibliothèque/lecteur après la 0.29.0. Elle rassemble les changements livrés sur `main` depuis cette version ; la reconstruction complète de ces espaces reste progressive.

## Bibliothèque et découverte

- Séparation plus nette entre **Collection**, **Téléchargements** et **Dossiers**, avec état de navigation plus stable et recherche exacte d'épisodes en ligne et hors ligne.
- Les dossiers locaux ouvrent la fiche commune des médias. Les métadonnées, affiches, titres, marqueurs de générique et association à une fiche en ligne peuvent être gérés sans changer les identifiants des fichiers ni leur historique.
- Édition du type de média pour les groupes locaux et téléchargés ; les marqueurs de série sont hérités par épisode sans écraser les marqueurs spécifiques.
- Import paginé des listes AniList et association manuelle des titres, lorsque le compte AniList est déjà connecté. Les imports TMDB restent explicites.
- Accueil et Explorer mieux synchronisés avec la source sélectionnée ; les réponses tardives d'une ancienne source ne remplacent plus la source active.

## Lecteur et lecture locale

- Contrôles de lecture stabilisés pendant le buffering, les rafales de seeks et les changements d'épisode ; avance automatique vers l'épisode suivant en fin de flux.
- Cadrage Contenir/Remplir/Étirer, zoom et déplacement bornés, commandes de sous-titres et ajustements d'image en direct. La chaîne d'effets vidéo est conservée pour éviter de la reconstruire à chaque réglage.
- Association de dossiers locaux à des titres en ligne, affichage commun des épisodes et amélioration de la gestion des téléchargements vidéo terminés.

## Fiabilité

- Validation renforcée des sauvegardes et du manifeste de l'extension de démonstration.
- La CI de publication exécute les tests JVM des sept modules et assemble l'APK avant de créer cette release.

## Installation

Télécharger **endless-sea-0.30.0-debug.apk**.

- Android 8.0 / API 26 minimum.
- `versionName = 0.30.0-debug` ; `versionCode = 30`.
- Identifiant : `dev.endlesssea.app.debug`.
- APK **debug**, signé avec la clé debug fixe publique du dépôt ; ce n'est pas une version de production signée avec une clé privée.
- La mise à jour peut s'installer par-dessus les versions debug précédentes de même signature. Ne pas désinstaller ; sauvegarder les données importantes avant mise à jour.
- Vérifier l'archive avec `sha256sum -c SHA256SUMS.txt`.

## Limites et validation

- La reconstruction Bibliothèque/lecteur n'est pas terminée : les sept thèmes existants restent disponibles et la restauration complète du lecteur après mort du processus n'est pas incluse.
- La connexion AniList/TMDB par navigateur n'est pas incluse ; elle dépend encore de la configuration des applications OAuth/API. Les méthodes de connexion existantes restent nécessaires.
- Android CI doit réussir sur le commit tagué avant publication. Aucun essai sur téléphone ou émulateur n'est revendiqué ; les effets GPU, l'affichage et le parcours de connexion doivent encore être vérifiés sur appareil.
