package dev.endlesssea.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.motion.rememberMotionPhase

/** A quiet ocean-ring loader shared by pages, buttons and player buffering. */
@Composable
fun EsLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 3.dp,
) {
    val phase = rememberMotionPhase(1400)
    Canvas(modifier.size(40.dp).progressSemantics().semantics { contentDescription = "Chargement en cours" }) {
        val width = strokeWidth.toPx().coerceAtMost(size.minDimension / 6f)
        val diameter = (size.minDimension - width).coerceAtLeast(0f)
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(color.copy(alpha = 0.12f), 0f, 360f, false, topLeft, arcSize, style = Stroke(width))
        repeat(3) { index ->
            drawArc(color.copy(alpha = 1f - index * 0.28f), phase.value * 360f + index * 120f,
                78f - index * 14f, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}

/** One drawing clock for the whole placeholder, rather than one animation per poster. */
@Composable
fun CatalogLoadingSkeleton(modifier: Modifier = Modifier) {
    val phase = rememberMotionPhase(1800, resting = 0.45f)
    val base = MaterialTheme.colorScheme.surfaceVariant
    val glow = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(280.dp).padding(16.dp).clipToBounds().progressSemantics()
        .semantics { contentDescription = "Chargement du catalogue" }) {
        val gap = 12.dp.toPx()
        val width = ((size.width - gap * 2) / 3).coerceAtLeast(0f)
        val posterHeight = (size.height * 0.72f).coerceAtLeast(0f)
        val sweep = phase.value * size.width * 2 - size.width
        val brush = Brush.linearGradient(listOf(base.copy(alpha = 0.55f), glow.copy(alpha = 0.16f), base.copy(alpha = 0.55f)),
            start = Offset(sweep, 0f), end = Offset(sweep + size.width, size.height))
        repeat(3) { column ->
            val x = column * (width + gap)
            drawRoundRect(brush, Offset(x, 0f), Size(width, posterHeight), CornerRadius(14.dp.toPx()))
            drawRoundRect(brush, Offset(x, posterHeight + gap), Size(width * 0.86f, 10.dp.toPx()), CornerRadius(5.dp.toPx()))
            drawRoundRect(brush, Offset(x, posterHeight + gap + 18.dp.toPx()), Size(width * 0.58f, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
    }
}
