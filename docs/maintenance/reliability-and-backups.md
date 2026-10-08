# Fiabilité locale et sauvegardes

État du code : 0.26.0 / versionCode 26, travail du 8 octobre 2026.
Aucune release, aucun tag et aucune migration de schéma Room ne sont nécessaires pour ces changements.

## Lecture locale

- L'accueil utilise désormais le même lancement local que la bibliothèque : file d'épisodes, identifiant de série, numéro/saison et repères intro/outro.
- Après un démarrage à froid, la reprise retrouve le dossier via l'historique ou l'URI SAF. Elle ne scanne que ce dossier, pas toute la carte SD. Sans dossier identifiable, elle propose uniquement le fichier choisi, sans inventer un identifiant de série.
- L'accès au fichier est vérifié hors du thread UI. Un fichier déplacé/supprimé ou une autorisation perdue produit un message invitant à vérifier le dossier, plutôt qu'un lancement aveugle.
- Une file ne mélange pas plusieurs séries. Les épisodes sont triés par saison/numéro, indépendamment des titres personnalisés. Les épisodes à quatre chiffres ne sont plus tronqués ; les spéciaux fractionnaires restent ordonnés mais ne deviennent pas des numéros entiers pour les trackers.
- Les scans libèrent leur permis d'accès avant d'attendre les sous-dossiers : suppression d'un interblocage possible avec huit branches imbriquées.
- Les annotations locales utilisent un vrai codec JSON : la clé URI n'est plus confondue avec le titre. Titres, couvertures et repères occupent leurs champs corrects ; accents, guillemets et virgules sont préservés. Les anciens tableaux à deux champs restent lisibles.

## Sauvegarde JSON v2

### Inclus

| Données | Portée |
|---|---|
| Bibliothèque | Toutes les catégories, favoris, statut de watchlist, genres personnalisés, tri et date d'ajout |
| Historique | Toutes les lignes, y compris les épisodes terminés et au-delà de 500 entrées |
| Fiches | Métadonnées de toutes les fiches en cache, titres/couvertures personnalisés et identifiants externes |
| Genres | Noms, visibilité et ordre |
| Catégories personnelles | Noms et listes de médias/URI associés |
| Annotations locales | Titre, référence de couverture, début/fin d'intro, début d'outro |
| Réglages sélectionnés | Thème clair/sombre/AMOLED/système, vitesse par défaut, reprise automatique, pas de saut, thème du lecteur, positions des barres/Megaskip et enregistrement de l'historique |

**Non inclus :** fichiers vidéo, fichiers image, sous-titres, extensions installées et leurs réglages, dépôts d'extensions, comptes/jetons/cookies, liens et synchronisations de trackers, tâches/segments de téléchargement, cache et boutons Megaskip, historique des dossiers/autorisations SAF, autres réglages (dont logo, police, fond d'écran et filtres).

Les références d'image/URI sont conservées, pas les fichiers correspondants. Sur un autre appareil, elles peuvent être invalides : recopier les médias/images nécessaires et réautoriser les dossiers. Réinstaller les extensions pour recharger les épisodes en ligne. Il ne s'agit **pas d'un clonage complet de l'application**.

Le JSON n'est pas chiffré : il contient notamment l'historique, les titres et les références de fichiers. Le conserver dans un emplacement privé, même s'il n'exporte pas les comptes et jetons.

### Validation et fusion

1. Lecture UTF-8 bornée à **20 Mio**, fermeture du flux même en cas d'erreur.
2. Contrôle de l'application, de la version, des sections, types, identifiants uniques, valeurs négatives, réglages autorisés et profondeur JSON. Aucun enregistrement pendant cette phase.
3. Aperçu des quantités, explication de la fusion et confirmation explicite. Possibilité d'exclure les réglages sélectionnés. Annuler ne modifie aucune donnée.
4. Fusion des tables **dans une transaction Room**. Aucun effacement préalable :
   - bibliothèque et fiches déjà présentes : les valeurs de cet appareil gagnent ;
   - historique : la ligne dont `updatedAt` est strictement plus récent gagne ; à égalité, l'appareil conserve sa ligne ;
   - genres : fusion par nom, sans réutiliser les identifiants numériques de l'autre appareil.
5. Après succès de la transaction, fusion des catégories/annotations dans les préférences. Les catégories sont réunies ; les annotations déjà présentes gagnent. Les réglages cochés remplacent leurs valeurs locales.

**Limite d'atomicité :** les tables Room sont atomiques entre elles. Les préférences constituent un stockage distinct, appliqué ensuite : un arrêt de processus entre les deux phases peut laisser la base restaurée avant les préférences. Une seconde importation fusionne les données sans les dupliquer. Les horloges des appareils doivent être correctes pour la comparaison des progressions.

Les sauvegardes v1 de l'application restent importables, sans pouvoir recréer les données que l'ancien export n'incluait pas. Les versions futures sont refusées explicitement. Un échec d'écriture d'un export SAF est signalé ; le fournisseur de documents peut laisser un fichier incomplet, que l'import ne doit pas accepter.

### Copie automatique locale

- Déclenchée à l'initialisation de l'écran Réglages, si activée et si la dernière sauvegarde date d'au moins 24 heures.
- **Pas de tâche quotidienne garantie en arrière-plan.**
- Même format v2 ; écriture via `AtomicFile` pour protéger la copie précédente d'une interruption d'écriture.
- Emplacement : `(externalFilesDir ou filesDir)/EndlessSea/backups/endlesssea-backup-AAAA-MM-JJ.json`.
- Ce dossier appartient à l'application et peut être supprimé lors de sa désinstallation. Exporter une copie ailleurs avant toute désinstallation. Il n'y a pas de rotation automatique des archives.

## Tests automatisés

Tests ajoutés :

- `LocalPlaybackQueueTest` : tri numérique, isolation des dossiers, reprise sans cache, absence de doublons, épisode 1000, titres renommés et spéciaux fractionnaires.
- `LocalMetadataCodecTest` : clé URI distincte des champs, compatibilité deux champs, caractères spéciaux et valeurs absentes.
- `BoundedTreeScanTest` : huit parents avec enfants, concurrence bornée, profondeur zéro et arrêt du scan.
- `BackupCodecTest` : aller-retour v2, v1, 701 épisodes terminés, fichier étranger/version inconnue, ligne tardive malformée, doublons, nombres négatifs, réglages invalides, champs exportés, limites de taille, fermeture des flux, UTF-8/BOM, imbrication, données parasites et politique de conflits.

Le workflow Android CI lance les tests des sept modules, assemble l'APK debug puis téléverse l'artefact. Une CI verte valide ces étapes, **pas le fonctionnement sur téléphone, les fournisseurs SAF ni les comptes distants**. Le comportement réel de la transaction Room n'est pas couvert par un test instrumenté dans cette passe.

## Vérifications sur appareil à effectuer

- [ ] Reprendre depuis l'accueil après arrêt forcé ; tester précédent/suivant et reprise de position.
- [ ] Même dossier sur mémoire interne et carte SD ; fichiers déplacés et permission révoquée.
- [ ] Dossier avec S01E02, S01E10, S01E12.5, S01E13 et S01E1000 : vérifier ordre et absence de suivi entier du spécial.
- [ ] Scan de huit dossiers contenant eux-mêmes des sous-dossiers : fin de scan, annulation, interface réactive.
- [ ] Titres avec accents/guillemets/virgules et anciens repères intro/outro : vérifier les valeurs affichées et les sauts.
- [ ] Export/import sur installation de test : catégories personnelles, favoris, historique terminé, annotations et réglages.
- [ ] Importer deux fois ; vérifier absence de doublons et préservation des modifications locales plus récentes.
- [ ] Importer une ancienne sauvegarde v1 ; vérifier son périmètre limité.
- [ ] Annuler l'aperçu ; comparer les données avant/après.
- [ ] Importer un fichier tronqué, une mauvaise version et un fichier de plus de 20 Mio : aucune modification avant validation.
- [ ] Tester interruption/stockage plein pendant export et copie automatique ; contrôler la dernière archive lisible.
- [ ] Sur un autre appareil, vérifier le message sur les fichiers absents, réautoriser les dossiers et réinstaller les extensions.
- [ ] Réaliser séparément la checklist lecteur/logos de [cleanup-and-logos.md](cleanup-and-logos.md).
