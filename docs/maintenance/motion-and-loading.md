# Animations et chargements — passe transversale

Version du code inchangée : 0.26.0. Pas de release associée.

## Ce qui change

- **Chargements circulaires** : composant vectoriel partagé à trois arcs, animé sur 1,4 seconde, couleurs du thème et libellé d'accessibilité stable. Utilisé dans les fiches, les extensions, le scan local, le lecteur (buffering et recherche de sous-titres), les réglages, les statistiques et le suivi. Pas de faux pourcentage.
- **Catalogues** : placeholders de trois affiches et lignes de texte avec un reflet doux (1,8 seconde) dans Accueil, Explorer, Voir tout et les résultats de recherche vides en chargement. Une horloge par bloc, pas une boucle par affiche. Aucun délai ajouté au chargement réel.
- **Cartes média partagées** : entrée en fondu/déplacement de 10 dp sur 220 ms ; appui à 97,5 % sur 140 ms au lieu d'une réduction plus forte. Aucun délai échelonné imposé aux longues listes.
- **Navigation** : fondu court entre onglets, maintien du léger glissement pour les pages de détail et retour. La pilule de navigation change de couleur et de largeur progressivement.
- **Réglages** : apparition lors du changement de catégorie, retour d'appui discret sur les catégories et les lignes de réglage. Le contenu n'est pas remonté dans un second arbre animé, afin de préserver les états et interactions.
- **Historique et téléchargements** : animations natives de placement/apparition/disparition sur les éléments à identifiant stable. Progression visuelle des téléchargements interpolée sur 220 ms ; les octets, statuts et pourcentages métiers ne changent pas.
- **Fond liquide** : les phases sont lues au dessin, plutôt que de recomposer le composant à chaque image.

## Réduction du mouvement

`AppMotionProvider` est installé dans le thème commun et reçoit la préférence directement depuis MainActivity et PlayerActivity. Il observe le réglage Android `ANIMATOR_DURATION_SCALE` et le cycle de vie.

- Préférence désactivée ou échelle Android nulle : nouveaux mouvements désactivés, loaders statiques et transitions de navigation immédiates.
- Activité non démarrée : arrêt des nouvelles boucles (loaders, reflets, fond liquide et défilement automatique du carrousel).
- Échelle Android positive : Compose applique cette échelle lui-même, sans multiplication supplémentaire.
- Pas d'attente artificielle du splash lorsque les animations sont désactivées.
- Les fondus d'images de MediaCard, SafeAsyncImage et des vignettes vidéo locales suivent cette politique.

Il ne s'agit pas d'un remplacement de toutes les animations internes de Material, de Coil ou des commandes des sept thèmes du lecteur. Les gestes vidéo, positions enregistrées et temporisations du lecteur ne sont pas modifiés. Les autres animations historiques doivent encore être vérifiées visuellement.

## Validation

`MotionPolicyTest` : sept tests de préférence, échelle système, activité/arrière-plan et bornes de durée. Ces tests de logique ne prouvent pas la qualité du rendu Compose ni une fréquence d'images sur appareil.

Checklist appareil :

- [ ] Passer entre tous les onglets, ouvrir une fiche puis revenir ; conserver position de défilement et sélection.
- [ ] Charger un catalogue lent, vide, en erreur et déjà en cache ; aucun retard artificiel ni clignotement gênant.
- [ ] Tester la recherche avec clavier ouvert, petit écran et grande taille de police.
- [ ] Faire défiler une longue bibliothèque ; vérifier la lisibilité des cartes et la fluidité.
- [ ] Réordonner/retirer des téléchargements et supprimer une entrée d'historique ; vérifier les confirmations et actions pendant les transitions.
- [ ] Couper les animations dans l'app puis dans Android ; conserver des indicateurs de chargement visibles et statiques.
- [ ] Changer le réglage Android pendant que l'app est ouverte ; le rendu doit s'actualiser.
- [ ] Passer en arrière-plan puis revenir, y compris depuis le lecteur ; aucune boucle décorative active derrière une activité arrêtée.
- [ ] Vérifier le buffering et les petits indicateurs sur les sept thèmes du lecteur.
- [ ] Vérifier TalkBack, mode sombre/clair/AMOLED et écran 60/120 Hz. Mesurer les frames sur un téléphone modeste avant toute affirmation de performance.
