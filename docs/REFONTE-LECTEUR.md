# Refonte du lecteur — bilan

## Implémenté

- Défaut : retour et titre en haut ; recul de 10 s, lecture/pause et avance de 10 s au centre ; sauts à gauche et paramètres/informations à droite au-dessus de la progression ; temps, vitesse, sous-titres, playlist et orientation en bas.
- Interface Essentiel unique. Les thèmes alternatifs historiques (Défaut, Zen, Orbit, Compact Bar, Neon Frame, Split Controls, Floating Cards) et leurs sélecteurs sont supprimés ; les préférences/sauvegardes anciennes sont migrées vers Essentiel.
- Épaisseur de progression réellement appliquée (2–8 dp), curseur 8–20 dp, buffer 2–8 dp, masquage du curseur au repos et forme arrondie configurables.
- Playlist : vignettes ou emplacement de repli, saison/épisode et durée quand fournis, historique et progression, sélection d'épisode, badge de téléchargement. File locale conservée, lecture locale prioritaire pour les épisodes téléchargés depuis une fiche.
- Masquage des commandes après 3 s, suspendu pendant les panneaux et le glissement ; bouton de déverrouillage indépendant ; paramètres défilants.
- Bandes-annonces : activité séparée, vidéo HTTPS directe via Media3 ou intégration YouTube/Vimeo/Dailymotion dans une WebView sans lancement externe automatique. Validation des hôtes et schémas.
- Historique : liste, suppression d'une entrée ou d'une série, entrées marquées vues ou lues à 95 %, entrées de plus de 7/30/90 jours, tout effacer avec confirmation ; désactivation de l'enregistrement indépendante du suivi tracker.
- Accueil : bouton paramètres à la place du logo, nom de l'application au-dessus de l'extension.
- Explorer : recherche directe avec focus et clavier ; filtres sans bordure ; suggestions locales fondées sur les genres des médias récemment regardés et les catégories des catalogues chargés.
- Matching tracker streaming et local : titre recherchable éditable, vignette, année et confirmation du rattachement. Correspondance automatique explicitement désactivable, limitée à la saison choisie. Progression par numéro entier à 90 %, sans incrément sur relecture ; spéciaux, numéros inconnus et autre saison ignorés. Un seul service rattaché par fiche, comme avant.
- Identifiant de série locale stable partagé par fiche, lecteur et historique ; les reprises locales ouvrent la bonne fiche. Chemins de lecture locale unifiés, marqueurs conservés, saison/numéro déduits du nom original et non du titre personnalisé.
- « Tout marquer vu » avec confirmation sur fiches streaming, locales et téléchargées : historique local uniquement, aucune modification distante. Balayage gauche/droite des entrées d'historique avec confirmation.
- Métadonnées de playlists téléchargées complétées par les épisodes Room : saison, numéro exact, durée et vignette quand disponibles. Les durées locales chargées après scan sont publiées au cache partagé.
- Plein écran Media3 et WebView des bandes-annonces, sortie par Retour et libération de la vue personnalisée. Pause à la mise en arrière-plan ; intégration toujours dépendante de l'hébergeur.
- Services : Shikimori distingue création et PATCH d'une entrée existante avec user_id ; MAL conserve refresh token tournant et expires_in réels, renouvelle après HTTP 401 sans secret. Échec de reconnexion : le compte existant n'est pas écrasé. Synchronisations sérialisées et accusé conditionné à la version envoyée.
- Base Room v8 et migration v7→v8 : ancien suivi conservé, correspondance automatique désactivée par défaut.

## Validation

Compilation Android réalisée avec JDK 17, SDK 35 et Media3 1.5.1. Commandes validées :

```sh
./gradlew :app:testDebugUnitTest :core:testDebugUnitTest :data:testDebugUnitTest \
  :extensions-api:testDebugUnitTest :downloader:testDebugUnitTest \
  :player:testDebugUnitTest :extensions-loader:testDebugUnitTest
./gradlew :app:assembleDebug
python3 docs/validate_room_sqlite.py
```

107 tests JVM réussis, 0 échec : app 41, core 13, API 6, downloader 26, player 11, loader 10. Le module data n'a pas de tests JVM. Les 17 nouveaux tests de cette continuation couvrent le matching, l'idempotence/hors-ligne, les numéros locaux et les requêtes Shikimori/MAL simulées sans vrais comptes. `git diff --check` réussi.

APK debug assemblé avec succès (signature debug du dépôt conservée, applicationId `dev.endlesssea.app.debug`, version 0.26.0-debug). Il ne constitue pas une release de production. Validation SQLite : schémas exportés v7 et v8 identiques après application des SQL de migration, progression conservée et suppression des vus manuels vérifiée. Ce n'est pas un test Android instrumenté.

Pour une machine disposant de 2 Go de RAM, séparer compilation et assemblage :

```sh
# Compilation Kotlin :
--no-daemon --max-workers=1 \
-Dorg.gradle.jvmargs='-Xmx800m -XX:MaxMetaspaceSize=448m -XX:+UseSerialGC -Dfile.encoding=UTF-8' \
-Pkotlin.compiler.execution.strategy=in-process --no-configuration-cache
# Tests : mêmes options, heap 640m ; assemblage APK : heap 1024m, metaspace 256m.
```

## Limites / reste à configurer ou vérifier

- Pas de connexion OAuth navigateur ajoutée : clients OAuth et URI de retour doivent être enregistrés auprès des services. Aucun identifiant inventé ni secret embarqué. Connexion existante par jeton conservée. Shikimori nécessite un backend de confiance pour l'échange avec secret. Le renouvellement MAL suppose un client natif public et un refresh token valides.
- Le rattachement ne lit pas la progression déjà présente sur le service. Vérifier le titre et la saison distante avant d'activer la correspondance ; désactiver si les numéros ne commencent pas à 1 ou ne correspondent pas. Un rattachement par fiche, pas un lien différent pour chaque saison/service.
- Durées, saisons et vignettes de playlists ne sont affichées que lorsqu'elles sont fournies ou déductibles. Aucune métadonnée inventée. Les épisodes.json actuels contiennent des titres par numéro, pas une table complète de saisons.
- Suggestions heuristiques : pas de résultats si les genres ou catégories ne sont pas fournis ; pas de moteur distant ni de recommandations inventées.
- L'hébergeur peut interdire une bande-annonce intégrée ; aucun contournement, extraction YouTube ni ouverture externe automatique.
- Media3 reste à la version 1.5.1 du dépôt : pas de montée à une version 1.11 non vérifiée.
- Pas d'émulateur ni de test visuel sur téléphone dans cet environnement. Les tests JVM ne valident pas les gestes, le plein écran WebView ni les migrations Room sur appareil. Liste détaillée : [VERIFICATION-REFONTE.md](VERIFICATION-REFONTE.md).

## Intégration GitHub

Les quatre commits de la première livraison sont intégrés avec les ajouts de cette continuation sur `main`. La CI du dépôt lance les tests puis assemble l'APK debug à chaque push sur `main` et publie l'artefact `endless-sea-debug-apk` si elle réussit. Vérifier l'exécution GitHub Actions puis suivre la liste de tests sur appareil avant une publication de production.
