package dev.endlesssea.app.ui.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.tracking.*
import dev.endlesssea.app.ui.components.*
import dev.endlesssea.app.ui.search.SearchItemUi

@Composable
internal fun AniListLibraryPanel(query: String, onMediaClick: (String) -> Unit, onFindSource: (String) -> Unit,
    model: AniListLibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val state by model.state.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val menus = LocalSectionMenu.current
    var status by remember { mutableStateOf("ALL") }
    var statuses by remember { mutableStateOf(false) }
    var selected by remember(state.account) { mutableStateOf<RemoteLibraryEntry?>(null) }
    var matching by remember(state.account) { mutableStateOf(false) }
    var targetQuery by remember(state.account) { mutableStateOf("") }
    var target by remember(state.account) { mutableStateOf<SearchItemUi?>(null) }
    if (state.account == null) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Retrouvez votre liste AniList", style = MaterialTheme.typography.titleLarge)
            Text("Les titres suivis dans vos autres applications apparaîtront ici si elles utilisent le même compte AniList.", Modifier.padding(vertical = 12.dp))
            Button(onClick = menus.trackers) { Text("Connecter AniList") }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AniList · ${state.account}", style = MaterialTheme.typography.labelLarge)
                Text(if (state.updatedAt > 0) "Importé à ${java.text.DateFormat.getDateTimeInstance(3,3).format(java.util.Date(state.updatedAt))}" else "Pas encore importé",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                TextButton(onClick = { statuses = true }) { Text(if (status == "ALL") "Tous les statuts" else remoteStatusLabel(status)) }
                DropdownMenu(statuses, { statuses = false }) {
                    listOf("ALL","WATCHING","PLANNING","COMPLETED","PAUSED","DROPPED","REPEATING").forEach { s ->
                        DropdownMenuItem(text = { Text(if (s == "ALL") "Tous" else remoteStatusLabel(s)) }, onClick = { status = s; statuses = false })
                    }
                }
            }
            IconButton(onClick = model::refresh, enabled = !state.busy) { Icon(Icons.Default.Refresh,"Importer les changements AniList") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        val entries = state.entries.filter { (status == "ALL" || status == it.status) && it.title.contains(query, true) }
        if (entries.isEmpty() && !state.busy) Text("Aucun titre pour cette sélection. Vérifiez le compte et actualisez si nécessaire.", Modifier.padding(24.dp))
        LazyVerticalGrid(GridCells.Adaptive(140.dp), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(entries, key = { it.id }) { entry ->
                CatalogPoster(SearchItemUi(entry.id,entry.title,entry.poster), onClick = { selected = entry; matching = false; target = null },
                    caption = "${entry.progress}/${entry.total ?: "?"} · ${remoteStatusLabel(entry.status)}")
            }
        }
    }
    selected?.let { entry ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(entry.title) },
            text = { Column {
                Text("${remoteStatusLabel(entry.status)} · ${entry.progress}/${entry.total ?: "?"} épisodes")
                Text("AniList fournit le suivi et les métadonnées, pas les vidéos.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                if (matching) {
                    OutlinedTextField(targetQuery, { targetQuery = it }, label = { Text("Rechercher dans ma liste") }, singleLine = true)
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(state.targets.filter { it.title.contains(targetQuery,true) }, key = { it.id }) { item ->
                            TextButton(onClick = { target = item }) { Text((if (target?.id == item.id) "✓ " else "") + item.title) }
                        }
                    }
                    Text("Choisissez la bonne fiche. Cette association remplace son éventuel suivi précédent, sans modifier votre liste distante. Le suivi automatique des épisodes reste désactivé.", style = MaterialTheme.typography.bodySmall)
                } else {
                    state.linked[entry.id].orEmpty().forEach { id ->
                        TextButton(onClick = { selected = null; onMediaClick(id) }) { Text("Ouvrir la fiche associée") }
                    }
                    TextButton(onClick = { matching = true; targetQuery = entry.title }) { Text("Associer manuellement à ma liste") }
                    TextButton(onClick = { selected = null; onFindSource(entry.title) }) { Text("Rechercher dans les extensions") }
                }
            } },
            confirmButton = { if (matching) TextButton(enabled = target != null, onClick = {
                target?.let { model.match(it.id, entry) { selected = null; onMediaClick(it.id) } }
            }) { Text("Confirmer l'association") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Fermer") } })
    }
}
