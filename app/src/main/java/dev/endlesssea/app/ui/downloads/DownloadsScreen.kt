package dev.endlesssea.app.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.endlesssea.core.model.DownloadStatus

/**
 * Téléchargements (spec §5) : file d'attente ordonnable, pause/reprise/annulation,
 * progression + vitesse + ETA, sections Actifs / Terminés.
 */
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.active.isEmpty() && state.finished.isEmpty()) {
            item {
                Text(
                    "Aucun téléchargement. Depuis une fiche : Télécharger → choisir serveur et qualité → démarrer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.active.isNotEmpty()) {
            item { Text("En cours", style = MaterialTheme.typography.titleMedium) }
            items(state.active, key = { it.id }) { task ->
                DownloadCard(
                    row = task,
                    onPause = { viewModel.pause(task.id) },
                    onResume = { viewModel.resume(task.id) },
                    onCancel = { viewModel.cancel(task.id) },
                )
            }
        }

        if (state.finished.isNotEmpty()) {
            item { Spacer(Modifier.height(8.dp)); Text("Terminés", style = MaterialTheme.typography.titleMedium) }
            items(state.finished, key = { it.id }) { task ->
                DownloadCard(row = task, onPause = {}, onResume = {}, onCancel = { viewModel.cancel(task.id) })
            }
        }
    }
}

@Composable
private fun DownloadCard(
    row: DownloadRowUi,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        row.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (row.status) {
                    DownloadStatus.DOWNLOADING.name, DownloadStatus.QUEUED.name ->
                        IconButton(onClick = onPause) { Icon(Icons.Filled.Pause, "Pause") }
                    DownloadStatus.PAUSED.name, DownloadStatus.FAILED.name ->
                        IconButton(onClick = onResume) { Icon(Icons.Filled.PlayArrow, "Reprendre") }
                    else -> {}
                }
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Annuler") }
            }
            if (row.status == DownloadStatus.DOWNLOADING.name || row.status == DownloadStatus.QUEUED.name || row.status == DownloadStatus.PAUSED.name) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { row.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    row.progressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.error != null) {
                Text(row.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

data class DownloadRowUi(
    val id: String,
    val title: String,
    val detail: String,
    val status: String,
    val fraction: Float,
    val progressLabel: String,
    val error: String? = null,
)
