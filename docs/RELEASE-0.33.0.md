# EndlessSea 0.33.0 — Sources FR, anti-bot et fiches fiables

Cette version accompagne les extensions FR 2.1.0 et corrige la chaîne complète entre les sites protégés, les extensions et l'affichage dans l'application.

## Sources et vérification anti-bot

- Lorsqu'une extension rencontre Cloudflare ou Turnstile, l'application ouvre désormais l'écran WebView interactif, conserve les en-têtes utiles, accepte les cookies tiers nécessaires puis rejoue l'opération interrompue.
- Les cookies WebView sont partagés avec les requêtes OkHttp des extensions et avec le chargeur d'images. Une validation réussie vaut donc aussi pour les fiches, affiches et lecteurs protégés.
- Le mécanisme couvre l'accueil, Explorer, Tout voir, la recherche, les fiches et la résolution progressive ou groupée des lecteurs.
- Une seule vérification est présentée à la fois ; elle peut être annulée et expire après cinq minutes.

## Fiches et catalogues

- Les notes, langues, identifiants externes, titres alternatifs et statuts ne sont plus perdus lors des copies ou de la mise en cache locale d'une fiche.
- Les bannières, affiches, personnages et vignettes d'épisodes utilisent le chargeur d'images commun compatible avec les cookies des sites protégés.
- Les rangées de catalogue sont échantillonnées par familles (films, séries, anime, direct) au lieu de ne retenir que les premières rangées de films.
- Un lecteur dont la langue n'est pas fournie est affiché comme « Langue non indiquée » plutôt que « Autre ».
- Les liens reçus au fil de l'eau sont dédoublonnés avant leur affichage.

## Extensions FR 2.1.0

- Corrections ciblées pour 1Jour1Film, AnimeSite, AnimoFlix, FRAnime, Vostfree et Zenix.
- Rejet des playlists factices ou minuscules connues, résolution concurrente des hébergeurs et prise en charge VOE actualisée.
- Nouvelle extension Wiflix dédiée.

## Téléchargements

- Les ajouts DASH et sous-titres HLS préparés pour la 0.32 sont inclus, avec correction de deux erreurs Kotlin qui empêchaient cette version de compiler et donc d'être publiée.

## Validation

- Compilation Kotlin de l'application et des extensions affectées sous JDK 17 / Android SDK 35.
- Tests JVM de l'application, du cœur, du chargeur d'extensions et du socle commun des extensions.
- Tests ajoutés pour l'équilibrage des catégories et le filtrage des playlists HLS invalides ou trop courtes.

## Installation

Télécharger **endless-sea-0.33.0-debug.apk**.

- Android 8.0 / API 26 minimum.
- `versionName = 0.33.0-debug` ; `versionCode = 33`.
- Identifiant : `dev.endlesssea.app.debug`.
- APK debug signé avec la clé debug fixe publique du dépôt ; la mise à jour peut s'installer par-dessus les versions debug précédentes de même signature.
- Vérifier l'archive avec `sha256sum -c SHA256SUMS.txt`.

## Limites

- Les protections anti-bot restent interactives : l'application ne contourne pas automatiquement les contrôles destinés aux humains.
- Les domaines et lecteurs tiers peuvent changer sans préavis ; le réglage d'adresse de chaque source reste disponible quand il est pris en charge.
