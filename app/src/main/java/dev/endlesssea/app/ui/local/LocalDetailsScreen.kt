package dev.endlesssea.app.ui.local

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.local.LocalLibraryCache
import dev.endlesssea.app.local.LocalVideos
import dev.endlesssea.app.ui.components.GlassCard
import dev.endlesssea.app.ui.library.LibraryViewModel
import dev.endlesssea.app.ui.library.playLocal

/**
 * §fiche-locale : un dossier de vidéos locales s'ouvre comme une fiche de source.
 *
 * Même structure qu'une fiche d'extension : affiche, titre, description et
 * métadonnées (lues dans `details.json` à la façon d'Aniyomi), puis la liste des
 * épisodes. Les repères d'intro/outro réglés ici s'appliquent à TOUT le dossier.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalDetailsScreen(
    folderUri: String,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val cached by LocalLibraryCache.files.collectAsState()

    // La fiche fonctionne que l'on vienne de la bibliothèque (état chargé) ou
    // d'un lien direct (cache du dernier scan).
    val files = remember(state.localFiles, cached, folderUri, state.localMetaTick) {
        (state.localFiles.takeIf { it.isNotEmpty() } ?: cached)
            .filter { it.parentUri == folderUri }
            // §episodes-json : tri par NUMÉRO d'épisode (S01E02 avant S01E10),
            // puis par nom — au lieu du tri alphabétique « épisode 10, épisode 2 ».
            .sortedWith(
                compareBy(
                    { LocalVideos.episodeNumber(it.displayName) ?: Int.MAX_VALUE },
                    { it.displayName.lowercase() },
                ),
            )
    }
    val meta = LocalVideos.seriesMeta[folderUri]
    val first = files.firstOrNull()
    val title = meta?.title ?: first?.let {
        dev.endlesssea.app.local.LocalNames.pretty(folderUri)
    } ?: "Dossier"
    val cover = meta?.coverUri ?: files.firstOrNull { it.customCoverUri != null }?.customCoverUri
        ?: first?.uri

    // Repères communs au dossier : pré-remplis avec ce qui existe déjà.
    var introStart by remember(folderUri, files.size) {
        mutableStateOf(files.firstNotNullOfOrNull { it.introStartSec }?.toString() ?: "")
    }
    var introEnd by remember(folderUri, files.size) {
        mutableStateOf(files.firstNotNullOfOrNull { it.introEndSec }?.toString() ?: "")
    }
    var outroStart by remember(folderUri, files.size) {
        mutableStateOf(files.firstNotNullOfOrNull { it.outroStartSec }?.toString() ?: "")
    }
    var editTitle by remember { mutableStateOf(false) }
    var titleDraft by remember(title) { mutableStateOf(title) }
    /** §metadonnees-fichier : édition des métadonnées écrites dans details.json. */
    var editSeriesMeta by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ---- En-tête : affiche + métadonnées, comme une fiche de source
        item {
            Box(Modifier.fillMaxWidth()) {
                coil.compose.AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                )
                Box(
                    Modifier.fillMaxWidth().height(230.dp).background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
                )
                TextButton(onClick = onBack, modifier = Modifier.padding(6.dp)) { Text("‹ Retour") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                coil.compose.AsyncImage(
                    model = cover,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(110.dp).height(160.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 3)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${files.size} vidéo(s) · " +
                            LocalVideos.humanSize(files.sumOf { it.sizeBytes }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    meta?.author?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "Auteur : $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!meta?.genres.isNullOrEmpty()) {
                        Text(
                            meta!!.genres.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            files.firstOrNull()?.let { playLocal(context, files, it) }
                        }) { Text("Lire") }
                        TextButton(onClick = { editTitle = true }) { Text("Renommer") }
                        // §metadonnees-fichier : écrit details.json dans le dossier
                        TextButton(onClick = { editSeriesMeta = true }) { Text("Métadonnées") }
                    }
                }
            }
        }
        meta?.description?.takeIf { it.isNotBlank() }?.let { d ->
            item {
                dev.endlesssea.app.ui.components.ExpandableText(
                    text = d,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    dialogTitle = "Synopsis",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ---- Intro / outro valables pour TOUT le dossier
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                cornerRadius = 16.dp,
                contentPadding = PaddingValues(14.dp),
            ) {
                Column {
                    Text("Intro / Outro du dossier", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Appliqué à toutes les vidéos de ce dossier (en secondes).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = introStart,
                            onValueChange = { introStart = it.filter(Char::isDigit).take(5) },
                            label = { Text("Début intro") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = introEnd,
                            onValueChange = { introEnd = it.filter(Char::isDigit).take(5) },
                            label = { Text("Fin intro") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = outroStart,
                        onValueChange = { outroStart = it.filter(Char::isDigit).take(5) },
                        label = { Text("Début outro") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.saveFolderMeta(
                                folderUri,
                                null,
                                introStart.toIntOrNull(),
                                introEnd.toIntOrNull(),
                                outroStart.toIntOrNull(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Appliquer à tout le dossier") }
                }
            }
        }

        item {
            Text(
                "Épisodes",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(files, key = { it.uri }) { video ->
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                cornerRadius = 14.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { playLocal(context, files, video) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    coil.compose.AsyncImage(
                        model = video.customCoverUri ?: video.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(74.dp).height(44.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        // §episodes-json : « Ép. 3 · Le début » quand le dossier
                        // publie episodes.json, sinon le nom de fichier nettoyé.
                        val number = LocalVideos.episodeNumber(video.displayName)
                        val metaTitle = number?.let { meta?.episodeTitles?.get(it) }
                        val shownTitle = metaTitle
                            ?: LocalVideos.episodeTitleFromFileName(video.displayName)
                                .ifBlank { video.displayName }
                        Text(
                            if (number != null) "Ép. $number · $shownTitle" else shownTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            LocalVideos.humanSize(video.sizeBytes) +
                                (video.durationMs?.let { " · " + formatDuration(it) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("▶", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    // §metadonnees-fichier : les métadonnées de la série sont écrites DANS le
    // dossier (details.json) — elles suivent donc la carte SD, pas l'application.
    if (editSeriesMeta) {
        var metaTitle by remember(title) { mutableStateOf(meta?.title ?: title) }
        var metaDescription by remember { mutableStateOf(meta?.description ?: "") }
        var metaAuthor by remember { mutableStateOf(meta?.author ?: "") }
        var metaGenres by remember { mutableStateOf(meta?.genres?.joinToString(", ") ?: "") }

        AlertDialog(
            onDismissRequest = { editSeriesMeta = false },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveSeriesMeta(
                        folderUri = folderUri,
                        title = metaTitle,
                        description = metaDescription.ifBlank { null },
                        author = metaAuthor.ifBlank { null },
                        genres = metaGenres.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    )
                    // Le titre de la série s'applique aussi aux fichiers sans titre
                    // perso, pour que la grille de la bibliothèque suive le dossier.
                    files.filter { it.customTitle == null }.forEach { f ->
                        viewModel.saveLocalMeta(
                            f.uri, metaTitle.takeIf { it.isNotBlank() }, f.customCoverUri,
                            f.introStartSec, f.introEndSec, f.outroStartSec,
                        )
                    }
                    editSeriesMeta = false
                }) { Text("Enregistrer dans le dossier") }
            },
            dismissButton = {
                TextButton(onClick = { editSeriesMeta = false }) { Text("Annuler") }
            },
            title = { Text("Métadonnées de la série") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Écrites dans « details.json », à côté des vidéos (format Aniyomi).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = metaTitle,
                        onValueChange = { metaTitle = it },
                        label = { Text("Titre de la série") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = metaDescription,
                        onValueChange = { metaDescription = it },
                        label = { Text("Synopsis") },
                        minLines = 3,
                    )
                    OutlinedTextField(
                        value = metaAuthor,
                        onValueChange = { metaAuthor = it },
                        label = { Text("Auteur / studio") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = metaGenres,
                        onValueChange = { metaGenres = it },
                        label = { Text("Genres (séparés par des virgules)") },
                        singleLine = true,
                    )
                }
            },
        )
    }

    if (editTitle) {
        AlertDialog(
            onDismissRequest = { editTitle = false },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveFolderMeta(folderUri, null, null, null, null)
                    files.forEach { f ->
                        viewModel.saveLocalMeta(
                            f.uri, titleDraft, f.customCoverUri,
                            f.introStartSec, f.introEndSec, f.outroStartSec,
                        )
                    }
                    editTitle = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editTitle = false }) { Text("Annuler") } },
            title = { Text("Nom de la série") },
            text = {
                OutlinedTextField(
                    value = titleDraft,
                    onValueChange = { titleDraft = it },
                    singleLine = true,
                )
            },
        )
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "${m} min"
}
