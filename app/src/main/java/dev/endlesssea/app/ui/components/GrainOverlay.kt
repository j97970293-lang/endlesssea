package dev.endlesssea.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode

/**
 * §anymex-theme : « Grain Texture » — très légère texture de film posée sur
 * toute l'interface (comme AnyMEX). Statique, déterministe (graine fixe),
 * petits points blancs/noirs quasi transparents — aucun coût GPU visible.
 */
@Composable
fun GrainOverlay(modifier: Modifier = Modifier, density: Int = 1400) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxSize()) {
        // Positions pseudo-aléatoires stables de 0..1, régénérées à la taille.
        val rnd = kotlin.random.Random(7_331)
        val pts = List(density) { Offset(rnd.nextFloat(), rnd.nextFloat()) }
        val white = Color.White.copy(alpha = 0.035f)
        val black = Color.Black.copy(alpha = 0.045f)
        val wPts = pts.filterIndexed { i, _ -> i % 2 == 0 }
            .map { Offset(it.x * size.width, it.y * size.height) }
        val bPts = pts.filterIndexed { i, _ -> i % 2 != 0 }
            .map { Offset(it.x * size.width, it.y * size.height) }
        drawPoints(wPts, PointMode.Points, white, strokeWidth = 2f)
        drawPoints(bPts, PointMode.Points, black, strokeWidth = 2f)
        onSurface.hashCode() // ref thème si le schéma change (composition)
    }
}
