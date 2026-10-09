# Explorer, reprise, téléchargements, cadrage et métadonnées

## Corrections
- Les entrées de reprise sont dédupliquées par fiche après tri par date : plusieurs épisodes interrompus ne produisent plus la même clé LazyRow. Le défilement en fin de rangée reste à valider sur téléphone (pas de logcat fourni).
- Recherche, choix de source et filtres d’Explorer défilent dans la même grille que les affiches. Seul l’en-tête de section reste fixe.
- Suppression du réglage de teinte du logo au démarrage et de son application ; les variantes originales du logo restent disponibles.
- Carte Métadonnées indépendante du suivi, avec choix AniList/TMDB et titre manuel, même lorsqu’un tracker est déjà associé. TMDB fournit aussi titre et genres ; JSON null ne devient plus une URL d’affiche.
- Statuts supplémentaires dans la fiche : à voir, pause, et revisionnage pour AniList.
- Noms de nouveaux téléchargements : `Titre (année) [1080p] [VF].mp4` pour un film ; `Titre (année) - S02E03 [1080p] [VF].mp4` pour une série. Pas de saison inventée ; spéciaux S00 et numéros fractionnaires conservés. Aucun renommage des fichiers existants ni changement de leurs URI.
- MediaStore ne publie que les copies terminées ; un échec ne supprime plus le fichier source. Indexation des fichiers sur le chemin public Android ancien. Les dossiers SAF restent soumis à l’accès accordé aux autres applications.
- Cadrage : Contenir, Remplir, Étirer (déformation annoncée), zoom manuel et position bornée ; réinitialisation image entière. Pas de remplacement de surface ou de programme vidéo pendant ces ajustements.
- Recherche/import de sous-titres dans le dialogue « Paroles / sous-titres », retirés de Plus. Il s’agit bien de sous-titres vidéo, pas d’un nouveau moteur de paroles de chansons.

## Non livré : connexion navigateur sans saisie de jeton
Aucun client OAuth AniList enregistré pour EndlessSea ni configuration d’application TMDB n’a été trouvé dans le dépôt ou ses variables Actions. Les formulaires de connexion existants restent utilisables ; cette livraison ne prétend pas remplacer leur authentification.
L’enregistrement et la configuration des applications auprès des services doivent précéder le parcours navigateur. Ne jamais demander les mots de passe des comptes dans l’application ni embarquer un secret OAuth confidentiel dans l’APK.

## Vérification sur téléphone
1. Défilement jusqu’au bout de Reprendre avec plusieurs épisodes interrompus de la même série.
2. Grille Explorer sur petit écran et après rotation, filtres toujours accessibles en remontant.
3. Fiche suivie sur AniList : importer un résultat TMDB et vérifier que le suivi ne change pas.
4. Télécharger film, série, spécial et épisode fractionnaire, puis ouvrir depuis un autre lecteur ayant accès au stockage.
5. Contenir/Remplir puis zoom et déplacements répétés, rotation, pause/reprise ; vérifier que Play et le mégaskip restent utilisables.
6. Vérifier les statuts et progressions avec le véritable compte AniList. La compilation et les tests avec réponses simulées ne remplacent pas ce contrôle.
