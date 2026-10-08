package dev.endlesssea.app.ui.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    /** §recherche-dans-explorer : (requête, source) → écran de résultats. */
    onSearch: (String, String) -> Unit = { _, _ -> },
    /** (pkg, catégorie) → page « Tout voir » de cette rangée. */
    onSeeAll: (String, String) -> Unit,
    viewModel: ExploreViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // Sélecteur d'extension : « Toutes » agrège les catalogues, sinon une seule source
    var selectedPkg by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf(false) }
    val visibleRows = if (selectedPkg == null) state.rows else state.rows.filter { it.pkg == selectedPkg }

    Column(Modifier.fillMaxSize()) {
        // §recherche-dans-explorer : la recherche vit ici (plus d'onglet dédié) et
        // interroge la source sélectionnée dans les pastilles ci-dessous.
        dev.endlesssea.app.ui.components.EndlessSeaTopBar(
            title = "Explorer", subtitle = "Catalogues des extensions", icon = Icons.Filled.Explore,
            onSearch = { onSearch("", selectedPkg ?: "ALL") },
            searchDescription = "Rechercher dans les extensions",
        )
        
        // §filtres-explorer : filtres par genre, année, type
        var showFilters by remember { mutableStateOf(false) }
        var selectedGenre by remember { mutableStateOf<String?>(null) }
        var selectedYear by remember { mutableStateOf<Int?>(null) }
        var selectedType by remember { mutableStateOf<String?>(null) }
        
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            TextButton(onClick = { suggestions = false }) { Text("Catalogue") }
            TextButton(onClick = { suggestions = true }) { Text("Suggestions") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(Icons.Filled.FilterList, "Filtres")
            }
        }
        
        // §filtres-explorer : dialogue de filtres
        if (showFilters) {
            FilterDialog(
                onDismiss = { showFilters = false },
                selectedGenre = selectedGenre,
                selectedYear = selectedYear,
                selectedType = selectedType,
                onGenreSelected = { selectedGenre = it },
                onYearSelected = { selectedYear = it },
                onTypeSelected = { selectedType = it },
                onReset = {
                    selectedGenre = null
                    selectedYear = null
                    selectedType = null
                },
            )
        }
        
        // Appliquer les filtres aux rangées
        val filteredRows = remember(visibleRows, selectedGenre, selectedYear, selectedType) {
            filterExploreRows(visibleRows, selectedGenre, selectedYear, selectedType)
        }
        if (suggestions) {
            val items by viewModel.suggestions.collectAsState()
            LazyColumn {
                item {
                    if (items.isEmpty()) Text("Regardez quelques épisodes pour obtenir des suggestions.", Modifier.padding(16.dp))
                    else MediaRow(title = "D'après votre activité récente", items = items, onMediaClick = onMediaClick)
                }
            }
        } else {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, "Rafraîchir") }
            FilterChip(
                border = null,
                selected = selectedPkg == null,
                onClick = { selectedPkg = null },
                label = { Text("Toutes", maxLines = 1, softWrap = false) },
            )
            state.extensions.forEach { (pkg, name) ->
                FilterChip(
                    border = null,
                    selected = selectedPkg == pkg,
                    onClick = { selectedPkg = if (selectedPkg == pkg) null else pkg },
                    label = { Text(name, maxLines = 1, softWrap = false) },
                )
            }
        }

        val visibleErrors = if (selectedPkg == null) state.errors
        else state.errors.filterKeys { name -> state.extensions.any { it.first == selectedPkg && it.second == name } }

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
                if (filteredRows.isEmpty() && visibleErrors.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(":(", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (selectedGenre != null || selectedYear != null || selectedType != null) {
                                    "Aucun résultat pour les filtres sélectionnés"
                                } else {
                                    "Catalogue vide pour l'instant — essayez une autre source ci-dessus."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                items(filteredRows, key = { "${it.pkg}:${it.title}" }) { row ->
                    MediaRow(
                        title = row.title, items = row.items, onMediaClick = onMediaClick,
                        onSeeAll = { onSeeAll(row.pkg, row.category) },
                    )
                }
                visibleErrors.forEach { (name, err) ->
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
}

// §filtres-explorer : composant de dialogue de filtres
@Composable
private fun FilterDialog(
    onDismiss: () -> Unit,
    selectedGenre: String?,
    selectedYear: Int?,
    selectedType: String?,
    onGenreSelected: (String?) -> Unit,
    onYearSelected: (Int?) -> Unit,
    onTypeSelected: (String?) -> Unit,
    onReset: () -> Unit,
) {
    val genres = listOf("Action", "Aventure", "Comédie", "Drame", "Fantastique", "Horreur", "Romance", "SF", "Thriller")
    val years = (2010..2026).reversed().toList()
    val types = listOf("Anime", "Film", "Série", "OVA", "ONA")
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtres") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Filtre par genre
                Column {
                    Text("Genre", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        genres.forEach { genre ->
                            FilterChip(
                                selected = selectedGenre == genre,
                                onClick = { 
                                    onGenreSelected(if (selectedGenre == genre) null else genre)
                                },
                                label = { Text(genre) },
                            )
                        }
                    }
                }
                
                // Filtre par année
                Column {
                    Text("Année", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        years.forEach { year ->
                            FilterChip(
                                selected = selectedYear == year,
                                onClick = { 
                                    onYearSelected(if (selectedYear == year) null else year)
                                },
                                label = { Text(year.toString()) },
                            )
                        }
                    }
                }
                
                // Filtre par type
                Column {
                    Text("Type", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        types.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { 
                                    onTypeSelected(if (selectedType == type) null else type)
                                },
                                label = { Text(type) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text("Réinitialiser")
            }
        },
    )
}
