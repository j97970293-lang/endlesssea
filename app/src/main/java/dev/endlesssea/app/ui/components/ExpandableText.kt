package dev.endlesssea.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * §textes-longs : texte rogné (1 ou 2 lignes, jamais de défilement horizontal) ;
 * un toucher ouvre un dialogue avec le texte COMPLET. Utilisé pour les titres
 * d'épisode, noms de serveurs, noms de fichiers locaux…
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    maxLines: Int = 1,
    dialogTitle: String = "Texte complet",
) {
    var show by remember { mutableStateOf(false) }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clickable { show = true },
    )
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Fermer") } },
            title = { Text(dialogTitle) },
            text = { Column { Text(text) } },
        )
    }
}
