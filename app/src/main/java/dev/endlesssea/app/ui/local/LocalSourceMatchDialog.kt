package dev.endlesssea.app.ui.local

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.library.LocalVideoUi

@Composable
internal fun LocalSourceMatchDialog(
    folder: String, initialTitle: String, files: List<LocalVideoUi>,
    onDismiss: () -> Unit, onApplied: () -> Unit,
    model: LocalSourceMatchViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by model.state.collectAsState()
    DisposableEffect(folder) { onDispose { model.dismiss() } }
    var query by remember(folder) { mutableStateOf(initialTitle) }
    var fallbackSeason by remember(state.preview?.hit) { mutableStateOf<Int?>(null) }
    var importEpisodes by remember { mutableStateOf(true) }
    val preview = state.preview
    val matched = preview?.let { model.pairs(it, files, fallbackSeason).size } ?: 0
    fun close() { model.dismiss(); onDismiss() }
    AlertDialog(onDismissRequest = { if (!state.busy) close() },
        title = { Text(if (preview == null) "Associer à une fiche en ligne" else "Confirmer l'association") },
        text = {
            Column {
                if (preview == null) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Titre de l'œuvre") })
                    TextButton(enabled = query.isNotBlank() && !state.busy, onClick = { model.search(query) }) { Text("Rechercher dans mes extensions") }
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (preview == null) {
                        items(state.hits, key = { it.source + ":" + it.item.url }) { hit ->
                            TextButton(enabled = !state.busy, onClick = { model.preview(hit) }) {
                                dev.endlesssea.app.SafeAsyncImage(url = hit.item.posterUrl, contentDescription = null,
                                    modifier = Modifier.width(44.dp).height(62.dp))
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(hit.item.title)
                                    Text(listOfNotNull(hit.sourceName, hit.item.year?.toString(), hit.item.type.name).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        item {
                            Text(preview.details.title, style = MaterialTheme.typography.titleMedium)
                            Text(preview.hit.sourceName, style = MaterialTheme.typography.labelSmall)
                            preview.details.synopsis?.let { Text(it.take(800), style = MaterialTheme.typography.bodySmall) }
                            Text("L'affiche et les métadonnées de la fiche seront importées. Vos titres et affiches manuels restent prioritaires.",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = importEpisodes, enabled = !state.busy, onCheckedChange = { importEpisodes = it })
                                Text("Associer aussi les épisodes")
                            }
                            if (importEpisodes) {
                                Text("Saison des fichiers sans numéro de saison :", style = MaterialTheme.typography.labelMedium)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(selected = fallbackSeason == null, enabled = !state.busy,
                                        onClick = { fallbackSeason = null }, label = { Text("Ne pas deviner") })
                                    preview.details.seasons.map { it.number }.distinct().sorted().forEach { season ->
                                        FilterChip(selected = fallbackSeason == season, enabled = !state.busy,
                                            onClick = { fallbackSeason = season }, label = { Text("Saison $season") })
                                    }
                                }
                                Text("$matched / ${files.size} fichiers reconnus par saison et numéro. Les doublons et cas ambigus restent inchangés.")
                            }
                            Text("Aucun fichier renommé ni téléchargé. L'historique conserve les identifiants locaux ; cette action ne modifie pas le suivi distant.",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (preview != null) TextButton(enabled = !state.busy, onClick = {
                model.apply(folder, files, fallbackSeason, importEpisodes) { onApplied(); close() }
            }) { Text("Associer") }
        },
        dismissButton = { TextButton(enabled = !state.busy, onClick = { close() }) { Text("Annuler") } })
}
