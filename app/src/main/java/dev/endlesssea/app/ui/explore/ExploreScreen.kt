package dev.endlesssea.app.ui.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.home.MediaRow

/**
 * Explore (spec §11) : rangées « catalogue » des extensions activées.
 * Ouvrir une extension depuis l'écran Extensions mène ici.
 */
@Composable
fun ExploreScreen(
    onMediaClick: (String) -> Unit,
    viewModel: ExploreViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    val genreChips = remember {
        listOf("Action", "Romance", "Isekai", "Thriller", "Comédie", "Péplum", "Sci-fi")
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, "Rafraîchir") }
            genreChips.forEach { genre ->
                FilterChip(
                    selected = selectedGenre == genre,
                    onClick = { selectedGenre = if (selectedGenre == genre) null else genre },
                    label = { Text(genre) },
                )
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.extensionCount == 0 -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Aucune extension activée",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Ouvrez « Extensions » puis ajoutez le dépôt de démonstration ou un fichier .esx. Repos activés : synchronisez pour voir les catalogues.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (state.rows.isEmpty() && state.errors.isEmpty()) {
                    item {
                        Text(
                            "Les extensions répondent mais leur catalogue « principal » est vide pour l'instant.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                items(state.rows, key = { it.title }) { row ->
                    MediaRow(title = row.title, items = row.items, onMediaClick = onMediaClick)
                }
                state.errors.forEach { (name, err) ->
                    item(key = "err-$name") {
                        Text(
                            "« $name » : $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
