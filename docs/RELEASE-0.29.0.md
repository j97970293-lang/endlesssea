# EndlessSea 0.29.0 — Reconstruction bibliothèque et lecteur, lot 1

**Premier lot intégré : la reconstruction complète n'est pas terminée.** Les grilles, les panneaux de fichiers et les thèmes de lecteur existants restent utilisés pendant la transition.

## Bibliothèque

- Trois espaces distincts : **Collection**, **Téléchargements**, **Dossiers**.
- Catégories de médias dans Collection, catégories personnelles de fichiers dans Dossiers.
- Navigation possédée par le ViewModel et identifiants stables, indépendants de l'ordre des onglets.
- Sélection conservée par SavedStateHandle lors des restaurations Android prises en charge ; ce n'est pas une préférence persistante après suppression de la tâche.
- Les filtres de Collection ne masquent plus la vue dédiée aux téléchargements.
- Retour à Dossiers si la catégorie personnelle sélectionnée a été supprimée.

## Lecteur

- Le ViewModel possède désormais l'attachement initial de la session.
- Recréer l'Activity ne consomme plus un second lancement vide pour préparer à nouveau le moteur.
- Une session indisponible affiche un message invitant à rouvrir la vidéo, au lieu de transmettre une liste de liens vide au moteur.
- Les sept thèmes restent disponibles. Le lecteur principal personnalisable et la restauration complète après mort du processus font partie des prochains lots, pas de cette publication.
- La politique de pause existante reste inchangée : aucune reprise automatique transparente après rotation n'est garantie par ce lot.

Aucune migration de base de données ni suppression des fichiers, historiques, catégories ou réglages existants dans ce lot. Les corrections de téléchargements et d'estimation de taille des versions précédentes sont conservées.

## Installation

Télécharger **endless-sea-0.29.0-debug.apk**.

- Android 8.0 / API 26 minimum.
- `versionName = 0.29.0-debug` ; `versionCode = 29`.
- Identifiant : `dev.endlesssea.app.debug`.
- **APK debug**, signé avec la clé fixe du dépôt, pas une build de production signée avec une clé privée.
- Mise à jour par-dessus une installation de même identifiant et même signature ; ne pas désinstaller. Sauvegarder les données importantes avant mise à jour.
- Les anciens partiels HLS chiffrés antérieurs à la correction AES doivent toujours être relancés ; les fichiers terminés ne sont pas réparés automatiquement.

Vérification : `sha256sum -c SHA256SUMS.txt`.

## Validation et limites

Publication uniquement après succès d'Android CI sur le commit de cette version : tests JVM des sept modules, assemblage debug et téléversement de l'APK. Le fichier distribué provient de cette CI, pas du workflow automatique Release historique en échec.

Le lot ajoute 20 tests ciblés : 12 sur la navigation de bibliothèque et 8 sur la propriété de session. Ils ont aussi réussi localement. Ces tests ne simulent pas un cycle de vie Android réel.

Aucun essai sur téléphone ou émulateur n'est revendiqué. Les prochaines étapes et la checklist appareil sont documentées dans `docs/maintenance/library-player-rebuild.md`.
