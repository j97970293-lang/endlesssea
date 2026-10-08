# Tailles de téléchargement : valeurs connues et inconnues

## Défauts identifiés

L'ancienne prévisualisation HLS prenait les trois premières URI de la playlist et multipliait leur taille moyenne par le nombre d'URI. Cela confondait parfois variantes d'une playlist maître et segments vidéo, et supposait des segments de taille uniforme. Le moteur extrapolait aussi la taille finale à partir du début déjà téléchargé. Ni l'un ni l'autre ne permet de promettre « 300 Mo » pour une vidéo à débit variable.

## Affichage corrigé

- HLS avant téléchargement : **estimation chiffrée rétablie**, calculée sur la qualité réellement sélectionnée. Voir la méthode ci-dessous ; « inconnue » reste réservé aux cas où les données ne permettent pas de calculer.
- HLS pendant téléchargement : octets réellement écrits et nombre de segments terminés. La barre représente l'avancement en segments, pas un pourcentage de taille totale estimée ni de durée.
- HLS terminé : taille réelle du fichier assemblé, comme auparavant. Les sous-titres séparés ne sont pas inclus dans cette taille.
- Fichier direct : taille **annoncée par le serveur**, à partir d'un GET Range puis HEAD en repli si la méthode n'est pas prise en charge. Une réponse partielle utilise le total de Content-Range, jamais son Content-Length d'un seul octet. Une page HTML, une playlist, un encodage compressé ou une réponse invalide ne fournit pas de taille vidéo.
- L'interface et le moteur partagent la détection HLS/DASH par type déclaré et suffixe d'URL, même si une extension appelle un `.m3u8` « fichier direct ».
- Quatre lignes peuvent être analysées simultanément côté sélecteur. L'estimateur HLS partage une limite de trois appels simultanés, un budget de coroutine de 20 secondes et trois secondes par appel HTTP (un repli HEAD peut demander un second appel). Les appels synchrones en cours peuvent finir après l'annulation du budget. Les fichiers directs gardent huit secondes par appel. Aucun corps vidéo complet n'est lu pour calculer la taille.

Une taille annoncée par un serveur peut changer ou être incorrecte. Cette correction ne constitue pas une garantie de place disponible ni une mesure du trafic réseau total (requêtes, reprises et sous-titres peuvent s'y ajouter). Aucun téléchargement existant n'est supprimé par ce correctif d'affichage.

## Vérification

`DownloadSizeSemanticsTest` (7 cas) et `HttpSizeProbeTest` (8 cas, MockWebServer) : classification, progression sans total inventé, réponse Range, repli HEAD, contenus non vidéo, erreurs et encodage. Tester sur appareil avec une vidéo HLS dont les premiers segments sont petits puis les suivants volumineux, un serveur direct et un serveur refusant Range.

## Qualité et reprises HLS

- Une qualité explicitement choisie (ex. 720p) sélectionne une variante dont la hauteur `RESOLUTION` correspond. Si elle est absente ou non identifiable, l'application refuse de choisir silencieusement une autre qualité. `UNKNOWN` conserve la sélection automatique du débit le plus élevé.
- `BANDWIDTH` n'est plus confondu avec `AVERAGE-BANDWIDTH`. Les URI relatives suivent la destination finale des redirections. Les playlists imbriquées sont bornées à quatre manifestes et 2 Mio par manifeste.
- Les playlists `EXT-X-BYTERANGE` et les initialisations à plage d'octets sont refusées : le moteur ne les implémente pas et ne doit pas recopier la ressource complète pour chaque segment.
- L'écriture d'un segment et son checkpoint ne sont plus séparés par une annulation de coroutine. Pause/annulation attendent la fin de cette section.
- Les nouveaux checkpoints HLS mémorisent la longueur cumulée validée. À la reprise, seuls les octets ajoutés après le dernier checkpoint sont retirés, puis le segment est retéléchargé : pas d'ajout en double d'une fin non validée.
- Une empreinte SHA-256 du plan (playlist, segments, initialisation, paramètres de clé) est conservée dans le cache ; aucun jeton en clair n'est ajouté à ce marqueur.
- **Limite volontaire :** un ancien partiel sans checkpoint vérifiable, un fichier trop court ou un plan différent nécessite d'annuler puis de relancer la tâche. Le refus lui-même ne supprime pas le partiel ; l'annulation peut ensuite le supprimer selon l'option choisie. Les vidéos déjà terminées ne sont pas modifiées. Des URL signées renouvelées peuvent également déclencher ce refus conservateur.
- La progression connue reste affichée pendant la pause. La reprise ne garantit pas une récupération après corruption disque ou coupure électrique ; elle protège la cohérence des segments lors des interruptions ordinaires.

`HlsQualityAndResumeTest` ajoute 14 cas : qualité 720p/automatique/absente, débit moyen distinct, URLs relatives/redirection, refus des plages d'octets, annulation pendant le checkpoint, retrait d'une fin non validée, conservation des anciens partiels, empreinte de variante et égalité octet par octet après une reprise simulée.

Tests appareil indispensables : choisir 720p dans un manifeste proposant aussi 1080p, mettre en pause/reprendre plusieurs fois et comparer le fichier final à un téléchargement sans interruption, tester une reprise ancienne et une URL signée renouvelée. Ces tests réseau unitaires ne reproduisent pas encore le serveur précis signalé par l'utilisateur.


## Estimation chiffrée avant téléchargement (demande de type IDM)

Le chiffre est de nouveau affiché, sans reprendre l'extrapolation des trois premiers segments :

1. Résolution par **le même moteur HLS que le téléchargement**, avec la même qualité et les mêmes en-têtes.
2. Pour un flux fini (`EXT-X-ENDLIST`), découpage de la vidéo en douze régions au maximum et sondage d'un segment représentatif par région, du début à la fin.
3. Pondération par les durées réelles `EXTINF` de chaque région, plutôt que par le seul nombre de segments. La taille de l'initialisation fMP4 est ajoutée.
4. Affichage `≈ X Mo estimés`, du nombre de segments analysés et, lorsque les débits mesurés varient, d'une **plage indicative** calculée à partir des débits observés. Cette plage n'est ni un plafond garanti ni un intervalle de confiance statistique : des segments non sondés peuvent la dépasser.
5. Si tous les segments ont été mesurés (notamment une petite playlist), affichage de la somme des **tailles annoncées par le serveur**, sans extrapolation.

L'estimation est refusée pour une fenêtre de direct, un sondage incomplet, une initialisation de taille inconnue ou des durées invalides nécessaires à l'extrapolation. Les sous-titres séparés et le surcoût réseau/reprises ne sont pas compris. Pendant le téléchargement, la progression conserve les octets réels et les segments ; aucun total estimé n'est enregistré comme taille finale réelle en base.

`HlsSizeEstimateTest` ajoute 12 tests : couverture de toute la vidéo, petit début peu représentatif, durée variable, somme complète avec initialisation, direct, sondages manquants, durées invalides, dépassements numériques, sélection réseau 720p et en-têtes transmis. Les 14 tests de qualité/reprise sont aussi réexécutés. Une estimation HLS ne peut pas promettre à l'avance le poids exact sans connaître la taille de tous les segments ; comparaison sur les vrais serveurs toujours nécessaire.
