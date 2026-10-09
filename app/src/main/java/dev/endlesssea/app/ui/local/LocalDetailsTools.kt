package dev.endlesssea.app.ui.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.details.DetailsViewModel
import dev.endlesssea.app.ui.library.LibraryViewModel

/** Local-only edit actions, embedded in the common DetailsScreen's existing menu. */
@Composable
internal fun LocalDetailsTools(details: DetailsViewModel, mode: String, close: () -> Unit,
    library: LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val folder = details.localFolder ?: return
    val state by details.uiState.collectAsState()
    val media = state.details
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) library.importFolderCover(folder, uri.toString()).invokeOnCompletion { details.load() }
    }
    if (mode == "source") {
        LocalSourceMatchDialog(folder, media?.title.orEmpty(), details.localFiles,
            onDismiss = close, onApplied = { details.load() })
        return
    }
    val file = details.localFiles.firstOrNull { mode == "episode:${it.uri}" }
    var title by remember(mode) { mutableStateOf(file?.displayName ?: media?.title.orEmpty()) }
    var synopsis by remember(mode) { mutableStateOf(media?.synopsis.orEmpty()) }
    var author by remember(mode) { mutableStateOf(media?.studios?.firstOrNull().orEmpty()) }
    var genres by remember(mode) { mutableStateOf(media?.genres?.joinToString(", ").orEmpty()) }
    var introStart by remember(mode) { mutableStateOf(file?.introStartSec?.toString().orEmpty()) }
    var introEnd by remember(mode) { mutableStateOf(file?.introEndSec?.toString().orEmpty()) }
    var outro by remember(mode) { mutableStateOf(file?.outroStartSec?.toString().orEmpty()) }
    val validMarkers = listOf(introStart, introEnd, outro).all { it.isBlank() || (it.toIntOrNull()?.let { n -> n >= 0 } == true) } &&
        (introStart.isBlank() == introEnd.isBlank()) && (introEnd.isBlank() || (introEnd.toIntOrNull() ?: -1) > (introStart.toIntOrNull() ?: Int.MAX_VALUE))
    AlertDialog(onDismissRequest = close,
        title = { Text(if (file == null) "Métadonnées" else "Métadonnées de l'épisode") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Titre") })
            if (file == null) {
                OutlinedTextField(synopsis, { synopsis = it }, label = { Text("Synopsis") })
                OutlinedTextField(author, { author = it }, label = { Text("Auteur / studio") })
                OutlinedTextField(genres, { genres = it }, label = { Text("Genres séparés par une virgule") })
                TextButton(onClick = { coverPicker.launch(arrayOf("image/*")) }) { Text("Choisir l'affiche") }
            } else {
                Text("Marqueurs en secondes ; laisser vide pour désactiver.")
                OutlinedTextField(introStart, { introStart = it }, label = { Text("Début intro") })
                OutlinedTextField(introEnd, { introEnd = it }, label = { Text("Fin intro") })
                OutlinedTextField(outro, { outro = it }, label = { Text("Début générique de fin") })
                if (!validMarkers) Text("Vérifiez les secondes et l'ordre des marqueurs.", color = MaterialTheme.colorScheme.error)
                Text("Le fichier n'est pas renommé. Sa miniature reste extraite de la vidéo.")
            }
        } },
        confirmButton = { TextButton(enabled = file == null || validMarkers, onClick = {
            if (file == null) library.saveSeriesMeta(folder, title, synopsis, author,
                genres.split(',').map { it.trim() }.filter { it.isNotEmpty() }).invokeOnCompletion { details.load() }
            else {
                library.saveLocalMeta(file.uri, title.trim().ifBlank { null }, file.customCoverUri,
                    introStart.toIntOrNull(), introEnd.toIntOrNull(), outro.toIntOrNull())
                details.load()
            }
            close()
        }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = close) { Text("Annuler") } })
}
