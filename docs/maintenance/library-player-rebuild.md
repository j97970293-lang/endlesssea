# Reconstruction bibliothèque et lecteur

## Décision et périmètre

Demande : repartir de zéro sur **l'interface et le fonctionnement** des deux espaces, avec carte blanche. Ce chantier n'est pas une simple nouvelle couche graphique. Il est livré progressivement, sans effacer les données ni retirer les fonctions avant leur remplacement.

**État : lot 1 — socle intégré. La reconstruction complète n'est pas terminée.** Pas de changement de version ni de nouvelle release pour ce lot.

## Constats vérifiés dans le code

- `LibraryScreen.kt` et `LibraryViewModel.kt` comptaient respectivement 1 083 et 806 lignes au début du chantier. Catégories de médias, catégories personnelles de fichiers, filtres de suivi, disponibilité locale, scan SAF et téléchargements partagent le même écran et plusieurs indicateurs booléens.
- La sélection d'onglet était un index `remember` local à l'écran, tandis que les modes et catégories appartenaient au ViewModel. Une recréation pouvait donc réafficher un onglet différent du contenu chargé.
- Le filtre « Tous » ne désactivait pas nécessairement l'affichage exclusif des téléchargements. La navigation et les filtres pouvaient se contredire.
- `PlayerActivity` (1 487 lignes) consommait `PlayerLaunchStore` puis appelait `prepare` à chaque `onCreate`, alors que le ViewModel et son moteur peuvent survivre à une recréation. Le second lancement pouvait donc recevoir une liste de liens vide.
- Les sept thèmes sont **réellement utilisés** : `ThemeProvider`, le lecteur et les réglages les exposent. Ils partagent `PlayerControlsState`, `PlayerControlsActions` et le moteur `EsPlayer`. Les thèmes alternatifs héritent notamment de `OriginalTheme` et réutilisent les barres communes. Leur diversité tient principalement à la présentation des commandes, pas à sept moteurs distincts.
- L'historique, les rattachements de suivi, les métadonnées locales et les files de lecture ont déjà des identifiants utilisés ailleurs dans l'application. Leur remplacement brutal provoquerait des régressions.

## Direction cible

### Bibliothèque

Trois espaces permanents :

1. **Collection** : titres suivis, favoris, recherche, tri et filtres clairement séparés.
2. **Téléchargements** : titres disponibles hors ligne, épisodes, tailles et actions de gestion.
3. **Dossiers** : arbres autorisés, fichiers détectés et catégories personnelles de fichiers.

Chaque vue aura un état explicite : chargement initial, données disponibles, vide, erreur récupérable, accès au stockage perdu. Un index commun devra assembler les sources sans confondre un fichier, un épisode et une série. Les statuts du tracker et l'historique local resteront distincts.

### Lecteur

**Un lecteur principal personnalisable**, plutôt que sept organisations concurrentes à maintenir. La personnalisation portera sur l'accent, la densité et les raccourcis, sans déplacer arbitrairement les fonctions essentielles.

Structure cible :

- une session possédée par un contrôleur, indépendante de la durée de vie de l'Activity ;
- une file d'épisodes et une sélection de qualité distinctes ;
- des requêtes de résolution annulables : une ancienne réponse ne doit pas remplacer la vidéo courante ;
- un adaptateur Media3 : conserver les fonctions éprouvées d'ExoPlayer, pas réécrire les décodeurs ;
- une surface vidéo et un unique ensemble de commandes adaptatives ;
- panneaux dédiés pour épisodes, pistes audio, sous-titres, qualité et options avancées ;
- sauvegarde de progression, accès externe et restauration après mort du processus conçus explicitement.

Les anciens thèmes restent accessibles **pendant la transition**. Aucun n'est supprimé dans le lot 1. La migration des préférences et les contrôles de parité précéderont leur remplacement.

## Lot 1 intégré

### Navigation de bibliothèque

- Nouveau modèle Kotlin `LibraryNavigation` : une destination stable, la dernière catégorie de collection et le filtre de disponibilité. `LibraryArea` sépare Collection / Téléchargements / Dossiers.
- Le ViewModel possède la navigation ; `SavedStateHandle` conserve les clés lors des restaurations Android prises en charge. Il ne s'agit pas d'une nouvelle préférence persistante après suppression de la tâche Android.
- Nouvelle barre de navigation à deux niveaux. Les types de média appartiennent à Collection, les catégories personnelles de fichiers à Dossiers. Téléchargements possède sa propre destination.
- Les filtres de collection ne masquent plus la vue dédiée aux téléchargements. Les identifiants Room et les URI SAF ne changent pas.
- Une catégorie personnelle supprimée revient vers Dossiers, plutôt que vers un autre onglet portant par hasard le même index.

### Propriété de session de lecture

- Nouveau composant Kotlin testable `PlaybackSessionOwner`, possédé par le ViewModel.
- L'Activity appelle `attachSession` au lieu de consommer et préparer elle-même un lancement à chaque création.
- Une session déjà attachée n'est pas préparée de nouveau ; un lancement en attente sans rapport n'est pas consommé par cette réattache.
- Sans lancement exploitable, un message explicite invite à rouvrir la vidéo. Aucun lien vide n'est transmis au moteur par cette entrée.

**Limites du lot :** les grilles, panneaux de fichiers, moteur et commandes existants sont encore utilisés. La navigation n'unifie pas encore tout le modèle de contenu. `PlayerLaunchStore` reste en mémoire ; la restauration complète après mort du processus et la sérialisation des changements d'épisodes restent à faire. La politique de pause existante est conservée : ce lot ne garantit pas une reprise automatique transparente après rotation.

## Lots suivants

1. **Index et présentation de bibliothèque** : séparer acquisition SAF/Room et construction des vues ; recherche et tri communs, regroupements par identité, états vides/erreurs, sélection multiple et gestion explicite des dossiers autorisés.
2. **Session de lecture complète** : file et source courante atomiques, annulation structurée des résolutions, sauvegarde de position fiable et stratégie de restauration sans sérialiser inutilement des liens signés périmés.
3. **Nouvelles interfaces** : bibliothèque entièrement recomposée autour de l'index et lecteur principal adaptatif, panneaux spécialisés, interactions et accessibilité cohérentes.
4. **Migration et retrait de l'ancien code** : inventaire des appels restants, migration des préférences de thèmes, tests de parité, suppression des seuls chemins effectivement remplacés.

Chaque lot terminé doit être poussé et vérifié par Android CI. Une release de reconstruction ne doit pas présenter le lot 1 comme un produit entièrement réécrit.

## Validation

Nouveaux tests du lot 1 :

- `LibraryNavigationTest` : 12 cas de destination, clé stable, restauration, retour à la collection et indépendance du filtre de disponibilité.
- `PlaybackSessionOwnerTest` : 8 cas d'attachement initial, recréations répétées, requête absente/invalide, isolation des propriétaires et préservation d'un lancement sans rapport.

Les tests Kotlin de ces composants ne simulent ni un vrai `SavedStateHandle` restauré par Android ni le cycle de vie Media3. La CI compile l'intégration Android. Aucun essai appareil n'est revendiqué.

### Checklist appareil à effectuer

- [ ] Alterner Collection / Téléchargements / Dossiers ; vérifier que les filtres d'une collection ne masquent pas les téléchargements.
- [ ] Sélectionner Anime ou une catégorie personnelle, recréer l'écran et vérifier sélection/contenu.
- [ ] Supprimer la catégorie sélectionnée ; revenir à Dossiers sans ouvrir une autre catégorie par erreur.
- [ ] Lire depuis chaque entrée : streaming, téléchargement, dossier, historique et Intent externe.
- [ ] Recréer l'Activity en lecture ; conserver la session et pouvoir reprendre après la pause sans lancement vide.
- [ ] Tuer le processus pendant la lecture : message honnête si aucune session n'est récupérable, aucune progression associée à une mauvaise vidéo.
- [ ] Vérifier fichiers sur carte SD, retrait de permission, petit écran, police agrandie et TalkBack.
