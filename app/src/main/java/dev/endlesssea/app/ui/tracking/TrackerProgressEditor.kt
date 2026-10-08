package dev.endlesssea.app.ui.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One request even for episode 1000. Never silently lower progress on the remote service. */
@Composable
fun TrackerProgressEditor(current: Int, total: Int, onSave: (Int) -> Unit) {
    var edit by remember { mutableStateOf(false) }
    TextButton(onClick = { edit = true }) { Text("Définir les épisodes vus…") }
    if (edit) {
        var value by remember(current) { mutableStateOf(current.toString()) }
        val number = value.toIntOrNull()
        val valid = number != null && number >= current && (total <= 0 || number <= total)
        AlertDialog(onDismissRequest = { edit = false }, title = { Text("Progression du suivi") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = value, onValueChange = { value = it.filter(Char::isDigit).take(7) },
                    label = { Text("Épisodes déjà vus") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Exemple : 850. La progression distante plus avancée sera conservée. Pour diminuer, corrigez-la sur le service.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(enabled = valid, onClick = { number?.let(onSave); edit = false }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("Annuler") } })
    }
}
