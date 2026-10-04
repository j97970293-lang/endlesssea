package dev.endlesssea.app.ui.explore

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.MediaCard

/** Grille « Tout voir » paginée : catalogue complet d'une extension. */
@Composable
fun SeeAllScreen(
    onMediaClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SeeAllViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    // Pagination automatique quand on approche du bas
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 6 && last >= 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasNextPage && !state.loadingMore) viewModel.loadNext()
    }

    Column(Modifier.fillMaxSize()) {
        // Ligne de titre légère (la barre globale de l'app reste au-dessus)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
            Text(
                state.extensionName.ifBlank { "Catalogue" },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null && state.items.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("(˘̩︿˘̩)", style = MaterialTheme.typography.headlineSmall)
                Text(
                    state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.loadNext() }) { Text("Réessayer") }
            }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items, key = { it.id }) { item ->
                    MediaCard(item = item, onClick = { onMediaClick(item.id) })
                }
                if (state.loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.height(28.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                if (!state.hasNextPage && state.items.isNotEmpty()) {
                    item {
                        Text(
                            "— Fin du catalogue —",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
