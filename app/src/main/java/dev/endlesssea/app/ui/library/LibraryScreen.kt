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
                Tab(selected = tab == i, onClick = { tab = i; viewModel.onCategory(key) }, text = { Text(label) })
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
