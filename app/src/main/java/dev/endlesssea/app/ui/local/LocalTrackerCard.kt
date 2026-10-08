package dev.endlesssea.app.ui.local

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.local.LocalMediaIds
import dev.endlesssea.app.local.LocalVideos
import dev.endlesssea.app.tracking.TrackerRegistry
import dev.endlesssea.app.tracking.TrackerSearchHit
import dev.endlesssea.app.ui.library.LocalVideoUi
import dev.endlesssea.app.ui.tracking.EpisodeMatchingControls

@Composable
internal fun LocalTrackerCard(
    folderUri: String, title: String, files: List<LocalVideoUi>,
    viewModel: LocalTrackerViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    var changeTracker by remember(folderUri) { mutableStateOf(false) }
    val accounts by viewModel.accounts.collectAsState()
    val defaultService by viewModel.defaultService.collectAsState()
    val links by viewModel.links.collectAsState()
    val hits by viewModel.hits.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val services = accounts.filter { it.enabled && it.userName.isNotBlank() && it.service != "TMDB" }.map { it.service }
    val link = links.firstOrNull { it.mediaId == LocalMediaIds.series(folderUri) }
    val seasons = files.map { LocalVideos.episodeSeason(it.name) }.distinct().ifEmpty { listOf(null) }
    var query by remember(folderUri, title) { mutableStateOf(title) }
    var service by remember(services, defaultService) { mutableStateOf(defaultService?.takeIf { it in services } ?: services.firstOrNull()) }
    var selected by remember(folderUri) { mutableStateOf<TrackerSearchHit?>(null) }
    var autoMatch by remember(folderUri) { mutableStateOf(false) }
    var season by remember(folderUri, seasons) { mutableStateOf(seasons.firstOrNull()) }
    LaunchedEffect(link?.service, link?.remoteId) { changeTracker = false }
    LaunchedEffect(folderUri, service) { viewModel.clearSearch() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Suivi de la série locale", style = MaterialTheme.typography.titleMedium)
        if (changeTracker) TextButton(onClick = { changeTracker = false }) { Text("Annuler le changement") }
        if (link != null && !changeTracker) {
            Text("${link.title} · ${TrackerRegistry.LABELS[link.service] ?: link.service}")
            Text("${link.progress}" + (link.totalEpisodes.takeIf { it > 0 }?.let { " / $it" } ?: "") + " épisode(s)")
            if (link.pendingSync) Text("Synchronisation en attente", style = MaterialTheme.typography.bodySmall)
            EpisodeMatchingControls(link.autoMatchEpisodes, link.autoMatchSeason, seasons) { enabled, s -> viewModel.setMatching(folderUri, enabled, s) }
            dev.endlesssea.app.ui.tracking.TrackerProgressEditor(link.progress, link.totalEpisodes) { viewModel.setProgress(link, it) }
            TextButton(onClick = { changeTracker = true }) { Text("Changer de tracker / de titre") }
            Row {
                TextButton(onClick = { viewModel.next(link) }) { Text("+1 épisode vu") }
                TextButton(onClick = { viewModel.unlink(folderUri) }) { Text("Ne plus suivre") }
            }
        } else if (services.isEmpty()) {
            Text("Connecte un compte AniList, MyAnimeList ou Shikimori dans Paramètres > Comptes & suivi. Aucune recherche n'est envoyée sans compte actif.", style = MaterialTheme.typography.bodySmall)
        } else {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Titre à rechercher") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                services.forEach { id -> FilterChip(selected = service == id, onClick = { service = id }, label = { Text(TrackerRegistry.LABELS[id] ?: id) }) }
            }
            TextButton(enabled = !busy && query.isNotBlank(), onClick = { service?.let { viewModel.search(it, query) } }) { Text("Rechercher") }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            hits.forEach { hit ->
                Row(Modifier.fillMaxWidth().clickable { selected = hit }.padding(vertical = 6.dp)) {
                    coil.compose.AsyncImage(model = hit.posterUrl, contentDescription = null, modifier = Modifier.width(44.dp).height(64.dp))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(hit.title)
                        Text(listOfNotNull(hit.year?.toString(), hit.totalEpisodes?.let { "$it épisodes" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    selected?.let { hit ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("Confirmer le rattachement") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Lier « $title » à « ${hit.title} »" + (hit.year?.let { " ($it)" } ?: "") + " ?")
                EpisodeMatchingControls(autoMatch, season, seasons) { enabled, s -> autoMatch = enabled; season = s }
            } },
            confirmButton = { TextButton(onClick = { service?.let { viewModel.link(folderUri, it, hit, autoMatch, season) }; selected = null }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Annuler") } })
    }
}
