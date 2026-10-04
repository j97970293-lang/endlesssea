package dev.endlesssea.app.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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

/** Onglet Téléchargements : file live, filtres par statut, tri, réorganisation. */
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // ---- Filtres par statut
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadFilter.entries.forEach { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = { Text(f.label) },
                )
            }
        }
        // ---- Tri (date / taille / nom + sens)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Tri :", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DownloadSort.entries.forEach { s ->
                FilterChip(
                    selected = state.sort == s,
                    onClick = { viewModel.setSort(s) },
                    label = { Text(s.label) },
                )
            }
            Text(
                if (state.ascending) "▲ croissant" else "▼ récent en haut",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { viewModel.toggleOrder() }.padding(6.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.rows.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("(._.)", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (state.totalCount == 0)
                                "Aucun téléchargement. Depuis une fiche : « Télécharger » → serveur + qualité."
                            else "Aucun élément dans ce filtre.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            items(state.rows, key = { it.id }) { task ->
                DownloadCard(
                    row = task,
                    onPause = { viewModel.pause(task.id) },
                    onResume = { viewModel.resume(task.id) },
                    onCancel = { viewModel.cancel(task.id) },
                    onMoveUp = { viewModel.reorder(task.id, up = true) },
                    onMoveDown = { viewModel.reorder(task.id, up = false) },
                )
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
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
                // Réorganisation de la file (priorité moteur)
                if (row.status == DownloadStatus.QUEUED.name) {
                    IconButton(onClick = onMoveUp) {
                        Icon(
                            Icons.Filled.ArrowUpward, "Monter dans la file",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onMoveDown) {
                        Icon(
                            Icons.Filled.ArrowDownward, "Descendre dans la file",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (row.status) {
                    DownloadStatus.DOWNLOADING.name, DownloadStatus.QUEUED.name ->
                        IconButton(onClick = onPause) { Icon(Icons.Filled.Pause, "Pause") }
                    DownloadStatus.PAUSED.name ->
                        IconButton(onClick = onResume) { Icon(Icons.Filled.PlayArrow, "Reprendre") }
                    DownloadStatus.FAILED.name ->
                        IconButton(onClick = onResume) {
                            Icon(Icons.Filled.Refresh, "Réessayer", tint = MaterialTheme.colorScheme.error)
                        }
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
                    row.progressLabel.ifBlank { statusLabel(row.status) },
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

private fun statusLabel(status: String) = when (status) {
    DownloadStatus.QUEUED.name -> "En file"
    DownloadStatus.PROBING.name -> "Analyse du lien…"
    DownloadStatus.DOWNLOADING.name -> "Téléchargement…"
    DownloadStatus.PAUSED.name -> "En pause"
    DownloadStatus.FAILED.name -> "Échec"
    DownloadStatus.COMPLETED.name -> "Terminé"
    else -> status
}

data class DownloadRowUi(
    val id: String,
    val title: String,
    val detail: String,
    val status: String,
    val fraction: Float,
    val progressLabel: String,
    val error: String?,
    val createdAt: Long = 0,
    val totalBytes: Long = 0,
)
