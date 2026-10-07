package dev.endlesssea.app.ui.details

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.endlesssea.app.ui.components.GlassCard
import dev.endlesssea.app.ui.player.PlayerActivity
import dev.endlesssea.extensions.api.model.AudioLang
import dev.endlesssea.extensions.api.model.Episode
import dev.endlesssea.extensions.api.model.StreamType
import dev.endlesssea.extensions.api.model.VideoLink

/** Libellé FR des sections de langue audio (VOSTFR / VF en tête). */
private fun audioSection(lang: AudioLang) = when (lang) {
    AudioLang.VOSTFR -> "VOSTFR"
    AudioLang.VF -> "VF"
    AudioLang.MULTI -> "MULTI"
    AudioLang.VO -> "VO (original)"
    AudioLang.OTHER -> "Autre"
}

/**
 * Fiche détaillée (design de référence : captures Anymex) — bandeau dégradé, affiche
 * arrondie 18dp, méta en pilules, boutons capsule 28dp, saisons en pilules,
 * épisodes avec vignette + badge EP + téléchargement par épisode, feuille
 * « Télécharger » groupée par langue audio (VOSTFR / VF / MULTI / VO).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailsScreen(
    mediaId: String,
    onBack: () -> Unit,
    onDownloadQueued: () -> Unit,
    /** §suivi (conversation 11) : ouverture de l'écran « Comptes & suivi ». */
    onOpenTrackers: () -> Unit = {},
    viewModel: DetailsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var downloadSheetEpisode by remember { mutableStateOf<Episode?>(null) }
    /** §fiche-serveurs : épisode dont le panneau « serveurs de lecture » est ouvert. */
    var playSheetEpisode by remember { mutableStateOf<Episode?>(null) }
    /** §telecharge-tout-sélect : dialogue de sélection d'épisodes (après le tri serveurs). */
    var batchSelectDialog by remember { mutableStateOf(false) }
    /** Priorité de serveurs choisie dans l'étape 1 du « Tout télécharger ». */
    var batchPriority by remember { mutableStateOf<List<String>?>(null) }
    // §glisser-serveurs : dialogue de choix/réordonnancement avant « Tout télécharger »
    var serverOrderDialog by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var synopsisExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    fun launchPlayer(): () -> Unit = { context.startActivity(Intent(context, PlayerActivity::class.java)) }

    val seasons = remember(state.episodes) {
        state.episodes.mapNotNull { it.season }.distinct().sorted()
    }
    val episodesShown = remember(state.episodes, selectedSeason) {
        if (selectedSeason == null) state.episodes
        else state.episodes.filter { it.season == selectedSeason }
            .ifEmpty { state.episodes }
    }

    // §film-et-serie : une même fiche peut porter un FILM et des saisons (cas
    // fréquent des animes : le long-métrage et la série partagent l'affiche).
    // On ne sépare pas les fiches — on sépare seulement les rangées, et un film
    // n'affiche jamais « Saison 1 · Épisode 1 ».
    val mediaIsMovie = state.details?.type == dev.endlesssea.extensions.api.model.MediaType.MOVIE
    val movieEpisodes = remember(episodesShown, mediaIsMovie) {
        episodesShown.filter { ep ->
            val alone = episodesShown.size == 1
            val noSeason = ep.season == null || ep.season == 0
            mediaIsMovie && (alone || noSeason) ||
                (ep.title?.contains("film", ignoreCase = true) == true) ||
                (ep.title?.contains("movie", ignoreCase = true) == true)
        }
    }
    val serieEpisodes = remember(episodesShown, movieEpisodes) {
        episodesShown.filterNot { it in movieEpisodes }
    }

    // ---- §use-poster-color : halo émotionnel teinté par l'affiche (Réglages → Thème)
    val usePosterColor by viewModel.usePosterColor.collectAsState()
    var posterGlowExtract by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(state.details?.posterUrl, usePosterColor) {
        posterGlowExtract = if (usePosterColor) extractPosterColor(context, state.details?.posterUrl) else null
    }
    val posterGlow = posterGlowExtract ?: MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {
        // ---- §fiche-immersive : toile de fond émotionnelle dédiée à la fiche —
        // affiche floutée + halos d'accent + fondu vers le fond. L'app n'affiche ici
        // AUCUNE barre : l'écran détail est un environnement indépendant plein écran.
        run {
            val bg = MaterialTheme.colorScheme.background
            val blurOk = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(410.dp),
            ) {
                (state.details?.bannerUrl ?: state.details?.posterUrl)?.let { img ->
                    AsyncImage(
                        model = img,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .then(if (blurOk) Modifier.blur(30.dp) else Modifier)
                            .graphicsLayer(alpha = 0.88f),
                        contentScale = ContentScale.Crop,
                    )
                }
                // halos accent (« bloom »)
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(340.dp)) {
                    drawCircle(
                        posterGlow.copy(alpha = 0.30f),
                        radius = size.width * 0.34f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.30f),
                    )
                    drawCircle(
                        posterGlow.copy(alpha = 0.16f),
                        radius = size.width * 0.30f,
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.72f),
                    )
                }
                // fondu vers le fond d'écran
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(410.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    bg.copy(alpha = 0.18f),
                                    bg.copy(alpha = 0.70f),
                                    bg.copy(alpha = 0.97f),
                                    bg,
                                ),
                            ),
                        ),
                )
            }
        }
        // §appui-long : confirmation de suppression d'un téléchargement
        var deleteCandidate by remember {
            androidx.compose.runtime.mutableStateOf<dev.endlesssea.app.ui.details.DeviceFileUi?>(null)
        }
        deleteCandidate?.let { f ->
            AlertDialog(
                onDismissRequest = { deleteCandidate = null },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteDeviceFile(f)
                        deleteCandidate = null
                    }) { Text("Supprimer") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteCandidate = null }) { Text("Annuler") }
                },
                title = { Text("Supprimer ce téléchargement ?") },
                text = { Text(f.label) },
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- §fiche-immersive : rangée flottante (retour + actions affiche) —
            // l'app n'affiche AUCUNE barre sur la fiche ; elle gère ses propres boutons.
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ImmersiveCircleButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Retour", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    (state.details?.posterUrl ?: state.details?.bannerUrl)?.let { img ->
                        val title = state.details?.title ?: "EndlessSea"
                        ImmersiveCircleButton(onClick = { sharePoster(context, title, img) }) {
                            Icon(Icons.Filled.Share, "Partager l'affiche", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    state.details?.url?.takeIf { it.startsWith("http") }?.let { url ->
                        ImmersiveCircleButton(onClick = { openExternal(context, url) }) {
                            Icon(Icons.Filled.Public, "Ouvrir dans le navigateur", tint = Color.White)
                        }
                    }
                }
            }

            // ---- Affiche + métadonnées (façon capture : titre à droite de l'affiche,
            // pilules de type/année/épisodes, pas de vide noir)
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box {
                        // halo de couleur derrière l'affiche (« bloom »)
                        androidx.compose.foundation.Canvas(
                            Modifier
                                .size(width = 138.dp, height = 198.dp),
                        ) {
                            drawCircle(
                                color = posterGlow.copy(alpha = 0.34f),
                                radius = size.minDimension * 0.52f,
                                center = center,
                            )
                        }
                        GlassCard(cornerRadius = 18.dp, contentPadding = PaddingValues(0.dp)) {
                            AsyncImage(
                                model = state.details?.posterUrl,
                                contentDescription = state.details?.title,
                                modifier = Modifier.size(width = 118.dp, height = 176.dp),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f).padding(top = 2.dp)) {
                        dev.endlesssea.app.ui.components.ExpandableText(
                            text = state.details?.title ?: "Chargement de la fiche…",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 3,
                            dialogTitle = "Titre complet",
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOfNotNull(
                                state.details?.rating?.let { "%.1f".format(it) },
                                state.details?.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() },
                                state.details?.year?.toString(),
                                state.details?.episodeCount?.let { "$it ép." }
                                    ?: if (state.episodes.isNotEmpty()) "${state.episodes.size} ép." else null,
                            ).forEach { MetaPill(it) }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Statut de suivi rapide (lecture bibliothèque)
                        if (state.inLibrary) {
                            MetaPill("${dev.endlesssea.app.ui.details.watchStatusLabel(state.libraryStatus)}")
                        }
                    }
                }
            }

            // ---- §progression-immersive : « Watching progress » à ligne ondulée
            run {
                val totalEp = state.details?.episodeCount ?: state.episodes.size
                if (totalEp > 0) {
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            cornerRadius = 20.dp,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            val watched by viewModel.watchedCount.collectAsState()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Progression de visionnage",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "$watched / $totalEp",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            WavyProgress(
                                progress = (watched.toFloat() / totalEp.coerceAtLeast(1)).coerceIn(0f, 1f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp)
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.error != null && state.details == null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(":(", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(state.error ?: "Erreur", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.load() }) { Text("Réessayer") }
                    }
                }
            } else {
                // ---- Actions capsule
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Ajouter / retirer de la liste (pilule accent)
                        Surface(
                            onClick = { viewModel.toggleLibrary(state.details?.type?.name ?: "ANIME") },
                            shape = RoundedCornerShape(28.dp),
                            color = if (state.inLibrary) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (state.inLibrary) Icons.Filled.Check else Icons.Filled.Add,
                                    null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.inLibrary) "Dans ma liste" else "Ajouter à ma liste",
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                if (state.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                "Favori", tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // ---- Menu ⋮ : navigateur / partager / télécharger l'affiche
                        Box {
                            var moreMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { moreMenu = true }) {
                                Icon(Icons.Filled.MoreVert, "Plus d'actions")
                            }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                state.details?.url?.takeIf { it.startsWith("http") }?.let { url ->
                                    DropdownMenuItem(
                                        text = { Text("Ouvrir dans le navigateur") },
                                        onClick = { moreMenu = false; openExternal(context, url) },
                                    )
                                }
                                (state.details?.posterUrl ?: state.details?.bannerUrl)?.let { img ->
                                    val title = state.details?.title ?: "EndlessSea"
                                    DropdownMenuItem(
                                        text = { Text("Partager l'affiche") },
                                        onClick = { moreMenu = false; sharePoster(context, title, img) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Télécharger l'affiche") },
                                        onClick = { moreMenu = false; downloadPoster(context, title, img) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- Statut watchlist §29 (quand le titre est en bibliothèque)
                if (state.inLibrary) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                            Text(
                                "Statut de suivi",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf("WISHLIST", "WATCHING", "COMPLETED", "DROPPED").forEach { st ->
                                    FilterChip(
                                        selected = state.libraryStatus == st,
                                        onClick = { viewModel.setLibraryStatus(st) },
                                        label = { Text(dev.endlesssea.app.ui.details.watchStatusLabel(st)) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- Bouton « Continuer » pleine largeur (reprise) + Lire / Tout télécharger
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        state.resumeLabel?.let { label ->
                            Button(
                                onClick = { viewModel.playResume(onReady = launchPlayer()) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(label)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    state.episodes.firstOrNull()?.let { viewModel.playEpisode(it, onReady = launchPlayer()) }
                                },
                                modifier = Modifier.weight(1.4f),
                            ) {
                                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Lire")
                            }
                        if (state.episodes.size > 1) {
                            OutlinedButton(
                                onClick = { serverOrderDialog = true },
                                enabled = !state.batchRunning,
                                modifier = Modifier.weight(1.4f),
                            ) {
                                if (state.batchRunning) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                } else {
                                    Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                }
                                Text("Tout télécharger")
                            }
                        }
                        }
                    }
                }

                // ---- Synopsis repliant
                state.details?.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text("Synopsis", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                synopsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (synopsisExpanded) 40 else 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { synopsisExpanded = !synopsisExpanded },
                            )
                            Row(
                                Modifier.clickable { synopsisExpanded = !synopsisExpanded }.padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (synopsisExpanded) "Réduire" else "Lire la suite",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    if (synopsisExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                // ---- Genres en pilules
                if (!state.details?.genres.isNullOrEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.details?.genres ?: emptyList()) { genre -> MetaPill(genre, accent = true) }
                        }
                    }
                }

                // ---- Studios + bande-annonce + personnages (si fournis par la source)
                val studios = state.details?.studios?.filter { it.isNotBlank() } ?: emptyList()
                if (studios.isNotEmpty() || state.details?.trailerUrl != null) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (studios.isNotEmpty()) MetaPill("${studios.take(3).joinToString(", ")}")
                            state.details?.trailerUrl?.let { trailer ->
                                Surface(
                                    onClick = { context.startActivity(android.content.Intent(context, TrailerActivity::class.java).putExtra("url", trailer)) },
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Text(
                                        "▶ Bande-annonce",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                val characters = state.details?.characters ?: emptyList()
                if (characters.isNotEmpty()) {
                    item {
                        Text(
                            "Personnages & doubleurs",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(characters) { credit -> CharacterCard(credit) }
                        }
                    }
                }

                // ---- §suivi (conversation 11) : rattachement + progression
                item { TrackerCard(viewModel = viewModel, onOpenTrackers = onOpenTrackers) }

                // ---- Saisons en pilules + épisodes
                if (state.episodes.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                when {
                                    serieEpisodes.isEmpty() && movieEpisodes.isNotEmpty() -> "Film"
                                    movieEpisodes.isNotEmpty() -> "Film & épisodes"
                                    else -> "Épisodes"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${episodesShown.size}/${state.episodes.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (seasons.size > 1) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    SeasonPill("Toutes", selectedSeason == null) { selectedSeason = null }
                                }
                                items(seasons) { s ->
                                    SeasonPill("Saison $s", selectedSeason == s) { selectedSeason = s }
                                }
                            }
                        }
                    }
                    if (movieEpisodes.isNotEmpty()) {
                        item {
                            Text(
                                "Film",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        items(movieEpisodes, key = { "movie-" + it.id }) { episode ->
                            EpisodeRowAnymex(
                                episode = episode,
                                loading = state.linksLoadingEpisode == episode.id,
                                onPlay = { playSheetEpisode = episode },
                                onDownload = { downloadSheetEpisode = episode },
                                isMovie = true,
                                mediaTitle = state.details?.title,
                            )
                        }
                    }
                    if (serieEpisodes.isNotEmpty() && movieEpisodes.isNotEmpty()) {
                        item {
                            Text(
                                "Série",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(serieEpisodes, key = { it.id }) { episode ->
                        EpisodeRowAnymex(
                            episode = episode,
                            loading = state.linksLoadingEpisode == episode.id,
                            onPlay = { playSheetEpisode = episode },
                            onDownload = { downloadSheetEpisode = episode },
                        )
                    }
                }

                // ---- §hors-ligne : « Sur l'appareil » — fichiers téléchargés de cette fiche
                if (state.deviceFiles.isNotEmpty()) {
                    item {
                        Text(
                            "Sur l'appareil",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    items(state.deviceFiles, key = { "dev_" + it.id }) { f ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            cornerRadius = 14.dp,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp, vertical = 10.dp,
                            ),
                        ) {
                            Row(
                                Modifier.combinedClickable(
                                    onClick = { viewModel.playDeviceFile(f, launchPlayer()) },
                                    // §appui-long : supprimer le téléchargement
                                    onLongClick = { deleteCandidate = f },
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Download, null)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    dev.endlesssea.app.ui.components.ExpandableText(
                                        text = f.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        dialogTitle = "Fichier sur l'appareil",
                                    )
                                    // §emplacement : où le fichier a été rangé
                                    Text(
                                        f.location.ifBlank { "Stockage de l'appareil" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                                androidx.compose.material3.Button(onClick = {
                                    viewModel.playDeviceFile(f, launchPlayer())
                                }) { Text("Lire") }
                            }
                        }
                    }
                }
                if (state.episodes.isEmpty() && state.details != null) {
                    item {
                        Text(
                            "Cette source ne liste aucun épisode — ouverture du lien direct.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                state.error?.let { err ->
                    item {
                        Text(
                            err, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // ---- Feuille « Télécharger » — groupée par langue audio, flux exclus
    // ---------------- Dialogue serveurs + priorité (glisser-déposer) §glisser-serveurs
    if (serverOrderDialog) {
        val storedOrder by viewModel.serverOrder.collectAsState()
        val detected = remember(state.linksByEpisode) {
            state.linksByEpisode.values.flatten()
                .mapNotNull { it.server?.takeIf { s -> s.isNotBlank() } }.distinct()
        }
        var ordered by remember(storedOrder, detected) {
            mutableStateOf(storedOrder.filter { it in detected } + detected.filter { it !in storedOrder })
        }
        if (ordered.isEmpty()) ordered = detected.ifEmpty { storedOrder }
        var inactive by remember { mutableStateOf(setOf<String>()) }
        AlertDialog(
            onDismissRequest = { serverOrderDialog = false },
            confirmButton = {
                Button(onClick = {
                    val active = ordered.filter { it !in inactive }
                    viewModel.saveServerOrder(ordered)
                    // §telecharge-tout-sélect : étape 2 — choix des épisodes
                    batchPriority = active
                    batchSelectDialog = true
                    serverOrderDialog = false
                }) { Text("Choisir les épisodes") }
            },
            dismissButton = { TextButton(onClick = { serverOrderDialog = false }) { Text("Annuler") } },
            title = { Text("Serveurs & priorité") },
            text = {
                Column {
                    Text(
                        "Maintiens la poignée ≡ et glisse pour classer : le serveur le plus haut " +
                            "est essayé en premier pour chaque épisode. Désactive ceux à ignorer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (ordered.isEmpty()) {
                        Text(
                            "Aucun serveur détecté pour l'instant — les liens seront résolus " +
                                "automatiquement avec l'ordre mémorisé.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        dev.endlesssea.app.ui.components.ReorderableColumn(
                            items = ordered,
                            key = { it },
                            onMove = { from, to ->
                                ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
                            },
                        ) { server, index, dragging, handle ->
                            dev.endlesssea.app.ui.components.ServerPriorityRow(
                                rank = index + 1,
                                server = server,
                                active = server !in inactive,
                                dragging = dragging,
                                onToggle = { on ->
                                    inactive = if (on) inactive - server else inactive + server
                                },
                                handleModifier = handle,
                            )
                        }
                    }
                    Text(
                        "L'ordre est mémorisé et réutilisé pour tous les téléchargements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
        )
    }

    // ---- §fiche-serveurs : au clic sur un épisode, choix du serveur avant lecture
    // §reprise-fiche : proposition automatique de reprendre où on s'était arrêté
    val resumePromptOn by viewModel.resumePrompt.collectAsState()
    var resumeAsked by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showResumeDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(state.resumeLabel, resumePromptOn) {
        if (resumePromptOn && !resumeAsked && state.resumeLabel != null) {
            resumeAsked = true
            showResumeDialog = true
        }
    }
    if (showResumeDialog && state.resumeLabel != null) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            confirmButton = {
                Button(onClick = {
                    showResumeDialog = false
                    viewModel.playResume(onReady = launchPlayer())
                }) { Text("Reprendre") }
            },
            dismissButton = {
                TextButton(onClick = { showResumeDialog = false }) { Text("Plus tard") }
            },
            title = { Text("Reprendre la lecture ?") },
            text = { Text(state.resumeLabel ?: "") },
        )
    }

    playSheetEpisode?.let { episode ->
        val links = state.linksByEpisode[episode.id] ?: emptyList()
        // §serveurs-visibles : la recherche démarre à l'ouverture de la feuille,
        // et la liste se remplit au fil de l'eau (serveur par serveur).
        LaunchedEffect(episode.id) { viewModel.loadLinks(episode) }
        ModalBottomSheet(onDismissRequest = { playSheetEpisode = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    "Lire — ${episode.title ?: "Épisode ${episode.number.toInt()}"}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (links.isEmpty()) {
                    val searching = state.linksLoadingEpisode == episode.id
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (searching) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Recherche des serveurs…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // §serveurs-bloques : plus de roue infinie — message clair + relance
                            Text(
                                state.message ?: "Aucun serveur trouvé pour cet épisode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            androidx.compose.material3.Button(onClick = { viewModel.loadLinks(episode) }) {
                                Text("Réessayer")
                            }
                        }
                    }
                } else {
                    // Ordre des serveurs : priorité glissée dans les réglages, inconnus à la fin (alpha)
                    val priority = viewModel.serverPriority()
                    fun serverRank(name: String): Int {
                        val i = priority.indexOfFirst { it.equals(name, true) }
                        return if (i >= 0) i else Int.MAX_VALUE
                    }
                    val grouped = links.groupBy { it.server.ifBlank { "Source" } }.toList()
                        .sortedWith(compareBy<Pair<String, List<dev.endlesssea.extensions.api.model.VideoLink>>> { serverRank(it.first) }
                            .thenBy { it.first.lowercase() })
                    val stillLoading = state.linksLoadingEpisode == episode.id
                    Text(
                        "${grouped.size} serveur(s) — ${links.size} lien(s)" +
                            if (stillLoading) " · recherche en cours…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    grouped.forEach { (server, srvLinks) ->
                        val best = srvLinks.maxByOrNull { it.quality.pixels } ?: srvLinks.first()
                        GlassCard(
                            cornerRadius = 14.dp,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    val startIndex = links.indexOf(best).coerceAtLeast(0)
                                    playSheetEpisode = null
                                    viewModel.playEpisode(episode, startIndex = startIndex, onReady = launchPlayer())
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(server, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        listOfNotNull(
                                            best.quality.label,
                                            best.audioLang.name.takeIf { l -> l != "OTHER" },
                                            "${srvLinks.size} qualité(s)",
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("▶", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        LaunchedEffect(episode.id) {
            if (state.linksByEpisode[episode.id] == null) viewModel.loadLinks(episode)
        }
    }

    // ---- §telecharge-tout-sélect : choix des épisodes avant « Tout télécharger »
    if (batchSelectDialog) {
        val selected = remember { mutableStateMapOf<String, Boolean>().apply {
            state.episodes.forEach { put(it.id, true) }
        } }
        AlertDialog(
            onDismissRequest = { batchSelectDialog = false },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    val picked = state.episodes.filter { selected[it.id] == true }
                    batchSelectDialog = false
                    if (picked.isNotEmpty()) {
                        val sp = batchPriority
                        if (sp != null) viewModel.enqueueAll(picked, serverPriority = sp)
                        else viewModel.enqueueAll(picked)
                    }
                    batchPriority = null
                }) { Text("Télécharger (${selected.count { it.value }})") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        state.episodes.forEach { selected[it.id] = true }
                    }) { Text("Tous") }
                    TextButton(onClick = {
                        state.episodes.forEach { selected[it.id] = false }
                    }) { Text("Aucun") }
                    TextButton(onClick = { batchSelectDialog = false }) { Text("Annuler") }
                }
            },
            title = { Text("Tout télécharger — épisodes") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                ) {
                    // §saisons : les épisodes sont groupés par saison, avec une
                    // case « toute la saison » et les serveurs déjà trouvés.
                    val bySeason = state.episodes.groupBy { it.season ?: 1 }.toSortedMap()
                    bySeason.forEach { (season, eps) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Saison $season (${eps.size})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                val allOn = eps.all { selected[it.id] == true }
                                eps.forEach { selected[it.id] = !allOn }
                            }) { Text("Tout") }
                        }
                        eps.forEach { ep ->
                        val epLinks = state.linksByEpisode[ep.id].orEmpty()
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected[ep.id] = selected[ep.id] != true
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = selected[ep.id] == true,
                                onCheckedChange = { selected[ep.id] = it == true },
                            )
                            Column(Modifier.weight(1f)) {
                                dev.endlesssea.app.ui.components.ExpandableText(
                                    text = ep.title ?: "Épisode ${ep.number.toInt()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    dialogTitle = "Épisode",
                                )
                                // §serveurs-disponibles : ce qui est déjà résolu
                                Text(
                                    when {
                                        state.linksLoadingEpisode == ep.id -> "Recherche des serveurs…"
                                        epLinks.isEmpty() -> "Serveurs non vérifiés — touchez la loupe"
                                        else -> epLinks.take(4).joinToString(" · ") { l -> l.server } +
                                            if (epLinks.size > 4) " +${epLinks.size - 4}" else ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { viewModel.loadLinks(ep) }) {
                                Text("Serveurs")
                            }
                        }
                        }
                    }
                }
            },
        )
    }

    downloadSheetEpisode?.let { episode ->
        val links = state.linksByEpisode[episode.id] ?: emptyList()
        // §serveurs-visibles : la recherche démarre à l'ouverture de la feuille,
        // et la liste se remplit au fil de l'eau (serveur par serveur).
        LaunchedEffect(episode.id) { viewModel.loadLinks(episode) }
        ModalBottomSheet(onDismissRequest = { downloadSheetEpisode = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    "Télécharger — ${episode.title ?: "Épisode ${episode.number.toInt()}"}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (links.isEmpty()) {
                    val searching = state.linksLoadingEpisode == episode.id
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (searching) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Recherche des serveurs…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // §serveurs-bloques : plus de roue infinie — message clair + relance
                            Text(
                                state.message ?: "Aucun serveur trouvé pour cet épisode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            androidx.compose.material3.Button(onClick = { viewModel.loadLinks(episode) }) {
                                Text("Réessayer")
                            }
                        }
                    }
                } else {
                    val grouped = links.groupBy { it.audioLang }.toList()
                        .sortedBy { AudioLang.entries.indexOf(it.first) }
                    val downloadable = links.count {
                        it.streamType == StreamType.DIRECT_FILE || it.streamType == StreamType.HLS
                    }
                    if (downloadable == 0) {
                        Text(
                            "Cette source ne propose que du flux (lecture en ligne uniquement).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    grouped.forEach { (lang, langLinks) ->
                        Text(
                            audioSection(lang),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        langLinks.sortedByDescending { it.quality.pixels }.forEach { link ->
                            DownloadRow(
                                link = link,
                                onEnqueue = {
                                    viewModel.enqueue(episode, link)
                                    downloadSheetEpisode = null
                                    onDownloadQueued()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        LaunchedEffect(episode.id) {
            if (state.linksByEpisode[episode.id] == null) viewModel.loadLinks(episode)
        }
    }
}

/** Bouton rond semi-transparent façon « vitrail » (rangée flottante de la fiche). */
@Composable
private fun ImmersiveCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** §progression-immersive : barre de progression ondulée (réf. Anymex). */
@Composable
private fun WavyProgress(progress: Float, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f) return@Canvas
        val mid = h / 2f
        val amp = h * 0.24f
        val cycles = 6f
        fun wave(upto: Float): androidx.compose.ui.graphics.Path {
            val p = androidx.compose.ui.graphics.Path()
            p.moveTo(0f, mid)
            var x = 0f
            val limit = (w * upto.coerceIn(0f, 1f))
            while (x <= limit) {
                val y = mid + amp * kotlin.math.sin((x / w) * cycles * 2f * Math.PI.toFloat())
                p.lineTo(x, y)
                x += 6f
            }
            return p
        }
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 6f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawPath(wave(1f), color = primary.copy(alpha = 0.22f), style = stroke)
        if (progress > 0f) drawPath(wave(progress), color = primary, style = stroke)
    }
}

/** §use-poster-color : couleur moyenne d'une affiche (échantillonnage 48 px, disque réseau Coil). */
private suspend fun extractPosterColor(
    context: android.content.Context,
    url: String?,
): Color? {
    if (url.isNullOrBlank()) return null
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val req = coil.request.ImageRequest.Builder(context)
                .data(url)
                .size(48)
                .allowHardware(false)
                .build()
            val drawable = coil.ImageLoader(context).execute(req).drawable
            val bmp = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@runCatching null
            var r = 0L; var g = 0L; var b = 0L; var n = 0
            val step = (bmp.width / 16).coerceAtLeast(1)
            val stepY = (bmp.height / 16).coerceAtLeast(1)
            var x = 0
            while (x < bmp.width) {
                var y = 0
                while (y < bmp.height) {
                    val c = bmp.getPixel(x, y)
                    r += android.graphics.Color.red(c)
                    g += android.graphics.Color.green(c)
                    b += android.graphics.Color.blue(c)
                    n += 1
                    y += stepY
                }
                x += step
            }
            if (n == 0) null else Color(Math.min(255, (r / n).toInt()), Math.min(255, (g / n).toInt()), Math.min(255, (b / n).toInt()))
        }.getOrNull()
    }
}

/** Ouvre un lien externe (bande-annonce → lecteur/navigateur par défaut). */
/** Partage le lien de l'affiche (apps de la fiche de partage Android). */
private fun sharePoster(context: android.content.Context, title: String, url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, "$title\n$url")
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Partager l'affiche"))
}

/** Enregistre l'affiche via le gestionnaire de téléchargement système (Images/EndlessSea). */
private fun downloadPoster(context: android.content.Context, title: String, url: String) {
    runCatching {
        val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val clean = title.replace(Regex("[^\\p{L}0-9._-]+"), "_").take(60)
        val req = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle("Affiche — $title")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_PICTURES, "EndlessSea/$clean.jpg")
        dm.enqueue(req)
        android.widget.Toast.makeText(context, "Affiche enregistrée dans Android ▸ Images/EndlessSea", android.widget.Toast.LENGTH_LONG).show()
    }.onFailure {
        android.widget.Toast.makeText(context, "Téléchargement de l'affiche impossible", android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun openExternal(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}

@Composable
private fun CharacterCard(credit: dev.endlesssea.extensions.api.model.CharacterCredit) {
    GlassCard(cornerRadius = 16.dp, contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = credit.imageUrl,
                    contentDescription = credit.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    credit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val va = buildString {
                    credit.voiceActor?.let { append("VA : $it") }
                    credit.voiceActorLang?.let { if (isNotEmpty()) append(" ($it)") else append(it) }
                }
                if (va.isNotBlank()) {
                    Text(va, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (credit.role.isNotBlank()) {
                    Text(credit.role, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * §suivi (conversation 11) — bloc « Suivi » de la fiche : service rattaché,
 * progression, statut, et rattachement manuel par recherche (résultats
 * cliquables avec vignette + année). Sans compte connecté, le bloc propose
 * simplement d'ouvrir l'écran « Comptes & suivi ».
 */
@Composable
private fun TrackerCard(
    viewModel: dev.endlesssea.app.ui.details.DetailsViewModel,
    onOpenTrackers: () -> Unit,
) {
    var matchQuery by remember { mutableStateOf("") }
    var selectedMatch by remember { mutableStateOf<dev.endlesssea.app.tracking.TrackerSearchHit?>(null) }
    val link by viewModel.trackerLink.collectAsState()
    val services by viewModel.connectedServices.collectAsState()
    val search by viewModel.trackerSearch.collectAsState()
    val tick by viewModel.trackerTick.collectAsState()
    // tick force la relecture après une action (progression/statut/détachement)
    remember(tick) { link }

    selectedMatch?.let { hit ->
        AlertDialog(onDismissRequest = { selectedMatch = null },
            title = { Text("Confirmer le rattachement") },
            text = { Text("Lier cette fiche à ${hit.title}" + (hit.year?.let { " ($it)" } ?: "") + " ? Les épisodes seront associés par leur numéro.") },
            confirmButton = { TextButton(onClick = { search.service?.let { viewModel.linkTracker(it, hit) }; selectedMatch = null }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { selectedMatch = null }) { Text("Annuler") } })
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column {
            Text("Suivi", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            when {
                link != null -> {
                    val l = link!!
                    Text(
                        "${l.title.ifBlank { l.remoteId }} · ${l.progress}" +
                            (l.totalEpisodes.takeIf { it > 0 }?.let { " / $it" } ?: "") +
                            " épisode(s)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Service : " + l.service.lowercase().replaceFirstChar { it.uppercase() } +
                            (if (l.pendingSync) " · en attente de synchronisation" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.FilterChip(
                            selected = false,
                            onClick = { viewModel.markNextWatched() },
                            label = { Text("+1 épisode vu", maxLines = 1, softWrap = false) },
                        )
                        listOf("WATCHING" to "En cours", "COMPLETED" to "Terminé", "DROPPED" to "Abandonné")
                            .forEach { (key, label) ->
                                androidx.compose.material3.FilterChip(
                                    selected = l.status == key,
                                    onClick = { viewModel.setTrackerStatus(key) },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                )
                            }
                    }
                    TextButton(onClick = { viewModel.unlinkTracker() }) { Text("Ne plus suivre") }
                }
                services.isEmpty() -> {
                    Text(
                        "Connecte AniList, MyAnimeList ou Shikimori pour suivre tes épisodes " +
                            "(et TMDB pour les affiches et bandes-annonces).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Button(onClick = onOpenTrackers) {
                        Text("Ouvrir Comptes & suivi")
                    }
                }
                else -> {
                    Text(
                        "Rattache cette fiche à ton compte pour envoyer la progression " +
                            "(un épisode vu hors ligne partira plus tard).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        services.forEach { id ->
                            androidx.compose.material3.FilterChip(
                                selected = false,
                                onClick = { viewModel.searchTracker(id) },
                                label = {
                                    Text(
                                        "Chercher sur " + (dev.endlesssea.app.tracking.TrackerRegistry.LABELS[id] ?: id),
                                        maxLines = 1, softWrap = false,
                                    )
                                },
                            )
                        }
                    }
                    if (search.loading) {
                        Spacer(Modifier.height(8.dp))
                        Text("Recherche…", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(value = matchQuery, onValueChange = { matchQuery = it },
                        label = { Text("Titre à rechercher (vide : titre de la fiche)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    search.service?.let { service ->
                        TextButton(onClick = { viewModel.searchTracker(service, matchQuery) }) { Text("Rechercher") }
                    }
                    search.hits.forEach { hit ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { selectedMatch = hit }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            hit.posterUrl?.let { url ->
                                dev.endlesssea.app.SafeAsyncImage(
                                    url = url,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 10.dp).width(40.dp).height(58.dp),
                                    placeholderModifier = Modifier.padding(end = 10.dp).width(40.dp).height(58.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(hit.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Text(
                                    listOfNotNull(hit.year?.toString(), hit.totalEpisodes?.let { "$it ép." })
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("+", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaPill(text: String, accent: Boolean = false) {
    // §contraste-badges : texte « on… » sur son propre conteneur (le couple
    // primaryContainer/primary était illisible) + liseré léger pour détacher
    // la pastille du fond verre.
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (accent) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            (if (accent) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.28f),
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (accent) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SeasonPill(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, softWrap = false) },
        shape = RoundedCornerShape(28.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected,
            borderColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

/** Ligne de téléchargement : serveur × qualité, badge lecture seule pour les flux non supportés. */
@Composable
private fun DownloadRow(link: VideoLink, onEnqueue: () -> Unit) {
    val direct = link.streamType == StreamType.DIRECT_FILE || link.streamType == StreamType.HLS

    // §taille-avant-téléchargement : un HEAD (ou un GET Range de 0 octet si le
    // serveur refuse HEAD) donne Content-Length ; pour le HLS on additionne la
    // taille des premiers segments et on extrapole sur la durée annoncée.
    var sizeLabel by androidx.compose.runtime.remember(link.url) {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(link.url) {
        if (!direct) return@LaunchedEffect
        sizeLabel = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { probeSize(link) }.getOrNull()
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = direct, onClick = onEnqueue)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${link.server} · ${link.quality.label}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (direct) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val extras = buildList {
                sizeLabel?.let { add(it) }
                if (link.subtitles.isNotEmpty()) add("${link.subtitles.size} sous-titres")
                add(
                    when (link.streamType) {
                        StreamType.DIRECT_FILE -> "Fichier direct"
                        StreamType.HLS -> "Flux HLS — assemblé par segments"
                        StreamType.DASH -> "Flux DASH — lecture seule"
                        StreamType.EMBED -> "Lecteur externe — lecture seule"
                        StreamType.TORRENT -> "Torrent — lecture seule"
                    },
                )
            }.joinToString(" · ")
            Text(
                extras, style = MaterialTheme.typography.bodySmall,
                color = if (direct) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        if (direct) {
            Icon(Icons.Filled.Download, "Télécharger", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Ligne d'épisode style référence : vignette + badge EP + titre + icône téléchargement. */
@Composable
private fun EpisodeRowAnymex(
    episode: Episode,
    loading: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    /** §film-et-serie : un film n'a ni saison ni numéro d'épisode à afficher. */
    isMovie: Boolean = false,
    mediaTitle: String? = null,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 118.dp, height = 66.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onPlay),
            ) {
                if (episode.thumbnailUrl != null) {
                    AsyncImage(
                        model = episode.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                ) {
                    Text(
                        "EP ${episode.number.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onPlay)) {
                dev.endlesssea.app.ui.components.ExpandableText(
                    text = when {
                        isMovie -> episode.title?.takeIf { it.isNotBlank() } ?: mediaTitle ?: "Film"
                        else -> episode.title ?: "Épisode ${episode.number.toInt()}"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    // §textes-longs : 1 ligne — les longs titres ne remplissent plus
                    // l'écran verticalement ; tap = titre entier en dialogue.
                    maxLines = 1,
                    dialogTitle = "Titre de l'épisode",
                )
                val meta = buildList {
                    if (isMovie) add("Film") else episode.season?.let { add("Saison $it") }
                    episode.durationMs?.let { add("${it / 60_000} min") }
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (loading) {
                CircularProgressIndicator(Modifier.padding(10.dp).size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, "Télécharger l'épisode", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}


/**
 * §taille-avant-téléchargement — estime le poids d'un lien.
 *
 * Fichier direct : `Content-Length` de la réponse HEAD (repli sur un GET avec
 * `Range: bytes=0-0`, dont le `Content-Range` porte la taille totale).
 * HLS : somme des segments listés dans la playlist, mesurés sur les trois
 * premiers, extrapolée au nombre total de segments (préfixée « ≈ »).
 */
private suspend fun probeSize(link: dev.endlesssea.extensions.api.model.VideoLink): String? {
    val headers = link.headers
    fun fmt(bytes: Long, approx: Boolean): String {
        val mo = bytes / 1_048_576.0
        val txt = if (mo >= 1024) "%.2f Go".format(mo / 1024) else "%.0f Mo".format(mo)
        return if (approx) "≈ $txt" else txt
    }
    if (link.streamType == StreamType.DIRECT_FILE) {
        val len = dev.endlesssea.core.net.EsNet.contentLength(link.url, headers) ?: return null
        return if (len > 0) fmt(len, false) else null
    }
    if (link.streamType == StreamType.HLS) {
        val playlist = dev.endlesssea.core.net.EsNet.text(link.url, headers) ?: return null
        val base = link.url.substringBeforeLast('/', "")
        val segments = playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { if (it.startsWith("http")) it else "$base/$it" }
            .toList()
        if (segments.isEmpty()) return null
        val sampled = segments.take(3).mapNotNull {
            dev.endlesssea.core.net.EsNet.contentLength(it, headers)
        }
        if (sampled.isEmpty()) return null
        val avg = sampled.average()
        return fmt((avg * segments.size).toLong(), true)
    }
    return null
}
