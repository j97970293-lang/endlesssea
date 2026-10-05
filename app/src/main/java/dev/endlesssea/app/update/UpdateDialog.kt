package dev.endlesssea.app.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Dialogue de progression de mise à jour (§5) : téléchargement IN-APP visible,
 * validation, bouton « Installer » au moment venu, « Réessayer » sinon.
 * Se montre automatiquement dès que [UpdateDownloadState] quitte Idle.
 */
@Composable
fun UpdateProgressDialog(onRetry: (() -> Unit)? = null) {
    val dl by UpdateDownloadState.state.collectAsState()
    val context = LocalContext.current
    when (val st = dl) {
        UpdateDl.Idle -> Unit
        is UpdateDl.Downloading -> AlertDialog(
            onDismissRequest = { /* ne pas perdre la progression en cours */ },
            confirmButton = { },
            title = { Text("Téléchargement de la mise à jour…") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = st.progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (st.totalKb > 0) "${st.doneKb / 1024} Mo / ${st.totalKb / 1024} Mo"
                        else "${st.doneKb / 1024} Mo reçus…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Tout se passe dans l'application — ne la quitte pas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        is UpdateDl.Ready -> AlertDialog(
            onDismissRequest = { UpdateDownloadState.reset() },
            confirmButton = {
                Button(onClick = { AppUpdateInstaller.install(context, st.file) }) {
                    Text("Installer ${st.info.tag}")
                }
            },
            dismissButton = {
                TextButton(onClick = { UpdateDownloadState.reset() }) { Text("Plus tard") }
            },
            title = { Text("Mise à jour prête ✔") },
            text = {
                Text(
                    "Le fichier a été téléchargé et vérifié. Le système va te proposer " +
                        "l'installation par-dessus la version actuelle, sans perdre tes données.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
        is UpdateDl.Failed -> AlertDialog(
            onDismissRequest = { UpdateDownloadState.reset() },
            confirmButton = {
                Button(onClick = { onRetry?.invoke() ?: UpdateDownloadState.reset() }) {
                    Text(if (onRetry != null) "Réessayer" else "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { UpdateDownloadState.reset() }) { Text("Fermer") }
            },
            title = { Text("Mise à jour impossible") },
            text = {
                Column {
                    Text(st.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Détail technique : ${st.technical}\n\nTu peux aussi ouvrir la page Releases depuis le navigateur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
