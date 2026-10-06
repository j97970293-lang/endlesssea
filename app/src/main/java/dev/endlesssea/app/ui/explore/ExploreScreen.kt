package dev.endlesssea.app.ui.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
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
    /** §recherche-dans-explorer : (requête, source) → écran de résultats. */
    onSearch: (String, String) -> Unit = { _, _ -> },
    /** (pkg, catégorie) → page « Tout voir » de cette rangée. */
    onSeeAll: (String, String) -> Unit,
    viewModel: ExploreViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    // Sélecteur d'extension : « Toutes » agrège les catalogues, sinon une seule source
    var selectedPkg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        // §recherche-dans-explorer : la recherche vit ici (plus d'onglet dédié) et
        // interroge la source sélectionnée dans les pastilles ci-dessous.
        var exploreQuery by remember { mutableStateOf("") }
        androidx.compose.material3.OutlinedTextField(
            value = exploreQuery,
            onValueChange = { exploreQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text("Rechercher dans " + (selectedPkg?.let { pkg -> state.extensions.firstOrNull { it.first == pkg }?.second } ?: "toutes les sources") + "…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                IconButton(onClick = { if (exploreQuery.isNotBlank()) onSearch(exploreQuery, selectedPkg ?: "ALL") }) {
                    Icon(Icons.Filled.Search, "Lancer la recherche")
                }
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { if (exploreQuery.isNotBlank()) onSearch(exploreQuery, selectedPkg ?: "ALL") },
            ),
        )
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
                selected = selectedPkg == null,
                onClick = { selectedPkg = null },
                label = { Text("Toutes") },
            )
            state.extensions.forEach { (pkg, name) ->
                FilterChip(
                    selected = selectedPkg == pkg,
                    onClick = { selectedPkg = if (selectedPkg == pkg) null else pkg },
                    label = { Text(name) },
                )
            }
        }

        val visibleRows = if (selectedPkg == null) state.rows else state.rows.filter { it.pkg == selectedPkg }
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
                if (visibleRows.isEmpty() && visibleErrors.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("(¬‿¬)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Catalogue vide pour l'instant — essayez une autre source ci-dessus.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                items(visibleRows, key = { "${it.pkg}:${it.title}" }) { row ->
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
