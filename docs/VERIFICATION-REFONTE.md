# Vérification sur appareil — refonte du lecteur

Cible minimale : Android 8 / API 26. Effectuer aussi la vérification sur Android récent.
Les tests JVM ne remplacent pas ces vérifications de Compose, SAF, WebView et Room.

## Mise à jour et stockage

- Installer une version avec base Room v7, créer un compte et un rattachement, puis mettre à jour vers la base v8.
- Vérifier que bibliothèque, historique, téléchargements et comptes sont conservés.
- Les anciens rattachements doivent avoir la correspondance automatique **désactivée**.
- Révoquer l'autorisation SAF d'un dossier : l'application doit rester utilisable.

## Lecteur et playlist

- Vérifier l'interface Essentiel en paysage et en portrait ; vérifier que les identifiants historiques migrent vers Essentiel.
- Ouvrir paramètres, informations et playlist ; attendre plus de trois secondes. Les panneaux restent accessibles.
- Tester verrouillage, déverrouillage, glissement de progression et changement d'épisode.
- Ouvrir une fiche téléchargée, puis passer au mode avion : la lecture locale ne dépend pas de l'extension.
- Vérifier saison, numéro, vignette et durée dans la playlist lorsqu'ils sont connus.
- Mettre en arrière-plan puis rouvrir : la position doit avoir été conservée.
- Un épisode marqué vu repart du début à sa prochaine lecture.

## Fiches locales et historique

- Scanner un dossier contenant S01E02 et S02E01 : tri par saison, puis épisode ; renommer la série sans perdre les numéros des fichiers.
- « Tout marquer vu » : vérifier annulation puis confirmation sur fiche streaming, locale et téléchargée.
- Vérifier que ce marquage n'envoie rien au tracker ; il fonctionne même si l'enregistrement automatique d'historique est désactivé.
- Balayer une entrée d'historique dans chaque direction : confirmer ou annuler la suppression.
- Supprimer toute une série puis les épisodes vus : ni les fichiers ni les rattachements tracker ne doivent disparaître.
- Une carte de reprise locale doit ouvrir la fiche locale, pas une extension nommée « local ».

## Matching et services

- Connecter un compte autorisé, rechercher un titre édité, sélectionner le résultat et confirmer.
- Aucune recherche n'est envoyée sans compte actif validé. TMDB reste un fournisseur de métadonnées, pas un tracker d'épisodes.
- Vérifier l'année et le titre distant : le rattachement ne récupère pas la progression déjà présente sur le service.
- Désactiver la correspondance : une lecture à 90 % ne modifie pas le suivi distant ; « +1 » reste manuel.
- Activer la correspondance et choisir la saison distante correspondante ; elle doit être numérotée depuis 1.
- Lire l'épisode 3 : progression 3 ; relire 3, puis lire 2 : progression toujours 3.
- Lire un épisode d'une autre saison, un spécial 12.5 ou un numéro inconnu : pas de synchronisation automatique.
- Lire hors ligne : progression conservée en attente ; rouvrir Comptes & suivi après reconnexion pour synchroniser.
- MAL : tester le renouvellement après HTTP 401 avec un client natif enregistré et un refresh token valide.
- Shikimori : tester création puis modification d'une entrée existante avec un jeton de portée user_rates.

## Bandes-annonces

- Tester vidéo HTTPS directe, YouTube, Vimeo et Dailymotion autorisant l'intégration.
- Plein écran : bouton du lecteur ou contrôle de l'hébergeur ; Retour sort d'abord du plein écran.
- Mettre en arrière-plan, revenir, quitter : aucun son ne doit continuer après fermeture.
- Un hébergeur interdisant l'intégration ne déclenche ni extraction ni ouverture externe automatique.

## OAuth restant à configurer

Aucun flux navigateur OAuth complet n'est fourni sans clients enregistrés.
Fournir les client IDs publics et les URI de retour autorisées pour AniList et MAL.
Pour Shikimori, l'échange exigeant un secret doit être réalisé par un backend de confiance ;
ne jamais embarquer ce secret dans Android.
