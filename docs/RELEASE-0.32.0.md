# EndlessSea 0.32.0 — Téléchargements, DASH et extensions

Cette version reprend le travail livré sur `main` après la préparation 0.31.0. Le workflow de publication relance les tests JVM et assemble l'APK avant de créer la release.

## Bibliothèque et lecture

- Au premier lancement, l'application demande un dossier. Les vidéos déjà présentes dans `Movies/EndlessSea` et dans MediaStore sont rescannées, même sans dossier choisi.
- Un changement de cadrage ne renvoie plus la lecture au début.
- Un double-appui au centre met en pause. Les côtés gardent le saut.
- Une vidéo finie (fin, 90 % ou moins de 15 secondes restantes) est marquée vue, épisode par épisode. « Reprendre » affiche la saison et l'épisode.
- Mode image « Plus net ».
- Suivi et métadonnées sont repliés. La bande-annonce a sa carte.

## Téléchargements

- « Tout télécharger » montre les serveurs de la fiche, puis la langue et la qualité. Si le choix n'existe pas, un autre lien est pris. Un serveur exclu n'est jamais utilisé en repli.
- L'onglet Téléchargements a des filtres Langue et Serveurs.
- Les manifestes DASH statiques se téléchargent : fichier direct, ou segments `SegmentTemplate` / `SegmentList`. L'audio séparé est enregistré à côté et relu avec la vidéo.
- Les flux DASH en direct et les pistes protégées par DRM sont refusés. Aucun contournement n'est ajouté.
- Les sous-titres HLS `EXT-X-MEDIA` et ceux choisis sur la fiche sont enregistrés à côté de la vidéo.
- Les dialogues ASS/SSA sont affichés en SRT. Le rendu complet (polices, dessins, animations) n'est pas inclus, pour ne pas alourdir les petits appareils.
- « Économie de données » n'ouvre qu'une connexion. Sous environ 384 Mo de mémoire libre, le plafond baisse tout seul.

## Extensions et métadonnées

- Le dépôt Extensions FR déjà publié est ajouté automatiquement et mémorisé hors de la base. Il n'a plus à être collé à chaque ouverture. Le supprimer soi-même l'empêche de revenir.
- Guide développeur : `docs/en/extension-dev-guide.md`.
- Si un épisode n'a ni titre ni image, un appel AniZip ou Jikan peut compléter la fiche. TMDB ne charge qu'une saison, et seulement si une clé est déjà enregistrée et qu'il manque des images.

## Installation

Télécharger **endless-sea-0.32.0-debug.apk**.

- Android 8.0 / API 26 minimum.
- `versionName = 0.32.0-debug` ; `versionCode = 32`.
- Identifiant : `dev.endlesssea.app.debug`.
- APK **debug**, signé avec la clé debug fixe publique du dépôt ; ce n'est pas une version de production signée avec une clé privée.
- La mise à jour peut s'installer par-dessus les versions debug précédentes de même signature. Ne pas désinstaller ; sauvegarder les données importantes avant mise à jour.
- Vérifier l'archive avec `sha256sum -c SHA256SUMS.txt`.

## Limites

- Aucun essai sur téléphone n'est revendiqué pour cette publication. Le DASH, la reprise de cadrage et le dépôt d'extensions doivent encore être vérifiés sur appareil.
- Les sous-titres image PGS ne sont pas extraits dans un fichier séparé. Media3 peut les afficher s'ils sont déjà dans la vidéo.
- La navigation Compose complète n'est pas exécutée dans l'émulateur de CI. Un workflow séparé couvre des tests instrumentés légers et ne bloque pas cette release.
