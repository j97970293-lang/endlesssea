# Tailles de téléchargement : valeurs connues et inconnues

## Défauts identifiés

L'ancienne prévisualisation HLS prenait les trois premières URI de la playlist et multipliait leur taille moyenne par le nombre d'URI. Cela confondait parfois variantes d'une playlist maître et segments vidéo, et supposait des segments de taille uniforme. Le moteur extrapolait aussi la taille finale à partir du début déjà téléchargé. Ni l'un ni l'autre ne permet de promettre « 300 Mo » pour une vidéo à débit variable.

## Affichage corrigé

- HLS avant téléchargement : **taille finale inconnue — débit variable**, sans chiffre extrapolé.
- HLS pendant téléchargement : octets réellement écrits et nombre de segments terminés. La barre représente l'avancement en segments, pas un pourcentage de taille totale estimée ni de durée.
- HLS terminé : taille réelle du fichier assemblé, comme auparavant. Les sous-titres séparés ne sont pas inclus dans cette taille.
- Fichier direct : taille **annoncée par le serveur**, à partir d'un GET Range puis HEAD en repli si la méthode n'est pas prise en charge. Une réponse partielle utilise le total de Content-Range, jamais son Content-Length d'un seul octet. Une page HTML, une playlist, un encodage compressé ou une réponse invalide ne fournit pas de taille vidéo.
- L'interface et le moteur partagent la détection HLS/DASH par type déclaré et suffixe d'URL, même si une extension appelle un `.m3u8` « fichier direct ».
- Les vérifications sont limitées à quatre simultanément côté sélecteur, avec un délai maximal de huit secondes par appel réseau. Aucun corps vidéo complet n'est lu pour calculer la taille.

Une taille annoncée par un serveur peut changer ou être incorrecte. Cette correction ne constitue pas une garantie de place disponible ni une mesure du trafic réseau total (requêtes, reprises et sous-titres peuvent s'y ajouter). Aucun téléchargement existant n'est supprimé par ce correctif d'affichage.

## Vérification

`DownloadSizeSemanticsTest` (7 cas) et `HttpSizeProbeTest` (8 cas, MockWebServer) : classification, progression sans total inventé, réponse Range, repli HEAD, contenus non vidéo, erreurs et encodage. Tester sur appareil avec une vidéo HLS dont les premiers segments sont petits puis les suivants volumineux, un serveur direct et un serveur refusant Range.
