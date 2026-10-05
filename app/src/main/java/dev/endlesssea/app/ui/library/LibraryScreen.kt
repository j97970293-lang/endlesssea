package dev.endlesssea.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.MediaCard

// Onglet → catégorie de stockage (identifiants MediaType de l'API extensions)
val LIBRARY_TABS = listOf(
    "Favoris" to "FAV",
    "Anime" to "ANIME",
    "Films" to "MOVIE",
    "Séries" to "SERIES",
    "OVA" to "OVA",
    "ONA" to "ONA",
    "🎬 Fichiers" to "LOCAL",
)

/**
 * Bibliothèque locale (spec §8) : fonctionne hors ligne, catégories, favoris,
 * progression par titre, tailles/qualités/langues affichées sur les fiches.
 */
@Composable
fun LibraryScreen(
    onMediaClick: (String) -> Unit,
    viewModel: LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    var tab by remember { mutableIntStateOf(0) }
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            LIBRARY_TABS.forEachIndexed { i, (label, key) ->
                Tab(
                    selected = tab == i,
                    onClick = {
                        tab = i
                        if (key == "LOCAL") viewModel.setLocalMode(true)
                        else { viewModel.setLocalMode(false); viewModel.onCategory(key) }
                    },
                    text = { Text(label) },
                )
            }
        }
        // ---- Onglet « Fichiers » : panneau vidéos locales et on s'arrête là
        if (LIBRARY_TABS[tab].second == "LOCAL") {
            LocalFilesPanel(viewModel, state)
            return@Column
        }

        // ---- Filtre bibliothèque (comme Anymex : Tous / Sur l'appareil)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.FilterChip(
                selected = !state.onDeviceOnly,
                onClick = { viewModel.setOnDeviceOnly(false) },
                label = { Text("Tous") },
            )
            androidx.compose.material3.FilterChip(
                selected = state.onDeviceOnly,
                onClick = { viewModel.setOnDeviceOnly(true) },
                label = { Text("💾 Sur l'appareil") },
            )
            // Watchlist §29 : filtre par statut de suivi
            listOf(
                "WISHLIST" to "📋 À regarder",
                "WATCHING" to "▶ En cours",
                "COMPLETED" to "✅ Terminés",
                "DROPPED" to "✖ Abandonnés",
            ).forEach { (st, label) ->
                androidx.compose.material3.FilterChip(
                    selected = state.filterStatus == st,
                    onClick = { viewModel.setFilterStatus(if (state.filterStatus == st) "ALL" else st) },
                    label = { Text(label) },
                )
            }
        }
        if (state.items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("(っ˘̩╭╮˘̩)っ", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "« ${LIBRARY_TABS[tab].first} » est vide",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    "Ajoutez des titres depuis une fiche (boutons « Ajouter à ma liste » / cœur) ou la bannière d'accueil.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items, key = { it.id }) { item ->
                    MediaCard(item = item, onClick = { onMediaClick(item.id) })
                }
            }
        }
    }
}

// ================================================================  FICHIERS LOCAUX

/**
 * §bibliothèque-locale : panneau de l'onglet « 🎬 Fichiers ». Multi-répertoires SAF,
 * lecture hors-ligne via le lecteur interne, métadonnées éditables (titre + affiche).
 */
@Composable
private fun LocalFilesPanel(
    viewModel: LibraryViewModel,
    state: dev.endlesssea.app.ui.library.LibraryUiState,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var editMeta by remember { androidx.compose.runtime.mutableStateOf<LocalVideoUi?>(null) }

    // Choix d'un dossier SAF (persistance longue durée incluse)
    val dirPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.addLocalDir(it.toString())
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Glossaire : ajouter un dossier, retirer, scanner à nouveau
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.AssistChip(
                onClick = { dirPicker.launch(null) },
                label = { Text("📁 Ajouter un dossier") },
            )
            viewModel.dirs.collectAsState().value.forEach { dir ->
                androidx.compose.material3.AssistChip(
                    onClick = { viewModel.removeLocalDir(dir) },
                    label = {
                        Text("✕ " + android.net.Uri.parse(dir).path
                            ?.substringAfterLast(':')?.substringAfterLast('/') ?: dir)
                    },
                )
            }
            androidx.compose.material3.AssistChip(
                onClick = { viewModel.scanLocal() },
                label = { Text("↻ Scanner") },
            )
        }
        Text(
            "Lecture hors-ligne des vidéos trouvées (y compris celles NON téléchargées via l'app). " +
                "Touche une vidéo pour la lire, bouton ✎ pour renommer/choisir l'affiche.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (state.localScanning) {
            Row(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
            ) { androidx.compose.material3.CircularProgressIndicator() }
        } else if (state.localFiles.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text("🎞", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (state.localFiles.isEmpty() && viewModel.dirs.collectAsState().value.isEmpty())
                        "Aucun dossier — ajoute-en un avec « 📁 Ajouter un dossier » (plusieurs possibles)."
                    else "Aucune vidéo trouvée dans les dossiers choisis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp),
            ) {
                items(state.localFiles, key = { it.uri }) { video ->
                    dev.endlesssea.app.ui.components.GlassCard(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            // Affiche : éditée > icône vidéo
                            if (video.customCoverUri != null) {
                                dev.endlesssea.app.SafeAsyncImage(
                                    url = video.customCoverUri,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 10.dp).width(56.dp).height(84.dp),
                                    placeholderModifier = Modifier.padding(end = 10.dp).width(56.dp).height(84.dp),
                                )
                            } else {
                                Text("🎬", style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.padding(end = 10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    video.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                )
                                Text(
                                    listOfNotNull(video.humanSize.ifBlank { null }, video.humanDuration.ifBlank { null })
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                video.customTitle?.let {
                                    Text(
                                        "Fichier : ${video.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            androidx.compose.material3.TextButton(onClick = { editMeta = video }) {
                                Text("✎")
                            }
                            androidx.compose.material3.Button(onClick = {
                                dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
                                    title = video.displayName,
                                    mediaId = null, episodeId = video.uri,
                                    links = listOf(
                                        dev.endlesssea.extensions.api.model.VideoLink(
                                            url = video.uri,
                                            streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                                            quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN,
                                            server = "Fichier local",
                                        ),
                                    ),
                                    startIndex = 0,
                                )
                                context.startActivity(
                                    android.content.Intent(context, dev.endlesssea.app.ui.player.PlayerActivity::class.java),
                                )
                            }) { Text("Lire") }
                        }
                    }
                }
            }
        }

        // Dialogue métadonnées §métadonnées-locales
        editMeta?.let { video ->
            var title by androidx.compose.runtime.remember(video.uri) {
                androidx.compose.runtime.mutableStateOf(video.customTitle ?: "")
            }
            var cover by androidx.compose.runtime.remember(video.uri) {
                androidx.compose.runtime.mutableStateOf(video.customCoverUri ?: "")
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { editMeta = null },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        viewModel.saveLocalMeta(
                            video.uri,
                            title.takeIf { it.isNotBlank() },
                            cover.takeIf { it.isNotBlank() },
                        )
                        editMeta = null
                    }) { Text("Enregistrer") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { editMeta = null }) { Text("Annuler") }
                },
                title = { Text("Métadonnées du fichier") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            video.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = title, onValueChange = { title = it },
                            label = { Text("Titre affiché") }, singleLine = true,
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = cover, onValueChange = { cover = it },
                            label = { Text("Affiche (URL ou content://)") }, singleLine = true,
                        )
                        Text(
                            "Laisse vide pour revenir aux informations du fichier. " +
                                "Les modifications sont conservées par l'app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}
