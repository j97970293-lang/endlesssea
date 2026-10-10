package dev.endlesssea.app.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.glass
import dev.endlesssea.core.model.DownloadStatus

/** Onglet Téléchargements : file live, filtres par statut, tri, réorganisation. */
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    /** §deplacer-téléchargement : id de la tâche en cours de déplacement SAF. */
    var exportTargetId by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    // §appui-long : confirmation avant suppression
    var deleteCandidate by remember {
        androidx.compose.runtime.mutableStateOf<DownloadRowUi?>(null)
    }
    deleteCandidate?.let { row ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.cancel(row.id)
                    deleteCandidate = null
                }) { Text("Supprimer") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteCandidate = null }) {
                    Text("Annuler")
                }
            },
            title = { Text("Supprimer ce téléchargement ?") },
            text = { Text(row.title) },
        )
    }
    val treeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { u ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    u,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            exportTargetId?.let { viewModel.exportToTree(it, u.toString()) }
            exportTargetId = null
        }
    }
    // §retrouver-téléchargements : confirmations/erreurs (toast simple)
    androidx.compose.runtime.LaunchedEffect(state.notice) {
        state.notice?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearNotice()
        }
    }

    var collapsedSeries by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<String>()) }
    var languageFilter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var serverFilter by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    fun languageOf(row: DownloadRowUi): String {
        val name = row.title.uppercase()
        return when {
            "VOSTFR" in name -> "VOSTFR"
            "VF" in name -> "VF"
            "MULTI" in name -> "MULTI"
            "VO" in name -> "VO"
            else -> "Autre"
        }
    }
    fun serverOf(row: DownloadRowUi) = row.detail.substringBefore(" ·").ifBlank { "Source" }
    val visibleRows = state.rows.filter { row ->
        (languageFilter == null || languageOf(row) == languageFilter) &&
            (serverFilter == null || serverOf(row) == serverFilter)
    }
    val languages = state.rows.map { languageOf(it) }.distinct().sorted()
    val servers = state.rows.map { serverOf(it) }.distinct().sorted()
    Column(Modifier.fillMaxSize()) {
        // §barre-haut (conversation 4) : verre liquide — nombre de téléchargements.
        dev.endlesssea.app.ui.components.EndlessSeaTopBar(
            title = "Téléchargements",
            subtitle = "${state.totalCount} tâche(s) · ${state.rows.count { it.status == "DOWNLOADING" }} active(s)",
            icon = Icons.Filled.Download,
        )
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
                    label = { Text(f.label, maxLines = 1, softWrap = false) },
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
                    label = { Text(s.label, maxLines = 1, softWrap = false) },
                )
            }
            Text(
                if (state.ascending) "▲ croissant" else "▼ récent en haut",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { viewModel.toggleOrder() }.padding(6.dp),
            )
        }
        // ---- §entretien (conversation 10) : intégrité + nettoyage
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.AssistChip(
                onClick = { viewModel.verifyIntegrity() },
                label = { Text("Vérifier l'intégrité (SHA-256)", maxLines = 1, softWrap = false) },
            )
            androidx.compose.material3.AssistChip(
                onClick = { viewModel.tidy() },
                label = { Text("Nettoyer maintenant", maxLines = 1, softWrap = false) },
            )
        }
        if (languages.isNotEmpty()) {
            Text("Langue", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = languageFilter == null, onClick = { languageFilter = null }, label = { Text("Toutes") })
                languages.forEach { lang ->
                    FilterChip(selected = languageFilter == lang, onClick = { languageFilter = lang }, label = { Text(lang) })
                }
            }
        }
        if (servers.isNotEmpty()) {
            Text("Serveurs", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = serverFilter == null, onClick = { serverFilter = null }, label = { Text("Tous") })
                servers.take(12).forEach { server ->
                    FilterChip(selected = serverFilter == server, onClick = { serverFilter = server }, label = { Text(server, maxLines = 1, softWrap = false) })
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visibleRows.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("(._.)", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (state.totalCount == 0)
                                "Aucun téléchargement. Depuis une fiche : « Télécharger » serveur + qualité."
                            else "Aucun élément dans ce filtre.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            visibleRows.groupBy { it.seriesKey }.forEach { (seriesKey, episodes) ->
                item(key = "series:$seriesKey") {
                    androidx.compose.material3.TextButton(onClick = {
                        collapsedSeries = if (seriesKey in collapsedSeries) collapsedSeries - seriesKey else collapsedSeries + seriesKey
                    }) {
                        Text("${if (seriesKey in collapsedSeries) "▸" else "▾"} ${episodes.first().seriesTitle} · ${episodes.size} épisode(s)",
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (seriesKey !in collapsedSeries) items(episodes, key = { it.id }) { task ->
                DownloadCard(
                    row = task,
                    modifier = if (dev.endlesssea.app.ui.motion.LocalAppMotion.current.enabled) Modifier.animateItem() else Modifier,
                    onPause = { viewModel.pause(task.id) },
                    onResume = { viewModel.resume(task.id) },
                    onCancel = { viewModel.cancel(task.id) },
                    onMoveUp = { viewModel.reorder(task.id, up = true) },
                    onMoveDown = { viewModel.reorder(task.id, up = false) },
                    onPlay = {
                        viewModel.play(task.id) {
                            context.startActivity(
                                android.content.Intent(
                                    context,
                                    dev.endlesssea.app.ui.player.PlayerActivity::class.java,
                                ),
                            )
                        }
                    },
                    onMove = { exportTargetId = task.id; treeLauncher.launch(null) },
                    // §appui-long : supprimer un téléchargement
                    onLongPress = { deleteCandidate = task },
                )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DownloadCard(
    row: DownloadRowUi,
    modifier: Modifier = Modifier,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPlay: () -> Unit = {},
    onMove: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val motion = dev.endlesssea.app.ui.motion.LocalAppMotion.current
    val visualProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (row.fraction.isFinite()) row.fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = androidx.compose.animation.core.tween(motion.duration(220)), label = "downloadProgress",
    )
    // Conteneur « verre » (liquid glass sur toutes les surfaces, pas seulement les boutons)
    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongPress,
            )
            .glass(18.dp),
    ) {
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
                    DownloadStatus.COMPLETED.name -> {
                        IconButton(onClick = onPlay) {
                            Icon(
                                Icons.Filled.PlayArrow, "Lire le fichier",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onMove) {
                            Icon(
                                Icons.Filled.Folder, "Déplacer dans un dossier (carte SD…)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {}
                }
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Annuler") }
            }
            if (row.status == DownloadStatus.DOWNLOADING.name || row.status == DownloadStatus.QUEUED.name || row.status == DownloadStatus.PAUSED.name) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { visualProgress.value },
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
    /** Cible actuelle (file:// ou SAF content://) — lecture & « Déplacer ». */
    val targetUri: String = "",
    val seriesKey: String = "",
    val seriesTitle: String = "Téléchargements",
)
