# Bibliothèque par titres, hors-ligne prioritaire et lecteur Essentiel

Cette passe répond au besoin réel : les fichiers locaux sont des épisodes **dans une fiche**, pas des affiches indépendantes dispersées dans la bibliothèque. Elle complète et remplace plusieurs choix visuels provisoires du lot 1.

## Comportement intégré

- Dossiers : grille d'affiches, une entrée par dossier contenant des vidéos ; recherche par titre. Le mode de grille « tous les fichiers » n'est plus proposé et son ancienne préférence n'est plus utilisée par cet écran.
- Même regroupement dans la collection fusionnée et les catégories personnelles. Un dossier de 1 000 épisodes produit une carte, pas 1 000 cartes. Deux dossiers homonymes sur des volumes différents ne sont pas fusionnés.
- Les URI de fichiers déjà gérés comme téléchargements sont exclues de ces cartes locales, y compris lorsque deux permissions SAF donnent des URI différentes pour le même document. Pas de rapprochement par simple nom de fichier.
- La fiche locale propose titre, synopsis, auteur, genres, affiche, recherche d'épisode et filtre de saison. La liste reste paresseuse et utilise l'ordre numérique commun avec la file de lecture, y compris les épisodes spéciaux fractionnaires.
- L'appui long sur un épisode édite son titre et sa vignette sans renommer le fichier ni modifier son numéro. Renommer la série ne renomme plus tous ses épisodes.
- Les métadonnées de série éditées sont stockées dans Room, sous l'identité locale existante ; elles sont également exportées dans `details.json` quand le dossier est modifiable. Les valeurs peuvent être vidées. Une couverture de série n'est plus recopiée comme vignette personnalisée de chaque épisode.
- Les catégories de fichiers restent compatibles : les anciennes appartenances par URI sont affichées par dossier ; les nouvelles appartenances utilisent une clé de dossier. Retirer une racine demande confirmation et ne supprime pas les fichiers.
- Le scan ne lance plus MediaMetadataRetriever sur tous les épisodes. Les durées des lignes visibles sont chargées à la demande, avec trois lectures simultanées maximum par ViewModel.

## Téléchargements = mêmes fiches, mêmes épisodes

- Quand l'identité du média et sa fiche en cache sont connues, la carte téléchargée ouvre la **fiche d'origine**, pas une fiche parallèle. Les téléchargements anciens sans fiche identifiable conservent une fiche de secours ; aucune association par nom n'est inventée.
- La ligne d'épisode affiche « Hors ligne » et propose la suppression du fichier au lieu d'un nouveau téléchargement. La section séparée de fichiers est limitée aux anciens fichiers sans épisode correspondant.
- Au clic, la disponibilité est vérifiée pour l'épisode exact, sans ouvrir les 1 000 fichiers du titre. Un fichier lisible est joué sans ouvrir la recherche de serveurs. Une copie absente retourne au parcours en ligne.
- Le passage à l'épisode suivant vérifie à nouveau les copies locales via le résolveur : les téléchargements et suppressions survenus pendant la lecture sont pris en compte dans cette file.
- Supprimer une copie conserve la fiche et l'épisode. Un échec de suppression signalé par le fournisseur conserve la tâche ; aucune suppression réussie n'est annoncée dans ce cas.
- Les titres et affiches personnalisés du média ne sont plus écrasés lors d'un rafraîchissement de la source. Les épisodes téléchargés déjà en cache restent dans la liste même si la réponse courante de la source ne les contient plus.

La disponibilité signifie « lisible actuellement » : une permission révoquée ou un support retiré rend aussi la copie indisponible. La lecture lancée directement depuis la file technique des téléchargements garde son parcours de secours existant. Les identifiants d'épisodes d'une extension qui changent ne sont pas rapprochés arbitrairement.

## Serveurs et lecteur

- Feuilles de lecture et téléchargement : hauteur bornée à l'écran et contenu défilant. Dialogue de priorité des serveurs également borné. Pas de prétention d'auto-défilement pendant le glisser-déposer.
- Nouveau lecteur principal **Essentiel** : titre et source sur un bandeau sobre, transport central lisible, bouton lecture clair, timeline séparée et barre horizontale d'actions libellées (épisodes, audio, sous-titres, qualité, vitesse, sauts, options).
- Présentation centrale adaptée à la largeur ; tailles tactiles de 48 dp minimum dans les nouvelles commandes. Les incréments de saut suivent le réglage utilisateur.
- Il est activé une seule fois à la première ouverture après la mise à jour. Les anciens identifiants sont migrés vers « Essentiel » ; les anciens thèmes et leurs sélecteurs ne sont plus proposés.
- Le moteur, les sous-titres, la file et les identités de progression sont conservés. Les champs de placement historiques sont encore lus/écrits par les sauvegardes pour compatibilité, mais n'influencent plus la disposition fixe d'Essentiel.

## Tests et limites

- `FolderIndexTest` : 9 tests, dont 1 000 épisodes → une entrée, scans recouvrants, documents SAF sous plusieurs permissions et exclusion des copies gérées.
- `EpisodeSearchTest` : 7 tests de recherche exacte par numéro (y compris 1000 et les spéciaux), titre et remise à zéro. La recherche est commune aux fiches locales et aux fiches en ligne/téléchargées, avec un filtre hors ligne sur ces dernières.
- `OfflineSelectionTest` : 6 tests sur l'identité d'épisode, suppression, autre qualité lisible, absence d'association par nom et absence de sondage des autres épisodes.
- `ThemeProviderTest` : registre adapté pour Essentiel et les sept présentations historiques ; test d'accès au nouveau thème.
- Les 22 nouveaux tests purs de regroupement/sélection/recherche ont réussi localement. La CI Android doit compiler les écrans et exécuter la suite complète avant livraison.

Aucun essai sur téléphone n'est revendiqué. À vérifier : dossier de 1 000 fichiers, plusieurs saisons, métadonnées dans un dossier non modifiable, téléchargement puis lecture en mode avion, suppression puis lecture en ligne, longues listes de serveurs, petit écran/portrait, TalkBack et grand texte.

Le regroupement local suit le dossier contenant les vidéos : des sous-dossiers de saisons restent des entrées distinctes, sans fusion automatique par nom. La restauration totale après mort du processus et l'index global définitif du chantier de reconstruction restent à compléter.

## Révision lecteur de référence et association locale (2026-10-09)

- Essentiel conserve le bouton Play blanc de 80 dp. Entête et commandes secondaires arrondis ; précédent/suivant au centre. Le mégaskip est ancré immédiatement au-dessus de la barre de progression, à droite par défaut, indépendamment des placements historiques des outils. Le dock inférieur est défilant. Aucun faux moteur HW+ n'est ajouté.
- Les réglages de forme/épaisseur de progression, tampon et curseur sont conservés. Le placement haut/bas ne concerne plus Essentiel, ce que les libellés indiquent.
- Sur la fiche d'un dossier : « Associer à une fiche en ligne » recherche parmi les extensions activées, charge une fiche choisie, puis demande confirmation. La recherche est bornée (trois sources simultanées, première page, trente résultats/source). Aucun rapprochement automatique de titres.
- Association des épisodes par saison et numéro uniquement, uniques des deux côtés. Une saison de repli peut être choisie explicitement pour les fichiers sans saison ; par défaut, aucune saison n'est devinée. Les numéros fractionnaires sont conservés. Les lignes ambiguës restent inchangées.
- Import Room transactionnel, avec les identifiants locaux existants. Les sources vidéo restent les URI locales. Ni renommage, ni téléchargement, ni écriture tracker, ni remplacement des identifiants d'historique. Métadonnées manuelles prioritaires ; l'éditeur conserve désormais les références de source.
- Les numéros, saisons et titres importés sont projetés dans la fiche, sa recherche et la file de lecture complète. Filtrer la fiche ne tronque pas la file de lecture.
- Miniatures d'épisodes et de playlist locale : extraction à 10 % de la vidéo, demande 320×180, deux décodeurs maximum, cache mémoire URI/taille et repli visible. Décodeur vidéo explicite pour les fournisseurs SAF signalant `application/octet-stream`. Les affiches de série restent réservées à la fiche.

Validation à effectuer sur téléphone : proportions par rapport à la capture, portrait/paysage et encoche ; mégaskip avec les anciens réglages placés en haut ; vignettes SAF avec 1 000 fichiers ; import, redémarrage, titres manuels et historique ; refus des doublons et saisons inconnues. Les tests unitaires/CI ne valident pas ces comportements Android réels.
