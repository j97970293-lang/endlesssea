package dev.endlesssea.app.ui.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Explorer (spec §11–§12) : rangées par extension activée + puces de filtres
 * (genres visibles, type, année, langue, statut, qualité).
 */
@Composable
fun ExploreScreen(onMediaClick: (String) -> Unit) {
    val genreFilters = remember { mutableStateListOf("Action", "Romance", "Isekai", "Thriller") }
    val selected = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Filtres (spec §12 : genres, année, type, langue, statut, qualité)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genreFilters.forEach { genre ->
                FilterChip(
                    selected = selected.value == genre,
                    onClick = { selected.value = if (selected.value == genre) null else genre },
                    label = { Text(genre) },
                )
            }
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Installez des extensions pour explorer leurs catalogues. Les rangées de chaque provider apparaîtront ici (tendance, nouveautés…).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
