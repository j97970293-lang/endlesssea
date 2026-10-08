package dev.endlesssea.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/** Shared light UI model for catalog rows across screens. */
data class SearchItemUi(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val subtitle: String? = null,
    /** Note sur 10 fournie par la source (badge ⭐). */
    val rating: Double? = null,
    /** Langues audio annoncées par la source (« VF », « VOSTFR »…). */
    val audioLangs: List<String> = emptyList(),
    /** §recherche-groupée : extension d'origine (pkg + nom affiché). */
    val sourcePkg: String = "",
    val sourceName: String = "",
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val type: dev.endlesssea.extensions.api.model.MediaType? = null,
)

/**
 * Recherche multi-extensions (spec §12) : une requête interroge tous les providers
 * activés, les résultats sont regroupés/dédupliqués, les erreurs isolées par source.
 */
@Composable
fun SearchScreen(
    onMediaClick: (String) -> Unit,
    /** §recherche-source : source imposée par l'appelant (bouton de la barre du haut). */
    initialSource: String = "ALL",
    viewModel: SearchViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show() }
    androidx.compose.runtime.LaunchedEffect(initialSource) {
        if (initialSource.isNotBlank()) viewModel.setSourceFilter(initialSource)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).focusRequester(focusRequester),
            placeholder = { Text("One Piece, Naruto, un film…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, "Effacer")
                    }
                }
            },
            singleLine = true,
        )

        // §recherche-sources : cases pour désactiver des sources + mode « résultats seuls »
        val excluded by viewModel.searchExcluded.collectAsState()
        val resultsOnly by viewModel.resultsOnly.collectAsState()
        var showSourcePicker by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.AssistChip(
                onClick = { showSourcePicker = true },
                label = {
                    Text(
                        if (excluded.isEmpty()) "Sources : toutes"
                        else "Sources : ${excluded.size} désactivée(s)",
                    )
                },
            )
            androidx.compose.material3.FilterChip(
                selected = resultsOnly,
                onClick = { viewModel.setResultsOnly(!resultsOnly) },
                label = { Text("Résultats seuls", maxLines = 1, softWrap = false) },
            )
        }
        if (showSourcePicker) {
            val sources by viewModel.availableSources.collectAsState()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSourcePicker = false },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showSourcePicker = false }) {
                        Text("Fermer")
                    }
                },
                title = { Text("Sources interrogées") },
                text = {
                    Column {
                        if (sources.isEmpty()) Text("Aucune extension activée.")
                        sources.forEach { (id, name) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(
                                    checked = id !in excluded,
                                    onCheckedChange = { viewModel.toggleSearchSource(id) },
                                )
                                Text(name)
                            }
                        }
                    }
                },
            )
        }

        // ---- Filtres transmis aux extensions (FilterSet : type + langue)
        androidx.compose.foundation.layout.Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("ANIME" to "Anime", "MOVIE" to "Film", "SERIES" to "Série").forEach { (key, label) ->
                androidx.compose.material3.FilterChip(
                    selected = key in state.selTypes,
                    onClick = { viewModel.toggleType(key) },
                    label = { Text(label, maxLines = 1, softWrap = false) },
                )
            }
            Text(
                "│", color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(4.dp),
            )
            listOf("vf" to "VF", "vostfr" to "VOSTFR", "vo" to "VO").forEach { (key, label) ->
                androidx.compose.material3.FilterChip(
                    selected = key in state.selLangs,
                    onClick = { viewModel.toggleLang(key) },
                    label = { Text(label, maxLines = 1, softWrap = false) },
                )
            }
        }

        if (state.perExtensionErrors.isNotEmpty()) {
            state.perExtensionErrors.forEach { (ext, msg) ->
                Text(
                    "$ext : $msg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }

        if (state.loading) {
            if (state.results.isEmpty()) dev.endlesssea.app.ui.components.CatalogLoadingSkeleton()
            else dev.endlesssea.app.ui.components.EsLoadingIndicator(Modifier.padding(8.dp))
        }

        if (state.results.isEmpty() && state.query.isNotBlank() && !state.loading) {
            Text("Aucun résultat", modifier = Modifier.padding(16.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // §recherche-groupée : un en-tête PAR extension — on sait toujours
            // quelle source a renvoyé quelle vignette.
            val groups = if (resultsOnly) mapOf("" to state.results)
            else state.results.groupBy { it.sourceName.ifBlank { "Source" } }
            groups.forEach { (srcName, rows) ->
                if (srcName.isNotBlank()) item(key = "hdr:$srcName", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    ) {
                        Text(
                            srcName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "  ·  ${rows.size} résultat(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(rows, key = { it.id }) { item ->
                    dev.endlesssea.app.ui.components.MediaCard(item = item, onClick = { onMediaClick(item.id) })
                }
            }
        }
    }
}
