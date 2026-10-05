package dev.endlesssea.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * §glisser-serveurs — liste réordonnable par GLISSER-DÉPOSER (appui long sur la
 * poignée ≡) avec bascule actif/inactif. Le plus haut = serveur prioritaire n°1.
 *
 * Implémentation auto-suffisante (pas de bibliothèque externe, Android 8 OK) :
 * à chaque franchissement d'une demi-hauteur d'item, on permute dans la liste
 * parente via [onMove].
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, index: Int, dragging: Boolean, handle: Modifier) -> Unit,
) {
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember { mutableStateOf(-1) }
    var itemHeightPx by remember { mutableFloatStateOf(64f) }

    Column(modifier) {
        items.forEachIndexed { i, item ->
            val k = key(item)
            val dragging = draggingKey == k
            Box(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { itemHeightPx = it.size.height.toFloat().coerceAtLeast(1f) }
                    .graphicsLayer {
                        translationY = if (dragging) dragOffset else 0f
                        shadowElevation = (if (dragging) 8 else 0).dp.toPx()
                    }
                    // Item en cours de drag au-dessus
                    .let { m -> if (dragging) m.graphicsLayer { translationY = dragOffset } else m },
            ) {
                val handle = Modifier
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(k) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingKey = k
                                dragOffset = 0f
                                dragIndex = items.indexOfFirst { key(it) == k }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                                val shift = (dragOffset / (itemHeightPx / 2f)).roundToInt()
                                val from = items.indexOfFirst { key(it) == k }
                                val to = (dragIndex + if (dragOffset > 0) shift else shift)
                                    .coerceIn(0, items.lastIndex)
                                if (from in 0..items.lastIndex && to != from && abs(dragOffset) > itemHeightPx / 2f) {
                                    onMove(from, to)
                                    dragOffset = 0f
                                    dragIndex = to
                                }
                            },
                            onDragEnd = { draggingKey = null; dragOffset = 0f; dragIndex = -1 },
                            onDragCancel = { draggingKey = null; dragOffset = 0f; dragIndex = -1 },
                        )
                    }
                itemContent(item, i, dragging, handle)
            }
        }
    }
}

/** Ligne « serveur » prête à l'emploi : rang + nom + switch + poignée de drag. */
@Composable
fun ServerPriorityRow(
    rank: Int,
    server: String,
    active: Boolean,
    dragging: Boolean,
    onToggle: (Boolean) -> Unit,
    handleModifier: Modifier,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (dragging) MaterialTheme.colorScheme.surfaceVariant
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(server, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = active, onCheckedChange = onToggle)
        Icon(
            if (active) Icons.Filled.Menu else Icons.Filled.Check,
            contentDescription = "Glisser pour réordonner",
            modifier = handleModifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Placement pixel d'un int offset (utilitaire drag). */
private fun IntOffset.Companion.fromY(y: Float) = IntOffset(0, y.roundToInt())
