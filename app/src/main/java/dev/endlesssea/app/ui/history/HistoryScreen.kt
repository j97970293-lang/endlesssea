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
class HistoryViewModel @Inject constructor(private val dao: WatchHistoryDao, private val mediaDao: MediaDao, private val prefs: AppPrefs) : ViewModel() {
    val entries = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recordHistory = prefs.recordHistory
    private val _titles = MutableStateFlow<Map<String, String>>(emptyMap())
    val titles: StateFlow<Map<String, String>> = _titles
    init {
        viewModelScope.launch {
            dao.observeAll().collect { entries ->
                _titles.value = entries.map { it.mediaId }.distinct().filter { it.isNotBlank() }.associateWith {
                    mediaDao.byId(it)?.title ?: if (it.startsWith("local:")) {
                        dev.endlesssea.app.local.LocalVideos.seriesMeta[it.removePrefix("local:")]?.title
                            ?: dev.endlesssea.app.local.LocalNames.pretty(it.removePrefix("local:"))
                    } else it
                }
            }
        }
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
    val record by viewModel.recordHistory.collectAsState()
    var pending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Retour") }
            Text("Historique (${entries.size})", style = MaterialTheme.typography.titleLarge)
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
