package dev.endlesssea.player

/**
 * §gestes-lecteur (conversation 2) — configuration **centralisée** des gestes
 * du lecteur : seuils et pas, au lieu de nombres magiques dispersés dans
 * l'activité. Les valeurs viennent des réglages de l'utilisateur (secondes du
 * saut, glisser luminosité/volume inversé, pincement activé ou non).
 *
 * Le comportement appliqué :
 *  - tap = afficher/masquer les commandes ;
 *  - double tap gauche/droite = saut de ±[skipSeconds] ;
 *  - appui long = vitesse ×[longPressSpeed] (restaurée au relâchement) ;
 *  - glisser horizontal = déplacement dans la vidéo avec aperçu ;
 *  - glisser vertical = luminosité (gauche) / volume (droite), inversable ;
 *  - pincement = zoom de 1× à [maxPinchZoom], déplacement à deux doigts.
 */
data class GestureConfig(
    /** Fenêtre pendant laquelle un nouvel appui enchaîne un saut (« double appui continu »). */
    val doubleTapWindowMs: Long = 900L,
    /** Secondes sautées par double appui (et par la pastille de saut). */
    val skipSeconds: Int = 10,
    /** Zoom maximal au pincement. */
    val maxPinchZoom: Float = 3f,
    /** Vitesse appliquée pendant un appui long. */
    val longPressSpeed: Float = 2f,
    /** Glisser vertical : luminosité à gauche et volume à droite (false = inversé). */
    val brightnessOnLeft: Boolean = true,
    /** Pas de réglage par pixel glissé (luminosité / volume). */
    val verticalStepPerPx: Float = 0.0016f,
    /** Déplacement horizontal requis (px) pour changer de vitesse au glisser. */
    val seekDeadZonePx: Float = 24f,
    /** Pincement activé (sinon les gestes à deux doigts sont ignorés). */
    val pinchZoomEnabled: Boolean = true,
    /** Glisser vertical activé. */
    val verticalSwipeEnabled: Boolean = true,
)
