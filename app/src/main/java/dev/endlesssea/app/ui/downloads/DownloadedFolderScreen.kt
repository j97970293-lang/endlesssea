package dev.endlesssea.app.ui.downloads

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.local.LocalMediaType
import dev.endlesssea.app.ui.components.GlassCard
import dev.endlesssea.app.ui.library.DownloadedEpisodeUi
import dev.endlesssea.app.ui.library.LibrarySource
import dev.endlesssea.app.ui.library.LibraryViewModel
import dev.endlesssea.app.ui.player.PlayerLaunchStore

/**
 * §telecharges-bibliotheque (conversation 7) — « fiche » d'une série construite
 * à partir des fichiers téléchargés : on y voit les épisodes réellement
 * présents sur l'appareil et on les lit **hors-ligne**, sans extension ni réseau.
 *
 * Un épisode isolé (téléchargé seul, sans fiche source) possède lui aussi sa
 * fiche : c'est une série d'un épisode.
 */
@Composable
fun DownloadedFolderScreen(
    folderKey: String,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val group = state.downloadedGroups.firstOrNull { it.key == folderKey }
    val episodes = remember(state.downloadedEpisodes, folderKey) {
        viewModel.downloadedEpisodesOf(folderKey)
    }
    /** §gestion : épisode en attente de confirmation de suppression. */
    var confirmAllWatched by remember(folderKey) { mutableStateOf(false) }
    if (confirmAllWatched) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { confirmAllWatched = false },
            title = { Text("Tout marquer vu ?") },
            text = { Text("Les ${episodes.size} épisodes téléchargés seront marqués vus dans l'historique local, sans modifier le tracker.") },
            confirmButton = { TextButton(onClick = { viewModel.markDownloadedWatched(episodes); confirmAllWatched = false }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { confirmAllWatched = false }) { Text("Annuler") } })
    }
    var deleteCandidate by remember { mutableStateOf<DownloadedEpisodeUi?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("‹ Retour") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Box(
                    Modifier
                        .width(110.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    dev.endlesssea.app.SafeAsyncImage(
                        url = group?.posterUrl,
                        contentDescription = group?.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        group?.title ?: "Téléchargements",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${episodes.size} épisode(s) sur l'appareil · ${group?.humanSize ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Lecture hors-ligne : aucun réseau, aucune extension requise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (group != null) {
                        Text("Type de média", style = MaterialTheme.typography.labelLarge)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LocalMediaType.options.forEach { option ->
                                FilterChip(
                                    selected = group.mediaType == option.first,
                                    onClick = { viewModel.saveDownloadedMediaType(folderKey, group.mediaId, option.first) },
                                    label = { Text(option.second) },
                                )
                            }
                        }
                    }
                    // §netto-automatique (conversation 10) : purge différée des
                    // vieux épisodes pour ne pas remplir la carte SD.
                    AutoCleanControls(viewModel)
                    Spacer(Modifier.height(8.dp))
                    if (episodes.isNotEmpty()) {
                        androidx.compose.material3.Button(
                            onClick = { playDownloaded(context, episodes, episodes.first()) },
                        ) { Text("Lire") }
                    }
                }
            }
        }
        item {
            TextButton(onClick = { confirmAllWatched = true }, enabled = episodes.isNotEmpty(), modifier = Modifier.padding(horizontal = 16.dp)) { Text("Tout marquer vu") }
            Text(
                "Épisodes téléchargés",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (episodes.isEmpty()) {
            item {
                Text(
                    "Aucun fichier pour cette série : le téléchargement a peut-être été supprimé.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        items(episodes, key = { it.uri }) { episode ->
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                cornerRadius = 14.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f).clickable { playDownloaded(context, episodes, episode) },
                    ) {
                        Text(
                            if (episode.episodeNumber != null) {
                                "Ép. ${episode.episodeNumber} · ${episode.displayName}"
                            } else {
                                episode.displayName
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOf(
                                episode.humanSize,
                                episode.quality.takeIf { it != "UNKNOWN" },
                                LibrarySource.label(episode.storageKind),
                            ).filterNotNull().joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { playDownloaded(context, episodes, episode) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Lire")
                    }
                    // §gestion (conversation 7) : libérer l'espace d'un épisode
                    IconButton(onClick = { deleteCandidate = episode }) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer le téléchargement")
                    }
                }
            }
        }
    }

    // Confirmation : la suppression efface vraiment le fichier de l'appareil.
    deleteCandidate?.let { episode ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDownloadedEpisode(episode.taskId, episode.uri)
                    deleteCandidate = null
                }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Annuler") }
            },
            title = { Text("Supprimer ce téléchargement ?") },
            text = {
                Text(
                    "${episode.displayName} — ${episode.humanSize} libérés. " +
                        "Le fichier est effacé de l'appareil ; tu pourras le retélécharger plus tard.",
                )
            },
        )
    }
}

/**
 * §netto-automatique (conversation 10) : règle de purge des téléchargements
 * terminés, affichée sur la fiche — l'utilisateur voit ce qui disparaîtra et
 * peut vérifier immédiatement l'espace récupéré.
 */
@Composable
private fun AutoCleanControls(viewModel: LibraryViewModel) {
    val days by viewModel.autoCleanDays.collectAsState()
    var freeText by remember { mutableStateOf("") }
    Column(Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Nettoyage auto : ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(0 to "jamais", 3 to "3 j", 7 to "7 j", 30 to "30 j").forEach { (value, label) ->
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (days == value) FontWeight.Bold else FontWeight.Normal,
                    color = if (days == value) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clickable { viewModel.setAutoCleanDays(value) },
                )
            }
        }
        TextButton(onClick = { freeText = viewModel.cleanNow() }) {
            Text("Vérifier maintenant — " + freeText.ifBlank { "calculer l'espace" })
        }
    }
}

/**
 * File de lecture d'un groupe téléchargé : l'épisode suivant est le fichier
 * suivant du dossier, exactement comme une série locale.
 */
internal fun playDownloaded(
    context: android.content.Context,
    all: List<DownloadedEpisodeUi>,
    current: DownloadedEpisodeUi,
) {
    val links: (DownloadedEpisodeUi) -> List<dev.endlesssea.extensions.api.model.VideoLink> = { ep ->
        listOf(
            dev.endlesssea.extensions.api.model.VideoLink(
                url = ep.uri,
                streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN,
                server = "Téléchargement",
            ),
        )
    }
    PlayerLaunchStore.resolver = null
    PlayerLaunchStore.setQueue(
        all.map {
            PlayerLaunchStore.QueueItem(
                title = it.displayName,
                // clé d'historique stable : l'id d'épisode de la fiche quand il existe
                episodeId = it.episodeId ?: it.uri,
                links = links(it),
                downloaded = true, mediaId = it.mediaId, thumbnailUrl = it.thumbnailUrl ?: it.uri,
                episodeNumber = it.exactEpisodeNumber, season = it.season, durationMs = it.durationMs,
            )
        },
        all.indexOfFirst { it.uri == current.uri }.coerceAtLeast(0),
    )
    PlayerLaunchStore.set(
        title = current.displayName,
        mediaId = current.mediaId,
        episodeId = current.episodeId ?: current.uri,
        links = links(current),
        startIndex = 0,
    )
    context.startActivity(Intent(context, dev.endlesssea.app.ui.player.PlayerActivity::class.java))
}
