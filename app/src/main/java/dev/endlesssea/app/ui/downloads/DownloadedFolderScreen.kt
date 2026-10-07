package dev.endlesssea.app.ui.downloads

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.GlassCard
import dev.endlesssea.app.ui.library.DownloadedEpisodeUi
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
                    Modifier.fillMaxWidth().clickable { playDownloaded(context, episodes, episode) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
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
                            listOf(episode.humanSize, episode.quality.takeIf { it != "UNKNOWN" })
                                .filterNotNull().joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("▶", style = MaterialTheme.typography.titleMedium)
                }
            }
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
