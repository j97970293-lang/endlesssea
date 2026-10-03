package dev.endlesssea.app.ui.details

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.endlesssea.app.ui.player.PlayerActivity
import dev.endlesssea.app.ui.components.GlassCard
import dev.endlesssea.extensions.api.model.Episode
import dev.endlesssea.extensions.api.model.VideoLink

/**
 * Fiche détaillée (spec §13) : bannière + actions (Lire / Télécharger / Bibliothèque / Favoris),
 * épisodes regroupés par saison, serveurs visibles, mode auto, feuille « un clic ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    mediaId: String,
    onBack: () -> Unit,
    onDownloadQueued: () -> Unit,
    viewModel: DetailsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var downloadSheetEpisode by remember { mutableStateOf<Episode?>(null) }

    // Messages du VM → snackbar FR (spec : erreurs/confirmations lisibles)
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    fun launchPlayer(): () -> Unit = { context.startActivity(Intent(context, PlayerActivity::class.java)) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Bannière
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) {
                    AsyncImage(
                        model = state.details?.bannerUrl ?: state.details?.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                                ),
                            ),
                    )
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                        Icon(Icons.Filled.ArrowBack, "Retour", tint = Color.White)
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(
                            state.details?.title ?: "",
                            style = MaterialTheme.typography.headlineSmall, color = Color.White,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOfNotNull(
                            state.details?.type?.name,
                            state.details?.year?.toString(),
                            state.details?.episodeCount?.let { "$it épisodes" } ?: if (state.episodes.isNotEmpty()) "${state.episodes.size} épisodes" else null,
                        ).joinToString("  ·  ")
                        if (meta.isNotBlank()) {
                            Text(meta, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.error != null && state.details == null) {
                item {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Erreur", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.load() }) { Text("Réessayer") }
                    }
                }
            } else {
                // ---- Actions
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                state.episodes.firstOrNull()?.let { ep ->
                                    viewModel.playEpisode(ep, onReady = launchPlayer())
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                        ) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Lire") }
                        OutlinedButton(
                            onClick = {
                                state.episodes.firstOrNull()?.let { ep -> downloadSheetEpisode = ep }
                            },
                            modifier = Modifier.weight(1.2f),
                        ) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(4.dp)); Text("Télécharger") }
                        IconButton(onClick = { viewModel.toggleLibrary(state.details?.type?.name ?: "ANIME") }) {
                            Icon(
                                if (state.inLibrary) Icons.Filled.Check else Icons.Filled.Add,
                                "Bibliothèque", tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                if (state.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                "Favoris", tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // ---- Synopsis / genres
                state.details?.synopsis?.let { synopsis ->
                    item {
                        Text(
                            synopsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                if (state.details?.genres?.isNotEmpty() == true) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.details?.genres?.take(6)?.forEach { genre ->
                                GlassCard(cornerRadius = 14.dp,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text(genre, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // ---- Épisodes
                if (state.episodes.isNotEmpty()) {
                    item {
                        Text(
                            "Épisodes",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(state.episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            loading = state.linksLoadingEpisode == episode.id,
                            onPlay = { viewModel.playEpisode(episode, onReady = launchPlayer()) },
                            onDownload = { downloadSheetEpisode = episode },
                        )
                    }
                } else if (state.details != null) {
                    item {
                        Text(
                            "Cette source ne liste aucun épisode — ouverture du lien direct.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                if (state.error != null) {
                    item {
                        Text(
                            state.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // ---- Feuille « Télécharger » (un clic : serveur × qualité)
    downloadSheetEpisode?.let { episode ->
        val links = state.linksByEpisode[episode.id] ?: emptyList()
        ModalBottomSheet(onDismissRequest = { downloadSheetEpisode = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    "Télécharger — ${episode.title ?: "Épisode ${episode.number.toInt()}"}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                if (links.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                } else {
                    // Tri qualité décroissante (valeur numérique dans le nom si présente)
                    links.sortedByDescending { it.quality.name }.forEach { link ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.enqueue(episode, link)
                                    downloadSheetEpisode = null
                                    onDownloadQueued()
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${link.server} · ${link.quality.name}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                val extras = buildList {
                                    if (link.audioLang.name != "OTHER") add(link.audioLang.name)
                                    if (link.subtitles.isNotEmpty()) add("${link.subtitles.size} sous-titres")
                                    add(link.streamType.name)
                                }.joinToString(" · ")
                                if (extras.isNotBlank()) {
                                    Text(extras, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        // Résolution des liens au premier affichage
        LaunchedEffect(episode.id) {
            if (state.linksByEpisode[episode.id] == null) viewModel.loadLinks(episode)
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    loading: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onPlay)) {
                Text(
                    "Épisode ${episode.number.toInt()}${episode.title?.let { " — $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.durationMs?.let {
                    Text("${it / 60_000} min", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (loading) {
                CircularProgressIndicator(Modifier.padding(12.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, "Télécharger") }
        }
    }
}

// ---- petite boîte de confirmation « un clic » (serveurs auto) — conservée pour un usage futur
@Composable
private fun ConfirmDownload(
    link: VideoLink,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onConfirm) { Text("Télécharger") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("${link.server} · ${link.quality.name}") },
        text = { Text("Le segment démarre en parallèle (${link.quality.name}).") },
    )
}
