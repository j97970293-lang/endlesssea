# Fiche commune et rendu stable — après retour téléphone sur 7b43c9a

Le test utilisateur a invalidé la correction 7b43c9a : cadrage inopérant et image figée après changements répétés. Le document `player-live-controls.md` décrit cette tentative antérieure, pas une validation sur téléphone.

## Fiche commune

- Les clics sur les dossiers locaux ouvrent `details/{id}`, le même `DetailsScreen` / `DetailsViewModel` que les titres en ligne et téléchargés. L'ancienne route `localDetails/{folder}` reste compatible, mais rend également `DetailsScreen`. L'ancien écran local n'est plus appelé.
- `LocalDetailsRepository` alimente le modèle commun à partir du dossier, de Room et des métadonnées manuelles. Il rescane uniquement le dossier demandé, pas tout le stockage. Identités `local:<folder>` et URI des épisodes inchangées : historique et liens de suivi conservés.
- Même présentation, fiche, synopsis, favoris, bibliothèque, reprise, recherche, saisons et lignes d'épisodes. Les fichiers locaux sont signalés hors ligne ; aucun appel d'extension `local` ni faux bouton de téléchargement. Fichier inaccessible : message explicite, sans bascule inventée vers une source.
- Métadonnées/affiche et association en ligne sont accessibles dans le menu de la fiche commune ; titre/marqueurs de l'épisode via son menu. Les images d'épisodes locaux restent des frames vidéo, pas l'affiche.
- La suppression d'un fichier local original est distinguée d'un téléchargement géré et exige une confirmation explicite. Aucun nettoyage de fichiers à la migration.
- Un épisode sans numéro n'est pas inventé comme « épisode 1 / saison 1 ». Un numéro fractionnaire est conservé. La file complète reste indépendante des filtres de recherche.

## Rendu

- Programme GLES permanent (`LiveVideoEffect`), installé une fois avant préparation. Les styles, couleurs et netteté mettent à jour un snapshot atomique lu par frame. Plus de compilation/remplacement de chaîne GL à chaque modification ; une seule passe GPU.
- Cadrage calculé depuis le format source (dimensions, pixels non carrés, rotation), indépendamment de la taille de sortie des effets. Le viewport natif reste de taille fixe ; fit/crop/stretch et zoom sont des transformations visuelles à l'intérieur d'une zone découpée. Changer le cadrage réinitialise les zooms supplémentaires et le déplacement.
- L'agrandissement est désormais un zoom d'affichage, pas un agrandissement de framebuffer : aucune résolution source supplémentaire n'est revendiquée. Les presets de netteté restent des traitements légers, pas une super-résolution IA.
- Les modifications en pause rafraîchissent la frame sur un média seekable/prêt ; les modifications en lecture ne déclenchent pas de seek.

## Vérifications exécutées avant CI

- 19 tests JVM du lecteur : les 12 interactions précédentes et 7 tests de cadrage/paramètres.
- `tools/verify_live_shader.py` : compilation/link GLES et 1 000 changements de paramètres avec rendu et retour au neutre, zéro erreur GL. Exécuté sur Mesa EGL hors Android, **pas sur le GPU du téléphone**.
- Six nouveaux tests de projection locale ajoutés à la suite Android : identités, miniature vidéo, titres manuels, saisons, fractionnaires, inconnus et 1 000 fichiers.

## Validation téléphone indispensable

Contenir/Remplir/Étirer sur une vidéo dont le ratio diffère de l'écran ; répétition des styles/zoom/filtres en lecture et pause ; portrait/paysage et Texture/Surface ; pause/reprise après chaque série de changements. Vérifier aussi que le dossier local ouvre exactement la fiche habituelle, puis association, édition, redémarrage et reprise hors réseau.
