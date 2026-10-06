package dev.endlesssea.app.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.endlesssea.app.ui.search.SearchItemUi
import kotlinx.coroutines.delay

/**
 * Accueil (spec §10) : grande bannière horizontale auto/manuelle + rangées
 * « Ajouts récents », « Reprendre », « Téléchargements en cours », « Favoris ».
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) // ModalBottomSheet
@Composable
fun HomeScreen(
    onMediaClick: (String) -> Unit,
    viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // §fournisseurs : état et feuille modale hissés hors de la LazyColumn —
    // LazyListScope n'est pas @Composable, on ne peut y appeler ni remember
    // ni ModalBottomSheet (erreur « @Composable invocations can only happen
    // from the context of a @Composable function »).
    var providerSheetOpen by remember { mutableStateOf(false) }
    val carouselStyle by dev.endlesssea.app.ui.components.UiTuning.carouselStyle.collectAsState()
    // §fournisseur-en-haut : la barre du haut demande l'ouverture de la feuille
    val providerRequest by HomeUiBus.providerSheetRequest.collectAsState()
    androidx.compose.runtime.LaunchedEffect(providerRequest) {
        if (providerRequest > 0) providerSheetOpen = true
    }
    // Le libellé affiché dans la barre suit le filtre courant
    androidx.compose.runtime.LaunchedEffect(state.sourceFilter, state.sources) {
        HomeUiBus.publishProvider(
            state.sources.firstOrNull { it.first == state.sourceFilter }?.second ?: "Toutes mes sources",
            state.sourceFilter,
        )
    }
    val animationsOn by dev.endlesssea.app.ui.components.UiTuning.animations.collectAsState()

    // §accueil-actualiser : tirer vers le bas recharge les catalogues des extensions
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = {
            viewModel.loadRemote()
            viewModel.publishSources()
        },
        modifier = Modifier.fillMaxSize(),
    ) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (state.featured.isNotEmpty()) {
                // §anymex-ui : « Carousel Style » — bannière pleine largeur (classic)
                // ou rangée d'affiches verticales (portrait).
                if (carouselStyle == "portrait") {
                    PortraitCarousel(items = state.featured, onClick = onMediaClick)
                } else
                FeaturedBanner(
                    items = state.featured,
                    autoScroll = state.bannerAutoScroll && animationsOn,
                    onClick = onMediaClick,
                    onPlay = { onMediaClick(it) },          // « Lire » ouvre la fiche → épisodes + lecteur
                    onAdd = { viewModel.onAddToLibrary(it) },
                    onDownload = { onMediaClick(it) },      // « Télécharger » ouvre la fiche → feuille serveur×qualité
                )
            } else if (state.loading) {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EmptyHome(onBrowseExtensions = { /* navigate handled by parent later */ })
            }
        }

        if (state.continueWatching.isNotEmpty()) {
            item {
                MediaRow(title = "Reprendre la lecture", items = state.continueWatching, onMediaClick = onMediaClick)
            }
        }

        // §fournisseur-en-haut : le bouton n'est plus au milieu de la liste,
        // il vit dans la barre du haut (MainActivity) ; seule la feuille reste ici.

        // §fournisseur-en-haut : la rangée de pastilles de sources a disparu —
        // le bouton de la barre du haut suffit (demande utilisateur).

        state.remoteRows
            .filter { state.sourceFilter == "ALL" || it.sourcePkg == state.sourceFilter }
            .forEach { row ->
            item(key = "remote-${row.title}") {
                MediaRow(
                    title = row.title, items = row.items, onMediaClick = onMediaClick,
                    iconUrl = row.iconUrl,
                )
            }
        }
        if (state.remoteRows.isEmpty() && state.extensionCount > 0 && !state.loading) {
            item { Text(
                "Vos extensions ne proposent pas de catalogue « à la une » — utilisez la recherche.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) }
        }

        if (state.recent.isNotEmpty()) {
            item {
                MediaRow(title = "Ajoutés récemment", items = state.recent, onMediaClick = onMediaClick)
            }
        }
        if (state.favorites.isNotEmpty()) {
            item {
                MediaRow(title = "Favoris", items = state.favorites, onMediaClick = onMediaClick)
            }
        }
        }

            if (providerSheetOpen) {
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { providerSheetOpen = false },
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Text("Choisir le fournisseur", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Quelle source alimente l'accueil « à la une » ?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))

                        ProviderRow(
                            title = "Toutes mes sources",
                            subtitle = "Les catalogues de chaque extension fusionnés",
                            selected = state.sourceFilter == "ALL",
                            enabled = true,
                        ) { viewModel.setSourceFilter("ALL"); providerSheetOpen = false }

                        state.sources.forEach { (pkg, name, iconUrl) ->
                            ProviderRow(
                                title = name,
                                subtitle = "Catalogue de l'extension $name",
                                iconUrl = iconUrl,
                                selected = state.sourceFilter == pkg,
                                enabled = true,
                            ) { viewModel.setSourceFilter(pkg); providerSheetOpen = false }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Trackers & métadonnées (gratuits)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        listOf(
                            "AniList" to "Suivi anime & manga (gratuit)",
                            "MyAnimeList" to "La plus grande base anime & manga",
                            "Simkl" to "Suivi films & séries",
                        ).forEach { (t, sub) ->
                            ProviderRow(
                                title = t, subtitle = "$sub · bientôt dans Endless Sea",
                                selected = false, enabled = false,
                            ) {}
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
    }
}

// ---------------------------------------------------------------- banner

@Composable
private fun FeaturedBanner(
    items: List<SearchItemUi>,
    autoScroll: Boolean,
    onClick: (String) -> Unit,
    onPlay: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDownload: (String) -> Unit,
) {
    val pager = rememberPagerState(pageCount = { items.size })

    // Défilement automatique optionnel (spec §10)
    LaunchedEffect(autoScroll, items.size) {
        if (!autoScroll || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(5_000)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick(items[pager.currentPage].id) }
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.bannerUrl ?: item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Dégradé lisibilité
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
                )
                // Badge année / type (style Anymex)
                item.subtitle?.take(10)?.let { badge ->
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(14.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFB9C1FF),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (!item.subtitle.isNullOrBlank()) {
                        Text(
                            item.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPlay(item.id) }) {
                            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Lire")
                        }
                        Button(
                            onClick = { onAdd(item.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.22f),
                                contentColor = Color.White,
                            ),
                        ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Ajouter") }
                        Button(
                            onClick = { onDownload(item.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.22f),
                                contentColor = Color.White,
                            ),
                        ) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(4.dp)); Text("Télécharger") }
                    }
                }
            }
        }
        // Indicateurs + flèches ← → (défilement manuel, spec §10)
        Row(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(items.size.coerceAtMost(8)) { i ->
                Box(
                    Modifier
                        .width(if (i == pager.currentPage) 18.dp else 8.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i == pager.currentPage) Color.White
                            else Color.White.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

// ---------------------------------------------------------------- rows

@Composable
fun MediaRow(
    title: String,
    items: List<SearchItemUi>,
    onMediaClick: (String) -> Unit,
    /** Icône de la source (accueil multi-extensions §accueil-multi). */
    iconUrl: String? = null,
    /** « Tout voir » : affiché uniquement si une action est fournie. */
    onSeeAll: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (iconUrl != null) {
                    dev.endlesssea.app.SafeAsyncImage(
                        url = iconUrl,
                        contentDescription = null,
                        modifier = Modifier.width(22.dp).height(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll) { Text("Tout voir") }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.width(120.dp).clickable { onMediaClick(item.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        )
                        Text(
                            item.title,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(onBrowseExtensions: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Extension, contentDescription = null,
            modifier = Modifier.height(56.dp).width(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text("Aucune extension installée", style = MaterialTheme.typography.titleMedium)
        Text(
            "Endless Sea ne contient aucune source en dur. Ajoutez un dépôt d'extensions pour commencer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBrowseExtensions) { Text("Ouvrir Extensions") }
    }
}


@Composable
private fun ProviderRow(
    title: String,
    subtitle: String,
    iconUrl: String? = null,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = if (!enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        else if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconUrl != null) {
                dev.endlesssea.app.SafeAsyncImage(
                    url = iconUrl, contentDescription = null,
                    modifier = Modifier.width(36.dp).height(36.dp),
                )
            } else {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                ) {
                    Icon(
                        Icons.Filled.Extension, contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (selected) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}


/**
 * §anymex-ui — carrousel « Portrait » : les titres à la une défilent en cartes
 * d'affiches verticales au lieu d'une bannière pleine largeur.
 */
@Composable
private fun PortraitCarousel(items: List<SearchItemUi>, onClick: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(items, key = { it.id }) { item ->
            dev.endlesssea.app.ui.components.MediaCard(item = item, onClick = { onClick(item.id) })
        }
    }
}
