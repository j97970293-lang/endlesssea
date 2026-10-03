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
@Composable
fun HomeScreen(
    onMediaClick: (String) -> Unit,
    viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            if (state.featured.isNotEmpty()) {
                FeaturedBanner(
                    items = state.featured,
                    autoScroll = state.bannerAutoScroll,
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

        // ---- Rangées en ligne des extensions (la vraie vie de l'accueil)
        state.remoteRows.forEach { row ->
            item(key = "remote-${row.title}") {
                MediaRow(title = row.title, items = row.items, onMediaClick = onMediaClick)
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
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
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
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { /* voir tout */ }) { Text("Tout voir") }
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
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
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
        Text("🌊", style = MaterialTheme.typography.displayMedium)
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
