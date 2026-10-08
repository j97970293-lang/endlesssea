# EndlessSea 0.27.0 — Téléchargements, sauvegardes et interface

## Téléchargements

- Retour d'une estimation numérique HLS avant téléchargement : jusqu'à 12 segments répartis dans toute la vidéo, pondération par durée et plage indicative lorsque les débits observés varient. La qualité demandée est utilisée pour l'estimation et le téléchargement.
- Petites playlists : addition des tailles annoncées de tous les segments. Les données insuffisantes et flux non compatibles restent signalés plutôt qu'inventés. L'estimation n'est pas une garantie de taille finale ; les bornes indicatives peuvent être dépassées.
- Fichiers directs : vérification des tailles annoncées par le serveur. Pendant le téléchargement HLS, affichage des octets reçus et des segments, sans présenter l'estimation comme une mesure réelle.
- Reprise HLS protégée par l'identité du plan et des points de contrôle de fichier ; les reprises non vérifiables sont refusées sans supprimer automatiquement le partiel.
- Correction du déchiffrement AES-128 : vecteur d'initialisation implicite, IV hexadécimaux courts et retrait du remplissage entre les segments.

**Important :** les anciens partiels HLS chiffrés doivent être annulés puis retéléchargés, car ils peuvent déjà contenir des octets altérés. Les fichiers terminés ne sont pas réparés automatiquement. La rotation de clés, les initialisations fMP4 chiffrées et SAMPLE-AES/DRM ne sont pas résolus par cette correction.

## Sauvegardes et fichiers locaux

- Sauvegardes versionnées avec validation, aperçu et fusion avant restauration ; conservation des opérations lors de la recréation de l'écran et protection contre les doubles actions.
- Fusion des variantes de catégories sans perte des éléments importés.
- Reconstruction des playlists locales lors de la reprise, gestion des épisodes fractionnaires et des grands numéros.
- Scan des dossiers sans attente circulaire des permis de concurrence ; lectures JSON locales bornées et propagation de l'annulation.

Les copies automatiques locales ne sont pas une sauvegarde quotidienne garantie en arrière-plan. Les fichiers vidéo et permissions de stockage ne sont pas transférés par les sauvegardes.

## Interface

- Indicateurs de chargement, placeholders, entrées des cartes et transitions harmonisés ; prise en compte de la préférence de mouvement et du réglage Android.
- Huit logos conservés et sélectionnables ; bleu et blanc par défaut. Images optimisées sans supprimer les alternatives.

## Installation

Télécharger **endless-sea-0.27.0-debug.apk**.

- Android 8.0 / API 26 minimum.
- `versionName = 0.27.0-debug`, `versionCode = 27`.
- Identifiant : `dev.endlesssea.app.debug`.
- Même clé debug fixe du dépôt que la distribution 0.26.0 : mise à jour possible sur une installation avec le même identifiant et la même signature. Sauvegarder les données importantes avant mise à jour ; ne pas désinstaller pour mettre à jour.
- Il s'agit d'un **APK debug**, pas d'une build de production signée avec une clé privée de distribution.

Vérification du fichier : `sha256sum -c SHA256SUMS.txt`.

## Validation et limites

Cette release est publiée après succès du workflow Android CI sur son commit : tests JVM des sept modules, assemblage debug et téléversement de l'APK. L'APK distribué provient de ce workflow.

La correction AES a aussi été vérifiée localement avec 14 nouveaux tests et 27 tests de régression, soit 41 tests réussis. Huit tests de déchiffrement/assemblage échouaient avant correction.

Aucun essai sur téléphone ou émulateur n'est revendiqué pour cette publication. La CI ne démontre ni la fluidité du rendu, ni la compatibilité de tous les hébergeurs, ni le fonctionnement des permissions SAF sur tous les appareils. Les checklists restent dans `docs/maintenance/`.
