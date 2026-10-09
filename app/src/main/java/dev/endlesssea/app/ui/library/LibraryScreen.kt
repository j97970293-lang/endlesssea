package dev.endlesssea.app.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.MediaCard
import dev.endlesssea.app.ui.search.SearchItemUi

// Onglet → catégorie de stockage (identifiants MediaType de l'API extensions)
val LIBRARY_TABS = listOf(
    "Favoris" to "FAV",
    "Anime" to "ANIME",
    "Films" to "MOVIE",
    "Séries" to "SERIES",
    "OVA" to "OVA",
    "ONA" to "ONA",
    "Fichiers" to "LOCAL",
)

/**
 * Bibliothèque locale (spec §8) : fonctionne hors ligne, catégories, favoris,
 * progression par titre, tailles/qualités/langues affichées sur les fiches.
 */
@Composable
fun LegacyLibraryScreen(
    onMediaClick: (String) -> Unit,
    /** §fiche-locale : ouverture de la fiche d'un dossier de vidéos locales. */
    onLocalFolderClick: (String) -> Unit = {},
    /** §telecharges-bibliotheque : fiche d'une « série » de fichiers téléchargés. */
    onDownloadedFolderClick: (String) -> Unit = {},
    viewModel: LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // §catégories-perso : onglets = catégories natives + celles créées par l'utilisateur
    val customCats by viewModel.customCategories.collectAsState()
    val catTick by viewModel.categoryItemsTick.collectAsState()
    var showNewCategory by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    var newCategoryName by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf("")
    }
    val tabs = remember(customCats, state.localMode) {
        if (state.localMode) listOf("Tous les dossiers" to "LOCAL") + customCats.map { it to "CUSTOM:$it" }
        else LIBRARY_TABS.filter { it.second != "LOCAL" }
    }
    val currentKey = state.navigation.destination
    val currentLabel = when {
        state.downloadsOnly -> "Téléchargements"
        else -> tabs.firstOrNull { it.second == currentKey }?.first ?: "Bibliothèque"
    }
    androidx.compose.runtime.LaunchedEffect(currentKey, customCats) {
        if (currentKey.startsWith("CUSTOM:") && currentKey.removePrefix("CUSTOM:") !in customCats) {
            viewModel.navigate("LOCAL")
        }
    }
    // §bibliotheque-locale-fusion : les fichiers locaux apparaissent dans la grille
    // normale (même affichage que les titres suivis), activable d'un chip.
    val mergeLocal by viewModel.mergeLocal.collectAsState()
    androidx.compose.runtime.LaunchedEffect(mergeLocal) { if (mergeLocal) viewModel.scanLocal() }
    val localCards = if (mergeLocal) viewModel.folderCards() else emptyList()
    /** §métadonnées-éditées : fiche en cours d'édition (appui long dans la grille). */
    var editMediaMeta by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<SearchItemUi?>(null)
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        // §barre-haut (conversation 4) : verre liquide — titre, sous-titre
        // (nombre de titres et de fichiers locaux), menu = nouvelle catégorie.
        dev.endlesssea.app.ui.components.EndlessSeaTopBar(
            title = "Bibliothèque",
            subtitle = when (state.navigation.area) {
                LibraryArea.COLLECTION -> "${state.items.size} titre(s) dans la collection"
                LibraryArea.DOWNLOADS -> "${state.downloadedGroups.size} titre(s) · ${state.downloadedEpisodes.size} épisode(s)"
                LibraryArea.FOLDERS -> "${state.localFiles.size} fichier(s) local(aux)"
            },
            icon = Icons.Filled.VideoLibrary,
            onMenu = { showNewCategory = true },
            menuDescription = "Nouvelle catégorie",
        )
        LibraryWorkspaceNavigation(
            navigation = state.navigation,
            destinations = tabs,
            onArea = viewModel::openArea,
            onDestination = viewModel::navigate,
            onAddCategory = { newCategoryName = ""; showNewCategory = true },
        )

        if (showNewCategory) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showNewCategory = false },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        viewModel.addCategory(newCategoryName)
                        showNewCategory = false
                    }) { Text("Créer") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showNewCategory = false }) {
                        Text("Annuler")
                    }
                },
                title = { Text("Nouvelle catégorie") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Nom (ex. « À revoir »)") },
                        singleLine = true,
                    )
                },
            )
        }

        // ---- Onglet « Fichiers » : panneau vidéos locales et on s'arrête là
        if (currentKey == "LOCAL") {
            LocalFilesPanel(viewModel, onLocalFolderClick, state)
            return@Column
        }

        // ---- Onglet catégorie perso : les fichiers locaux rangés dedans
        if (currentKey.startsWith("CUSTOM:")) {
            val catName = currentKey.removePrefix("CUSTOM:")
            val uris = remember(catName, catTick) { viewModel.categoryItems(catName).toSet() }
            val picked = state.localFiles.filter { it.uri in uris || "folder:${it.parentUri}" in uris }
            CustomCategoryPanel(viewModel, catName, picked, onLocalFolderClick)
            return@Column
        }

        // Collection filters do not apply to the dedicated downloads area.
        if (!state.downloadsOnly) Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.FilterChip(
                selected = !state.onDeviceOnly,
                onClick = { viewModel.setOnDeviceOnly(false) },
                label = { Text("Tous", maxLines = 1, softWrap = false) },
            )
            androidx.compose.material3.FilterChip(
                selected = state.onDeviceOnly,
                onClick = {
                    viewModel.setOnDeviceOnly(true)
                },
                label = { Text("Sur l'appareil", maxLines = 1, softWrap = false) },
            )
            // §bibliotheque-locale-fusion : mêler les vidéos locales aux titres suivis
            androidx.compose.material3.FilterChip(
                selected = mergeLocal,
                onClick = { viewModel.setMergeLocal(!mergeLocal) },
                label = { Text("Fichiers locaux", maxLines = 1, softWrap = false) },
            )
            // Watchlist §29 : filtre par statut de suivi
            listOf(
                "WISHLIST" to "À regarder",
                "WATCHING" to "▶ En cours",
                "COMPLETED" to "Terminés",
                "DROPPED" to "Abandonnés",
            ).forEach { (st, label) ->
                androidx.compose.material3.FilterChip(
                    selected = state.filterStatus == st,
                    onClick = { viewModel.setFilterStatus(if (state.filterStatus == st) "ALL" else st) },
                    label = { Text(label, maxLines = 1, softWrap = false) },
                )
            }
            // §multi-sources (conversation 6) : d'où vient le contenu — on
            // n'affiche que les sources réellement présentes sur l'appareil.
            val availableSources = viewModel.presentSources()
            if (availableSources.isNotEmpty()) {
                androidx.compose.material3.FilterChip(
                    selected = state.sourceFilter == LibrarySource.ALL,
                    onClick = { viewModel.setSourceFilter(LibrarySource.ALL) },
                    label = { Text("Toutes sources", maxLines = 1, softWrap = false) },
                )
                availableSources.forEach { kind ->
                    androidx.compose.material3.FilterChip(
                        selected = state.sourceFilter == kind,
                        onClick = { viewModel.setSourceFilter(kind) },
                        label = { Text(LibrarySource.label(kind), maxLines = 1, softWrap = false) },
                    )
                }
            }
            // §multi-sources : état de visionnage réel (tout / en cours / terminé)
            androidx.compose.material3.FilterChip(
                selected = state.watchFilter == "WATCHING",
                onClick = {
                    viewModel.setWatchFilter(if (state.watchFilter == "WATCHING") "ALL" else "WATCHING")
                },
                label = { Text("◐ En cours", maxLines = 1, softWrap = false) },
            )
            androidx.compose.material3.FilterChip(
                selected = state.watchFilter == "COMPLETED",
                onClick = {
                    viewModel.setWatchFilter(if (state.watchFilter == "COMPLETED") "ALL" else "COMPLETED")
                },
                label = { Text("✓ Terminés", maxLines = 1, softWrap = false) },
            )
        }
        // §multi-sources (conversation 6) : on ne garde que ce qui vient de la
        // source choisie (mémoire interne, carte SD, téléchargements).
        val source = if (state.downloadsOnly) LibrarySource.ALL else state.sourceFilter
        val sourceLocal = when (source) {
            LibrarySource.ALL -> localCards
            LibrarySource.DOWNLOADS -> emptyList()
            else -> if (mergeLocal) viewModel.folderCards(state.localFiles.filter { it.storageKind == source }) else emptyList()
        }
        val sourceDownloads = if (source == LibrarySource.ALL || source == LibrarySource.DOWNLOADS) {
            state.downloadedGroups.map { it.toCard() }
        } else {
            emptyList()
        }
        // §telecharges-bibliotheque : en mode « Téléchargés », la grille ne montre
        // que les séries présentes sur l'appareil (fiches virtuelles incluses).
        // Le filtre « Sur l'appareil » les ajoute aux titres suivis.
        val shownItems = when {
            state.downloadsOnly -> sourceDownloads
            state.onDeviceOnly -> {
                val followed = state.items.map { it.id }.toSet()
                state.items + sourceLocal +
                    state.downloadedGroups
                        .filter {
                            it.key !in followed &&
                                (source == LibrarySource.ALL || source == LibrarySource.DOWNLOADS)
                        }
                        .map { it.toCard() }
            }
            else -> state.items + sourceLocal
        }.distinctBy { it.id }
        if (shownItems.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "« $currentLabel » est vide",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    if (state.downloadsOnly) "Vos téléchargements terminés apparaîtront ici, regroupés par titre."
                    else "Ajoutez des titres depuis une fiche (boutons « Ajouter à ma liste » / cœur) ou la bannière d'accueil.",
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
                // ---- §bibliotheque-sections (conversation 6) : « Reprendre la lecture »
                // puis « Récemment ajoutés », au-dessus de la grille, et seulement
                // dans la vue principale (pas en mode fichiers ni téléchargements).
                if (!state.localMode && !state.downloadsOnly && state.continueWatching.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Reprendre la lecture",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.continueWatching, key = { it.episodeId }) { card ->
                                ContinueCard(
                                    card = card,
                                    onClick = {
                                        // une fiche connue s'ouvre ; sinon on reprend le média
                                        if (card.mediaId.startsWith("local:")) onLocalFolderClick(card.mediaId.removePrefix("local:"))
                                        else if (card.mediaId.isNotBlank()) onMediaClick(card.mediaId)
                                    },
                                )
                            }
                        }
                    }
                }
                if (!state.localMode && !state.downloadsOnly && state.recentlyAdded.isNotEmpty() && state.items.size > 12) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Récemment ajoutés",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(state.recentlyAdded, key = { it.id }) { item ->
                                androidx.compose.foundation.layout.Box(Modifier.width(110.dp)) {
                                    MediaCard(item = item, onClick = { onMediaClick(item.id) })
                                }
                            }
                        }
                    }
                }
                items(shownItems, key = { it.id }) { item ->
                    MediaCard(
                        item = item,
                        onClick = {
                            when {
                                item.id.startsWith("local-folder:") ->
                                    onLocalFolderClick(item.id.removePrefix("local-folder:"))
                                // §telecharges-bibliotheque : un groupe téléchargé
                                // ouvre sa fiche (liste des épisodes sur l'appareil)
                                item.id.startsWith("downloaded:") ->
                                    onDownloadedFolderClick(item.id.removePrefix("downloaded:"))
                                else -> onMediaClick(item.id)
                            }
                        },
                        onLongClick = {
                            if (item.id.startsWith("local-folder:")) onLocalFolderClick(item.id.removePrefix("local-folder:"))
                            else if (item.id.startsWith("downloaded:")) onDownloadedFolderClick(item.id.removePrefix("downloaded:"))
                            else editMediaMeta = item
                        },
                    )
                }
            }
        }

        // ---- §métadonnées-éditées : appui long sur une carte de bibliothèque
        editMediaMeta?.let { mediaItem ->
            var editedTitle by androidx.compose.runtime.remember(mediaItem.id) {
                androidx.compose.runtime.mutableStateOf(mediaItem.title)
            }
            var editedCover by androidx.compose.runtime.remember(mediaItem.id) {
                androidx.compose.runtime.mutableStateOf(mediaItem.posterUrl ?: "")
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { editMediaMeta = null },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        viewModel.saveCustomMediaMeta(
                            mediaItem.id,
                            editedTitle.takeIf { it.isNotBlank() }?.takeIf { it != mediaItem.title },
                            editedCover.takeIf { it.isNotBlank() }?.takeIf { it != mediaItem.posterUrl },
                        )
                        editMediaMeta = null
                    }) { Text("Enregistrer") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { editMediaMeta = null }) {
                        Text("Annuler")
                    }
                },
                title = { Text("Titre et affiche perso") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            mediaItem.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = editedTitle, onValueChange = { editedTitle = it },
                            label = { Text("Titre affiché", maxLines = 1, softWrap = false) }, singleLine = true,
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = editedCover, onValueChange = { editedCover = it },
                            label = { Text("Affiche (URL ou content://)") }, singleLine = true,
                        )
                        Text(
                            "S’applique aussi aux vidéos téléchargées depuis l'app. " +
                                "Vide les champs et enregistre pour restaurer les informations de la source.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

/**
 * §bibliotheque-sections (conversation 6) — carte « Reprendre » : miniature 16:9,
 * barre de progression, temps restant et date relative. Même langage visuel que
 * les cartes d'historique de l'accueil (conversation 9).
 */
@Composable
private fun ContinueCard(
    card: dev.endlesssea.app.ui.library.ContinueCardUi,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(220.dp),
    ) {
        Column {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),
            ) {
                dev.endlesssea.app.SafeAsyncImage(
                    url = card.thumbUrl,
                    contentDescription = card.title,
                    modifier = Modifier.fillMaxSize(),
                    placeholderModifier = Modifier.fillMaxSize(),
                )
                // barre de progression en bas de la miniature
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .fillMaxWidth(card.progress)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(0.dp)),
                    )
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    card.remainingLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    card.updatedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun LocalFilesPanel(viewModel: LibraryViewModel, onLocalFolderClick: (String) -> Unit, state: LibraryUiState) {
    LocalFolderLibrary(viewModel, state, onLocalFolderClick)
}

@Composable
private fun CustomCategoryPanel(viewModel: LibraryViewModel, name: String, files: List<LocalVideoUi>, onFolder: (String) -> Unit) {
    LocalFolderLibrary(viewModel, viewModel.uiState.collectAsState().value, onFolder,
        files = files, categoryName = name)
}

/** §lecture-locale : prépare la file (tout le lot affiché) puis ouvre le lecteur. */
internal fun playLocal(
    context: android.content.Context,
    all: List<LocalVideoUi>,
    video: LocalVideoUi,
) {
    val queue = dev.endlesssea.app.local.localPlaybackQueue(all, video)
    fun link(f: LocalVideoUi) = dev.endlesssea.extensions.api.model.VideoLink(
        url = f.uri,
        streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
        quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN,
        server = "Fichier local",
    )
    dev.endlesssea.app.ui.player.PlayerLaunchStore.resolver = null
    dev.endlesssea.app.ui.player.PlayerLaunchStore.setQueue(
        queue.map {
            dev.endlesssea.app.ui.player.PlayerLaunchStore.QueueItem(
                title = it.displayName, episodeId = it.uri, links = listOf(link(it)),
                thumbnailUrl = it.customCoverUri ?: it.uri,
                durationMs = it.durationMs ?: 0L, episodeNumber = it.matchedNumber ?: it.episodeNumber?.toFloat(),
                season = it.matchedSeason ?: dev.endlesssea.app.local.LocalVideos.episodeSeason(it.name),
                mediaId = it.parentUri.takeIf { parent -> parent.isNotBlank() }?.let(dev.endlesssea.app.local.LocalMediaIds::series), downloaded = true,
                markers = dev.endlesssea.app.ui.player.PlayerLaunchStore.SkipMarkers(it.introStartSec, it.introEndSec, it.outroStartSec),
            )
        },
        queue.indexOfFirst { it.uri == video.uri },
    )
    dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
        title = video.displayName,
        mediaId = video.parentUri.takeIf { it.isNotBlank() }?.let(dev.endlesssea.app.local.LocalMediaIds::series), episodeId = video.uri,
        links = listOf(link(video)),
        startIndex = 0,
        markers = dev.endlesssea.app.ui.player.PlayerLaunchStore.SkipMarkers(
            introStartSec = video.introStartSec,
            introEndSec = video.introEndSec,
            outroStartSec = video.outroStartSec,
        ),
    )
    context.startActivity(
        android.content.Intent(context, dev.endlesssea.app.ui.player.PlayerActivity::class.java),
    )
}
