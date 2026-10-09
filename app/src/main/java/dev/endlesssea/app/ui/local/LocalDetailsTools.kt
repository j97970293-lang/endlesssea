package dev.endlesssea.app.ui.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.local.LocalMediaType
import dev.endlesssea.app.local.SeriesSkipMarkers
import dev.endlesssea.app.ui.details.DetailsViewModel
import dev.endlesssea.app.ui.library.LibraryViewModel

/** Local-only edit actions, embedded in the common DetailsScreen's existing menu. */
@Composable
internal fun LocalDetailsTools(
    details: DetailsViewModel,
    mode: String,
    close: () -> Unit,
    library: LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val folder = details.localFolder ?: return
    val state by details.uiState.collectAsState()
    val media = state.details
    val seriesMeta = library.folderMetadata(folder)
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
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
    var mediaType by remember(mode, seriesMeta.mediaType, media?.type) {
        mutableStateOf(LocalMediaType.normalize(seriesMeta.mediaType ?: media?.type?.name))
    }
    var introStart by remember(mode, seriesMeta.introStartSec) {
        mutableStateOf((if (file == null) seriesMeta.introStartSec else file.episodeIntroStartSec)?.toString().orEmpty())
    }
    var introEnd by remember(mode, seriesMeta.introEndSec) {
        mutableStateOf((if (file == null) seriesMeta.introEndSec else file.episodeIntroEndSec)?.toString().orEmpty())
    }
    var outro by remember(mode, seriesMeta.outroStartSec) {
        mutableStateOf((if (file == null) seriesMeta.outroStartSec else file.episodeOutroStartSec)?.toString().orEmpty())
    }
    val validMarkers = listOf(introStart, introEnd, outro).all {
        it.isBlank() || (it.toIntOrNull()?.let { seconds -> seconds >= 0 } == true)
    } && (introStart.isBlank() == introEnd.isBlank()) &&
        (introEnd.isBlank() || (introEnd.toIntOrNull() ?: -1) > (introStart.toIntOrNull() ?: Int.MAX_VALUE))
    val inheritedLabel = listOfNotNull(
        file?.introStartSec?.let { start ->
            "Intro $start s" + (file?.introEndSec?.let { end -> "–$end s" } ?: "")
        },
        file?.outroStartSec?.let { "Outro dès $it s" },
    ).joinToString(" · ").ifBlank { "aucun repère de série" }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (file == null) "Métadonnées de la série" else "Métadonnées de l'épisode") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Titre") })
                if (file == null) {
                    OutlinedTextField(synopsis, { synopsis = it }, label = { Text("Synopsis") })
                    OutlinedTextField(author, { author = it }, label = { Text("Auteur / studio") })
                    OutlinedTextField(genres, { genres = it }, label = { Text("Genres séparés par une virgule") })
                    Text("Type de média", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LocalMediaType.options.forEach { option ->
                            FilterChip(selected = mediaType == option.first,
                                onClick = { mediaType = option.first }, label = { Text(option.second) })
                        }
                    }
                    TextButton(onClick = { coverPicker.launch(arrayOf("image/*")) }) { Text("Choisir l'affiche") }
                    Text("Repères par défaut de la série (secondes). Un repère vide est désactivé.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Repères propres à l'épisode (secondes). Laissez vide pour hériter de la série.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Héritage actuel : $inheritedLabel", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(introStart, { introStart = it.filter(Char::isDigit).take(5) },
                    label = { Text("Début intro") }, singleLine = true)
                OutlinedTextField(introEnd, { introEnd = it.filter(Char::isDigit).take(5) },
                    label = { Text("Fin intro") }, singleLine = true)
                OutlinedTextField(outro, { outro = it.filter(Char::isDigit).take(5) },
                    label = { Text("Début générique de fin") }, singleLine = true)
                if (!validMarkers) Text("Vérifiez les secondes et l'ordre des marqueurs.",
                    color = MaterialTheme.colorScheme.error)
                if (file != null) Text("Le fichier n'est pas renommé. Sa miniature reste extraite de la vidéo.")
            }
        },
        confirmButton = {
            TextButton(enabled = validMarkers, onClick = {
                if (file == null) {
                    library.saveSeriesMeta(
                        folder, title, synopsis, author,
                        genres.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        markerDefaults = SeriesSkipMarkers(introStart.toIntOrNull(), introEnd.toIntOrNull(), outro.toIntOrNull()),
                        mediaType = mediaType,
                    ).invokeOnCompletion { details.load() }
                } else {
                    library.saveLocalMeta(file.uri, title.trim().ifBlank { null }, file.customCoverUri,
                        introStart.toIntOrNull(), introEnd.toIntOrNull(), outro.toIntOrNull())
                    details.load()
                }
                close()
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Annuler") } },
    )
}
