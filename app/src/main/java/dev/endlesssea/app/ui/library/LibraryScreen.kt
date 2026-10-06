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
fun LibraryScreen(
    onMediaClick: (String) -> Unit,
    viewModel: LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    var tab by remember { mutableIntStateOf(0) }
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
    val tabs = remember(customCats) { LIBRARY_TABS + customCats.map { it to "CUSTOM:$it" } }
    // §bibliotheque-locale-fusion : les fichiers locaux apparaissent dans la grille
    // normale (même affichage que les titres suivis), activable d'un chip.
    val mergeLocal by viewModel.mergeLocal.collectAsState()
    androidx.compose.runtime.LaunchedEffect(mergeLocal) { if (mergeLocal) viewModel.scanLocal() }
    val localCards = remember(state.localFiles, mergeLocal) {
        if (!mergeLocal) emptyList() else state.localFiles.map { f ->
            SearchItemUi(
                id = "local:" + f.uri,
                title = f.displayName,
                // §vignettes-locales : à défaut d'affiche, la vidéo elle-même
                posterUrl = f.customCoverUri ?: f.uri,
                subtitle = "Fichier local",
            )
        }
    }
    if (tab >= tabs.size) tab = 0
    /** §métadonnées-éditées : fiche en cours d'édition (appui long dans la grille). */
    var editMediaMeta by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<SearchItemUi?>(null)
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        // §onglets-scrollés : 7 catégories sans cassure verticale (bug « Fa vo ris »)
        // §onglets-compacts (capture utilisateur « Fa vo ris ») : des pastilles
        // qui défilent horizontalement, texte sur UNE ligne, jamais cassé
        // lettre par lettre comme le faisait le Tab à largeur contrainte.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { i, (label, key) ->
                androidx.compose.material3.FilterChip(
                    selected = tab == i,
                    onClick = {
                        tab = i
                        when {
                            key == "LOCAL" -> viewModel.setLocalMode(true)
                            // la catégorie perso contient des fichiers locaux : on scanne
                            key.startsWith("CUSTOM:") -> viewModel.setLocalMode(true)
                            else -> { viewModel.setLocalMode(false); viewModel.onCategory(key) }
                        }
                    },
                    label = {
                        Text(
                            label,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                        )
                    },
                )
            }
            androidx.compose.material3.AssistChip(
                onClick = { newCategoryName = ""; showNewCategory = true },
                label = { Text("＋ Catégorie", maxLines = 1, softWrap = false) },
            )
        }

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
        if (tabs[tab].second == "LOCAL") {
            LocalFilesPanel(viewModel, state)
            return@Column
        }

        // ---- Onglet catégorie perso : les fichiers locaux rangés dedans
        val currentKey = tabs[tab].second
        if (currentKey.startsWith("CUSTOM:")) {
            val catName = currentKey.removePrefix("CUSTOM:")
            val uris = remember(catName, catTick) { viewModel.categoryItems(catName).toSet() }
            val picked = state.localFiles.filter { it.uri in uris }
            CustomCategoryPanel(viewModel, catName, picked)
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
                label = { Text("Tous", maxLines = 1, softWrap = false) },
            )
            androidx.compose.material3.FilterChip(
                selected = state.onDeviceOnly,
                onClick = { viewModel.setOnDeviceOnly(true) },
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
        }
        val shownItems = state.items + localCards
        if (shownItems.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "« ${tabs[tab].first} » est vide",
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
                items(shownItems, key = { it.id }) { item ->
                    MediaCard(
                        item = item,
                        onClick = {
                            // les cartes locales lancent directement la lecture
                            if (item.id.startsWith("local:")) {
                                val uri = item.id.removePrefix("local:")
                                val video = state.localFiles.firstOrNull { it.uri == uri }
                                if (video != null) playLocal(context, state.localFiles, video)
                            } else {
                                onMediaClick(item.id)
                            }
                        },
                        onLongClick = { editMediaMeta = item },
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
    /** §catégories-perso : fichier qu'on range dans une catégorie. */
    var addToCat by remember { androidx.compose.runtime.mutableStateOf<LocalVideoUi?>(null) }
    val cats by viewModel.customCategories.collectAsState()
    val catsTick by viewModel.categoryItemsTick.collectAsState()
    // §scan-par-dossier (façon Kotatsu / Aniyomi) : tout ce qui est dans un même
    // dossier forme UNE entrée ; on n'ouvre la liste des fichiers que si on entre
    // dans le dossier.
    var openFolder by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val folders = remember(state.localFiles) {
        state.localFiles.groupBy { it.parentUri }.toList().sortedBy { it.second.first().folderName.lowercase() }
    }
    // §affichage-dossiers : « par dossier » (défaut) ou « tous les fichiers »
    val folderView by viewModel.localFolderView.collectAsState()
    val hiddenOn by viewModel.showHiddenFiles.collectAsState()
    val visibleFiles = remember(state.localFiles, openFolder, folderView) {
        when {
            folderView == "flat" -> state.localFiles
            openFolder == null -> emptyList()
            else -> state.localFiles.filter { it.parentUri == openFolder }
        }
    }

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
                label = { Text("Ajouter un dossier", maxLines = 1, softWrap = false) },
            )
            viewModel.dirs.collectAsState().value.forEach { dir ->
                androidx.compose.material3.AssistChip(
                    onClick = { viewModel.removeLocalDir(dir) },
                    label = {
                        Text("" + android.net.Uri.parse(dir).path
                            ?.substringAfterLast(':')?.substringAfterLast('/') ?: dir)
                    },
                )
            }
            androidx.compose.material3.AssistChip(
                onClick = { viewModel.scanLocal() },
                label = { Text("Scanner", maxLines = 1, softWrap = false) },
            )
        }
        Text(
            "Lecture hors-ligne des vidéos trouvées (y compris celles NON téléchargées via l'app). " +
                "Touche une vidéo pour la lire, bouton pour renommer/choisir l'affiche.",
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
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (state.localFiles.isEmpty() && viewModel.dirs.collectAsState().value.isEmpty())
                        "Aucun dossier — ajoute-en un avec « Ajouter un dossier » (plusieurs possibles)."
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
                // §affichage-dossiers : barre d'options du panneau local
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = folderView != "flat",
                            onClick = { viewModel.setLocalFolderView("folders") },
                            label = { Text("Par dossier", maxLines = 1, softWrap = false) },
                        )
                        androidx.compose.material3.FilterChip(
                            selected = folderView == "flat",
                            onClick = { viewModel.setLocalFolderView("flat") },
                            label = { Text("Tous les fichiers", maxLines = 1, softWrap = false) },
                        )
                        androidx.compose.material3.FilterChip(
                            selected = hiddenOn,
                            onClick = { viewModel.setShowHiddenFiles(!hiddenOn) },
                            label = { Text("Fichiers cachés", maxLines = 1, softWrap = false) },
                        )
                    }
                }
                // ---- Niveau 1 : les dossiers (une carte par dossier)
                if (openFolder == null && folderView != "flat") {
                    items(folders, key = { it.first }) { (parent, files) ->
                        dev.endlesssea.app.ui.components.GlassCard(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { openFolder = parent },
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 12.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        files.first().folderName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${files.size} vidéo(s) · " +
                                            dev.endlesssea.app.local.LocalVideos.humanSize(
                                                files.sumOf { it.sizeBytes },
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                // ---- Niveau 2 : les fichiers du dossier ouvert
                if (openFolder != null) {
                    item {
                        androidx.compose.material3.AssistChip(
                            onClick = { openFolder = null },
                            label = {
                                Text("‹ " + (visibleFiles.firstOrNull()?.folderName ?: "Tous les dossiers"))
                            },
                        )
                    }
                }
                items(visibleFiles, key = { it.uri }) { video ->
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
                            } else if (true) {
                                // §vignettes-locales : image extraite de la vidéo
                                coil.compose.AsyncImage(
                                    model = coil.request.ImageRequest.Builder(
                                        androidx.compose.ui.platform.LocalContext.current,
                                    ).data(video.uri).crossfade(true).build(),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.padding(end = 10.dp).width(56.dp).height(84.dp)
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                                )
                            } else {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Filled.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                dev.endlesssea.app.ui.components.ExpandableText(
                                    text = video.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    dialogTitle = "Fichier local",
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
                            androidx.compose.material3.TextButton(onClick = { addToCat = video }) {
                                Text("＋")
                            }
                            androidx.compose.material3.IconButton(onClick = { editMeta = video }) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.material.icons.Icons.Filled.Edit,
                                    contentDescription = "Métadonnées",
                                )
                            }
                            androidx.compose.material3.Button(onClick = {
                                // §épisode-suivant : toutes les vidéos du dossier
                                // forment la file de lecture (précédent / suivant).
                                dev.endlesssea.app.ui.player.PlayerLaunchStore.resolver = null
                                dev.endlesssea.app.ui.player.PlayerLaunchStore.setQueue(
                                    visibleFiles.map { f ->
                                        dev.endlesssea.app.ui.player.PlayerLaunchStore.QueueItem(
                                            title = f.displayName,
                                            episodeId = f.uri,
                                            links = listOf(
                                                dev.endlesssea.extensions.api.model.VideoLink(
                                                    url = f.uri,
                                                    streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                                                    quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN,
                                                    server = "Fichier local",
                                                ),
                                            ),
                                        )
                                    },
                                    visibleFiles.indexOfFirst { it.uri == video.uri },
                                )
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
                                    markers = dev.endlesssea.app.ui.player.PlayerLaunchStore.SkipMarkers(
                                        introStartSec = video.introStartSec,
                                        introEndSec = video.introEndSec,
                                        outroStartSec = video.outroStartSec,
                                    ),
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

        // §catégories-perso : choix des catégories qui contiennent ce fichier
        addToCat?.let { video ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { addToCat = null },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { addToCat = null }) { Text("Fermer") }
                },
                title = { Text("Ranger dans une catégorie") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (cats.isEmpty()) {
                            Text(
                                "Aucune catégorie : crée-en une avec « ＋ Catégorie » " +
                                    "en haut de la bibliothèque.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        cats.forEach { c ->
                            val inside = remember(c, catsTick, video.uri) {
                                video.uri in viewModel.categoryItems(c)
                            }
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(
                                    checked = inside,
                                    onCheckedChange = { viewModel.toggleCategoryItem(c, video.uri) },
                                )
                                Text(c)
                            }
                        }
                    }
                },
            )
        }

        // Dialogue métadonnées §métadonnées-locales
        editMeta?.let { video ->
            var title by androidx.compose.runtime.remember(video.uri) {
                androidx.compose.runtime.mutableStateOf(video.customTitle ?: "")
            }
            var cover by androidx.compose.runtime.remember(video.uri) {
                androidx.compose.runtime.mutableStateOf(video.customCoverUri ?: "")
            }
            var introStart by androidx.compose.runtime.remember(video.uri) {
                androidx.compose.runtime.mutableStateOf(video.introStartSec?.toString() ?: "")
            }
            var introEnd by androidx.compose.runtime.remember(video.uri) {
                androidx.compose.runtime.mutableStateOf(video.introEndSec?.toString() ?: "")
            }
            var outroStart by androidx.compose.runtime.remember(video.uri) {
                androidx.compose.runtime.mutableStateOf(video.outroStartSec?.toString() ?: "")
            }
            var wholeFolder by androidx.compose.runtime.remember(video.uri) {
                androidx.compose.runtime.mutableStateOf(false)
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { editMeta = null },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        viewModel.saveLocalMeta(
                            video.uri,
                            title.takeIf { it.isNotBlank() },
                            cover.takeIf { it.isNotBlank() },
                            introStart.toIntOrNull(),
                            introEnd.toIntOrNull(),
                            outroStart.toIntOrNull(),
                        )
                        // §métadonnées-dossier : même affiche et mêmes marqueurs partout
                        if (wholeFolder) {
                            viewModel.saveFolderMeta(
                                video.parentUri,
                                cover.takeIf { it.isNotBlank() },
                                introStart.toIntOrNull(),
                                introEnd.toIntOrNull(),
                                outroStart.toIntOrNull(),
                            )
                        }
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
                            label = { Text("Titre affiché", maxLines = 1, softWrap = false) }, singleLine = true,
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = cover, onValueChange = { cover = it },
                            label = { Text("Affiche (URL ou content://)") }, singleLine = true,
                        )
                        // §marqueurs intro/outro (secondes)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedTextField(
                                value = introStart,
                                onValueChange = { v -> introStart = v.filter(Char::isDigit).take(5) },
                                label = { Text("Intro déb. (s)") }, singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            androidx.compose.material3.OutlinedTextField(
                                value = introEnd,
                                onValueChange = { v -> introEnd = v.filter(Char::isDigit).take(5) },
                                label = { Text("Intro fin (s)") }, singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        androidx.compose.material3.OutlinedTextField(
                            value = outroStart,
                            onValueChange = { v -> outroStart = v.filter(Char::isDigit).take(5) },
                            label = { Text("Générique de fin — début (s)") }, singleLine = true,
                        )
                        Text(
                            "Pendant la lecture, un bouton « Passer » apparaîtra dans ces plages. " +
                                "Laisse les champs vides pour ne rien afficher.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = wholeFolder,
                                onCheckedChange = { wholeFolder = it },
                            )
                            Text(
                                "Appliquer l'affiche et les marqueurs à tout le dossier",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
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

/** §catégories-perso : contenu d'une catégorie créée par l'utilisateur. */
@Composable
private fun CustomCategoryPanel(
    viewModel: LibraryViewModel,
    name: String,
    files: List<LocalVideoUi>,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = { viewModel.removeCategory(name) }) {
                Text("Supprimer")
            }
        }
        if (files.isEmpty()) {
            Text(
                "Catégorie vide — ouvre « Fichiers », puis « ＋ » sur une vidéo pour la ranger ici.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        androidx.compose.foundation.lazy.LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(files, key = { it.uri }) { video ->
                dev.endlesssea.app.ui.components.GlassCard(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (video.customCoverUri != null) {
                            dev.endlesssea.app.SafeAsyncImage(
                                url = video.customCoverUri,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 10.dp).width(48.dp).height(72.dp),
                                placeholderModifier = Modifier.padding(end = 10.dp).width(48.dp).height(72.dp),
                            )
                        } else {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Filled.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                        Text(video.displayName, maxLines = 2, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.toggleCategoryItem(name, video.uri) },
                        ) { Text("Retirer") }
                        androidx.compose.material3.Button(onClick = {
                            dev.endlesssea.app.ui.player.PlayerLaunchStore.resolver = null
                            dev.endlesssea.app.ui.player.PlayerLaunchStore.setQueue(
                                files.map { f ->
                                    dev.endlesssea.app.ui.player.PlayerLaunchStore.QueueItem(
                                        title = f.displayName,
                                        episodeId = f.uri,
                                        links = listOf(
                                            dev.endlesssea.extensions.api.model.VideoLink(
                                                url = f.uri,
                                                streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                                                quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN,
                                                server = "Fichier local",
                                            ),
                                        ),
                                    )
                                },
                                files.indexOfFirst { it.uri == video.uri },
                            )
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
                                markers = dev.endlesssea.app.ui.player.PlayerLaunchStore.SkipMarkers(
                                    introStartSec = video.introStartSec,
                                    introEndSec = video.introEndSec,
                                    outroStartSec = video.outroStartSec,
                                ),
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
}

/** §lecture-locale : prépare la file (tout le lot affiché) puis ouvre le lecteur. */
internal fun playLocal(
    context: android.content.Context,
    all: List<LocalVideoUi>,
    video: LocalVideoUi,
) {
    fun link(f: LocalVideoUi) = dev.endlesssea.extensions.api.model.VideoLink(
        url = f.uri,
        streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
        quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN,
        server = "Fichier local",
    )
    dev.endlesssea.app.ui.player.PlayerLaunchStore.resolver = null
    dev.endlesssea.app.ui.player.PlayerLaunchStore.setQueue(
        all.map {
            dev.endlesssea.app.ui.player.PlayerLaunchStore.QueueItem(
                title = it.displayName, episodeId = it.uri, links = listOf(link(it)),
            )
        },
        all.indexOfFirst { it.uri == video.uri },
    )
    dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
        title = video.displayName,
        mediaId = null, episodeId = video.uri,
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
