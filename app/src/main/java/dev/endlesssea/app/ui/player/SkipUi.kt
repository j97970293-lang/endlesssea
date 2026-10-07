package dev.endlesssea.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.skip.CustomSkipButton
import dev.endlesssea.app.skip.SkipSegment

/**
 * §megaskip — composants du lecteur :
 *  · [SkipSegmentPill] : « Passer l'intro » (+ compte à rebours du saut auto) ;
 *  · [MegaskipRow] : boutons de saut personnalisés (hors-ligne) ;
 *  · [CustomSkipDialog] : CRUD des boutons (aussi utilisé par les Réglages).
 */

@Composable
fun SkipSegmentPill(
    segment: SkipSegment,
    countdown: Int?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.FastForward,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                segment.type.actionLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    countdown != null && countdown > 0 -> "dans ${countdown} s · ${segment.humanDuration}"
                    else -> segment.humanDuration
                },
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Boutons de saut personnalisés — visibles uniquement s'ils existent. */
@Composable
fun MegaskipRow(
    buttons: List<CustomSkipButton>,
    accent: Color,
    onJump: (CustomSkipButton) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (buttons.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(buttons, key = { it.id }) { button ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .clickable { onJump(button) }
                    .combineLongPress(onLongPress)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        button.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        button.human,
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** Appui long (édition) sans casser le clic simple. */
private fun Modifier.combineLongPress(onLongPress: () -> Unit): Modifier =
    this.pointerInput(onLongPress) {
        detectTapGestures(onLongPress = { onLongPress() })
    }

/**
 * CRUD des boutons de saut personnalisés (conversation 1 : « boutons
 * configurables », « fonctionne intégralement hors ligne »).
 */
@Composable
fun CustomSkipDialog(
    buttons: List<CustomSkipButton>,
    onAdd: (String, Int) -> Unit,
    onUpdate: (CustomSkipButton) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("85") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terminer") } },
        title = { Text("Boutons de saut") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Ces boutons restent disponibles hors-ligne (enregistrés sur l'appareil).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                buttons.forEach { button ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            button.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(button.human, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = {
                                onUpdate(button.copy(enabled = !button.enabled))
                            },
                        ) { Text(if (button.enabled) "Actif" else "Inactif") }
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onDelete(button.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Delete, "Supprimer",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(24) },
                        label = { Text("Nom") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Sec.") },
                        singleLine = true,
                        modifier = Modifier.width(92.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onAdd(label.ifBlank { "Saut" }, seconds.toIntOrNull() ?: 85); label = "" }) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ajouter")
                    }
                    listOf(15, 30, 85, 90, 120).forEach { preset ->
                        TextButton(onClick = { seconds = preset.toString() }) { Text("${preset}s") }
                    }
                }
            }
        },
    )
}
