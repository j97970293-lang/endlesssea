# Refonte du lecteur — bilan

## Implémenté

- Défaut : retour et titre en haut ; recul de 10 s, lecture/pause et avance de 10 s au centre ; sauts à gauche et paramètres/informations à droite au-dessus de la progression ; temps, vitesse, sous-titres, playlist et orientation en bas.
- Suppression des anciens thèmes de lecteurs tiers. Six interfaces originales : Zen, Orbit, Compact Bar, Neon Frame, Split Controls, Floating Cards. Anciennes préférences migrées vers Défaut.
- Épaisseur de progression réellement appliquée (2–8 dp), curseur 8–20 dp, buffer 2–8 dp, masquage du curseur au repos et forme arrondie configurables.
- Playlist : vignettes ou emplacement de repli, saison/épisode et durée quand fournis, historique et progression, sélection d'épisode, badge de téléchargement. File locale conservée, lecture locale prioritaire pour les épisodes téléchargés depuis une fiche.
- Masquage des commandes après 3 s, suspendu pendant les panneaux et le glissement ; bouton de déverrouillage indépendant ; paramètres défilants.
- Bandes-annonces : activité séparée, vidéo HTTPS directe via Media3 ou intégration YouTube/Vimeo/Dailymotion dans une WebView sans lancement externe automatique. Validation des hôtes et schémas.
- Historique : liste, suppression d'une entrée ou d'une série, entrées vues à 95 %, entrées de plus de 7/30/90 jours, tout effacer avec confirmation ; désactivation de l'enregistrement indépendante du suivi tracker.
- Accueil : bouton paramètres à la place du logo, nom de l'application au-dessus de l'extension.
- Explorer : recherche directe avec focus et clavier ; filtres sans bordure ; suggestions locales fondées sur les genres des médias récemment regardés et les catégories des catalogues chargés.
- Matching tracker streaming : requête éditable et confirmation avant rattachement ; recherche, comptes et marquage à 90 % existants conservés.

## Validation

Compilation Android réalisée avec JDK 17 et SDK 35. Commande finale :

```sh
./gradlew :app:testDebugUnitTest :core:testDebugUnitTest \
  :extensions-api:testDebugUnitTest :downloader:testDebugUnitTest \
  :player:testDebugUnitTest :extensions-loader:testDebugUnitTest
```

90 tests JVM réussis, 0 échec : app 24, core 13, API 6, downloader 26, player 11, loader 10. Cinq nouveaux tests couvrent le registre des thèmes et la validation des bandes-annonces. Le module data n'a pas de tests JVM. `git diff --check` réussi.

Pour une machine disposant de 2 Go de RAM, ajouter :

```sh
--no-daemon --max-workers=1 \
-Dorg.gradle.jvmargs='-Xmx640m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
-Pkotlin.compiler.execution.strategy=in-process --no-configuration-cache
```

## Limites / reste à faire

- Pas de connexion OAuth/WebView/Custom Tabs aux trackers ajoutée : les clients OAuth et URI de retour doivent être enregistrés auprès des services. Aucun identifiant inventé. Connexion existante par jeton conservée.
- Matching depuis une fiche locale et choix désactivable de correspondance des épisodes non ajoutés. La confirmation actuelle indique le comportement existant par numéro.
- Les fiches locales immersives et marqueurs globaux de téléchargement existent déjà et ont été conservés, pas reconstruits ; les demandes supplémentaires « tout marquer vu », gestes de suppression d'historique et métadonnées complètes de toutes les playlists restent à compléter.
- Suggestions heuristiques : pas de résultats si les genres ou catégories ne sont pas fournis ; pas de moteur distant ni de recommandations inventées.
- L'hébergeur peut interdire une bande-annonce intégrée ; aucun contournement, extraction YouTube ni ouverture externe automatique. Pas de gestion personnalisée du plein écran WebView.
- Media3 reste à la version 1.5.1 du dépôt : pas de montée à une version 1.11 non vérifiée.
- Pas d'émulateur ni de test visuel sur téléphone dans cet environnement. Les tests JVM ne valident pas les gestes, les WebView ni les migrations Room sur appareil. Aucun APK distribué.
- Commits créés localement, PAS envoyés à GitHub : aucune authentification sécurisée disponible. Le jeton exposé dans le message n'a pas été utilisé et doit être révoqué.

## Intégration

L'archive livrée contient les sources et les patches de quatre commits. Sur une copie propre du dépôt au commit de base `e1fbe03bf263c1022920a1b8d9e3b759cba99558` :

```sh
git am /chemin/patches/*.patch
```

Puis lancer les tests et `./gradlew :app:assembleDebug` sur une machine Android équipée. Après vérification, pousser avec votre authentification habituelle : `git push origin main` (ou une branche dédiée et une pull request).
