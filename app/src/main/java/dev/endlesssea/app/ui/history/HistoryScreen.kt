package dev.endlesssea.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.data.db.WatchHistoryDao
import dev.endlesssea.data.db.MediaDao
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: WatchHistoryDao,
    private val mediaDao: MediaDao,
    private val prefs: AppPrefs,
) : ViewModel() {
    private val _entries = MutableStateFlow<List<dev.endlesssea.data.db.WatchHistoryEntity>>(emptyList())
    val entries: StateFlow<List<dev.endlesssea.data.db.WatchHistoryEntity>> = _entries
    val recordHistory = prefs.recordHistory
    private val _titles = MutableStateFlow<Map<String, String>>(emptyMap())
    val titles: StateFlow<Map<String, String>> = _titles
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError

    /** Resolved titles are cached between progress updates, avoiding N SQL lookups per emission. */
    private val titleCache = mutableMapOf<String, Pair<String, Long>>()

    init {
        // One Room observer feeds both the list and labels. Previously the screen opened
        // two full-table flows and issued one media query per distinct title on every update.
        viewModelScope.launch {
            dao.observeAll()
                .map { rows -> resolveSnapshot(rows) }
                .catch { error ->
                    android.util.Log.e("HistoryViewModel", "Impossible de charger l’historique", error)
                    _loadError.value = "Impossible de charger l’historique. Réessayez plus tard."
                }
                .collect { (rows, titles) ->
                    _entries.value = rows
                    _titles.value = titles
                    _loadError.value = null
                }
        }
    }

    private suspend fun resolveSnapshot(
        rows: List<dev.endlesssea.data.db.WatchHistoryEntity>,
    ): Pair<List<dev.endlesssea.data.db.WatchHistoryEntity>, Map<String, String>> {
        val mediaIds = rows.asSequence()
            .map { it.mediaId }
            .filter { it.isNotBlank() && !it.startsWith("local:") }
            .distinct()
            .toList()
        val now = System.currentTimeMillis()
        val refreshBefore = now - 60_000L
        val needingLookup = mediaIds.filter { id -> titleCache[id]?.second?.let { it >= refreshBefore } != true }

        // SQLite bind-variable limits vary by Android release. Keep each IN query small.
        needingLookup.chunked(400).forEach { batch ->
            mediaDao.byIds(batch).forEach { media ->
                titleCache[media.id] = (media.customTitle?.takeIf { it.isNotBlank() } ?: media.title) to now
            }
        }
        needingLookup.forEach { id -> if (id !in titleCache) titleCache[id] = id to now }
        titleCache.keys.retainAll(mediaIds.toSet())

        val titles = rows.asSequence()
            .map { it.mediaId }
            .filter { it.isNotBlank() }
            .distinct()
            .associateWith { id ->
                if (id.startsWith("local:")) {
                    val seriesId = id.removePrefix("local:")
                    dev.endlesssea.app.local.LocalVideos.seriesMeta[seriesId]?.title
                        ?: dev.endlesssea.app.local.LocalNames.pretty(seriesId)
                } else titleCache[id]?.first ?: id
            }
        return rows to titles
    }

    fun setRecord(v: Boolean) = prefs.setRecordHistory(v)
    fun deleteEpisode(id: String) = viewModelScope.launch { dao.deleteEpisode(id) }
    fun deleteMedia(id: String) = viewModelScope.launch { dao.deleteMedia(id) }
    fun clear(action: String) = viewModelScope.launch {
        when (action) {
            "all" -> dao.deleteAll()
            "completed" -> dao.deleteCompleted()
            else -> action.toLongOrNull()?.takeIf { it in listOf(7L, 30L, 90L) }?.let {
                dao.deleteOlderThan(System.currentTimeMillis() - it * 86_400_000L)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val entries by viewModel.entries.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val record by viewModel.recordHistory.collectAsState()
    var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Retour") }
            Text("Historique (${entries.size})", style = MaterialTheme.typography.titleLarge)
            loadError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row {
                Text("Enregistrer l'historique", modifier = Modifier.weight(1f))
                Switch(record, viewModel::setRecord)
            }
            Text("Effacer l'historique ne supprime ni les vidéos ni le suivi envoyé aux trackers.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { pending = "Tout effacer ?" to { viewModel.clear("all") } }) { Text("Tout effacer") }
            TextButton(onClick = { pending = "Effacer les épisodes marqués vus ou lus à 95 % ?" to { viewModel.clear("completed") } }) { Text("Effacer les vus") }
            Row {
                listOf(7, 30, 90).forEach { days ->
                    TextButton(onClick = { pending = "Effacer les entrées de plus de $days jours ?" to { viewModel.clear(days.toString()) } }) { Text("$days jours") }
                }
            }
        }
        items(entries, key = { it.episodeId }) { entry ->
            val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    pending = "Supprimer cette entrée ?" to { viewModel.deleteEpisode(entry.episodeId) }
                }
                false
            })
            SwipeToDismissBox(
                modifier = if (dev.endlesssea.app.ui.motion.LocalAppMotion.current.enabled) Modifier.animateItem() else Modifier,
                state = dismissState, backgroundContent = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),
                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }) { Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                Text(titles[entry.mediaId] ?: "Vidéo locale", style = MaterialTheme.typography.titleSmall)
                Text(entry.episodeId.substringAfterLast('/'), maxLines = 1)
                Text("${dev.endlesssea.app.ui.player.themes.fmtTime(entry.positionMs)} / ${dev.endlesssea.app.ui.player.themes.fmtTime(entry.durationMs)}")
                Row {
                    TextButton(onClick = { pending = "Supprimer cette entrée ?" to { viewModel.deleteEpisode(entry.episodeId) } }) { Text("Supprimer") }
                    if (entry.mediaId.isNotBlank()) TextButton(onClick = { pending = "Supprimer l'historique de cette série ?" to { viewModel.deleteMedia(entry.mediaId) } }) { Text("Toute la série") }
                }
            } }
        }
    }
    pending?.let { (label, action) ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(label) },
            confirmButton = { TextButton(onClick = { action(); pending = null }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } })
    }
}
