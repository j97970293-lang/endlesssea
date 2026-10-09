# Lecteur : commandes et modifications d'image en direct

## Problèmes traités

- Media3 1.5.1 n'instancie son `VideoSink` qu'au premier `onEnabled` du renderer (ou après reset). Appeler `setVideoEffects` une fois la vidéo déjà lancée pouvait donc ne pas créer de chaîne GPU. Une chaîne vide est désormais installée dès la construction d'EsPlayer, avant toute préparation.
- Les changements de profils provoquaient un remplacement forcé Surface → Texture et un seek sur chaque modification (y compris pendant la lecture). La surface choisie reste stable ; les modifications sont regroupées sur 120 ms, les doublons ignorés. Seule une image en pause, prête et seekable est rafraîchie par seek. Une erreur synchrone n'est plus silencieusement avalée ; luminosité + gamma est borné à l'intervalle Media3.
- Le bouton Play se basait sur `isPlaying`, faux pendant le buffering, tandis que son action utilisait `playWhenReady`. L'icône et l'action partagent maintenant la même règle : Pause pendant lecture/buffering demandé ; Play/reprise en pause, fin, erreur ou idle.
- Les doubles appuis natifs regroupaient les tapotements successifs par paires. Une séquence explicite garde le double appui initial, puis compte chaque appui supplémentaire. Les boutons de saut comptent chacun de leurs appuis. Les sauts relatifs sont accumulés pendant une fenêtre bornée de 60 ms, sans dépendre d'une position décodée périmée ; un seek absolu/changement d'épisode annule la cible en attente.
- Cadrage confié au `PlayerView` Media3 sans contrôles natifs : fit, crop/zoom et stretch fonctionnent pour Texture et Surface, avec prise en compte des dimensions et rotation. Détachement explicite à la destruction. Le rendu des sous-titres reste celui de l'app. Dans Essentiel, l'icône de cadrage est accompagnée du libellé du mode.

## Vérification

12 tests JVM ciblés exécutés localement avec Kotlin 2.1.0 et Media3 1.5.1 : séquences rapides, inversion de sens, expiration, annulation, cumul de cinq clics avec position moteur figée, bornes, durée inconnue et état du bouton en buffering/erreur/fin/idle.

La compilation Android complète reste à contrôler par CI. Aucun résultat JVM ne démontre le comportement des pilotes GPU sur téléphone.

## Essai téléphone requis

1. Pendant lecture, changer agrandissement et style d'image, puis presser Pause et Play plusieurs fois ; recommencer pendant buffering et en pause.
2. Modifier un filtre fin et revenir au neutre sans fermer le lecteur ; vérifier que lecture/pause et position restent cohérentes.
3. Vérifier Contenir/Remplir/Étirer en portrait/paysage, Texture/Surface, y compris après changement de sortie.
4. Depuis une position éloignée des extrémités, cinq appuis rapides sur +10 s donnent +50 s ; tester -10 s et inversion. Sur l'image, un double appui lance le saut, puis chaque appui de la rafale ajoute un saut.
5. Vérifier sous-titres, appui long (restauration de la vitesse initiale), seek absolu et changement d'épisode après une rafale.

Source technique : sources Media3 1.5.1, `MediaCodecVideoRenderer.onEnabled` et `setVideoEffects`, dans l'archive Maven officielle `media3-exoplayer-1.5.1-sources.jar`.
