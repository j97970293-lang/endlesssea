# Explorer, bibliothèque et import AniList

## Nouvelle organisation

- Bibliothèque : trois destinations seulement — Ma liste, Hors ligne, AniList. Recherche visible, filtres secondaires dans une feuille. Dossiers et téléchargements sont réunis par titre dans Hors ligne ; le filtre permet de les séparer. Les anciennes catégories et leurs affectations restent conservées, accessibles dans Organiser/Filtrer et par appui long sur un dossier.
- Explorer : recherche avec requête transmise à l'écran de résultats, choix de source dans une liste défilante, catalogues et suggestions, grille d'affiches. Les filtres genre/année/type restent disponibles hors du contenu principal.
- Cartes communes à ces grilles : affiche, titre en dessous sur deux lignes et éventuellement une seule ligne secondaire. L'indicateur hors ligne est une petite icône sur l'affiche, jamais mêlée au titre. Même carte dans la recherche et « Tout voir ».
- Suppression du bouton flottant global. Le cercle du titre de chaque section ouvre Paramètres / Extensions / Comptes & suivi. À l'accueil, ce cercle affiche le logo choisi de l'application ; les alternatives de logo ne sont pas supprimées.

## AniList

L'ancienne intégration savait chercher/associer un titre et envoyer une progression, mais n'importait pas la liste du compte. Une connexion réussie ne suffisait donc pas à afficher les listes créées depuis une autre application.

- Import paginé, avec l'identifiant retourné par `Viewer` du jeton connecté ; aucune déduction depuis le nom saisi.
- Import à l'ouverture et au retour au premier plan de l'onglet AniList, et bouton Actualiser. Ce n'est pas du temps réel ni une tâche de fond permanente.
- Toutes les pages sont reçues avant publication ; erreurs HTTP/GraphQL distinctes d'une liste réellement vide. Ancien cache conservé en cas d'erreur. Cache privé atomique distinct par compte, sans jeton.
- Statuts, titres, affiches et progression distante sont affichés même sans fiche locale associée. AniList n'est pas présenté comme une source de vidéos.
- Association manuelle à un titre de Ma liste, après choix/confirmation ; recherche dans les extensions pour les autres titres. Suivi automatique désactivé par défaut, puis saison et correspondance à configurer dans la fiche.
- Réception des modifications externes pour les liens sans écriture locale en attente. Une progression locale non envoyée n'est pas écrasée par l'import.
- Dans une fiche, import des métadonnées AniList indépendant du choix du tracker. Un titre peut recevoir affiche/synopsis/genres sans activation du suivi. Les titres/affiches personnels restent prioritaires ; la provenance importée est conservée lors des rafraîchissements de source.
- Les services de suivi existants restent sélectionnables dans la fiche s'ils sont connectés. L'import de liste de cette révision concerne AniList uniquement.

## Accueil

La bannière est dérivée des mêmes rangées et du même identifiant de source que le catalogue visible. Aucun repli sur une autre extension lorsqu'une source précise est sélectionnée ; une réponse tardive de l'ancienne source ne pollue pas le carrousel. Le cache récent ne sert de repli qu'en mode Toutes.

## Validation

Tests ajoutés : parsing, statuts, erreurs, liste vide, pagination authentifiée simulée, import sans écrasement des écritures en attente et isolation des sources du carrousel. Les tests API utilisent des réponses interceptées, pas le compte de l'utilisateur. Vérification réelle à effectuer sur téléphone : connexion AniList, import de sa liste, modification depuis une autre application puis actualisation, import des métadonnées, navigation et lisibilité des cartes.
