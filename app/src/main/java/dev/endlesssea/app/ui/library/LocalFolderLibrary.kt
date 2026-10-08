@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.endlesssea.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.MediaCard

/** Folders are titles, files are episodes. A flat file grid is deliberately not offered. */
@Composable
internal fun LocalFolderLibrary(
    viewModel: LibraryViewModel, state: LibraryUiState, onFolder: (String) -> Unit,
    files: List<LocalVideoUi> = state.localFiles, categoryName: String? = null,
) {
    val context = LocalContext.current
    val dirs by viewModel.dirs.collectAsState()
    val categories by viewModel.customCategories.collectAsState()
    val categoryTick by viewModel.categoryItemsTick.collectAsState()
    var query by rememberSaveable(categoryName) { mutableStateOf("") }
    var removeDir by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .recoverCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.addLocalDir(uri.toString())
        }
    }
    val cards = viewModel.folderCards(files).filter { it.title.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Rechercher une série ou un dossier") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { picker.launch(null) }, label = { Text("Ajouter un dossier") })
            AssistChip(onClick = { viewModel.scanLocal() }, label = { Text("Actualiser") })
            dirs.forEach { dir ->
                InputChip(selected = false, onClick = { removeDir = dir },
                    label = { Text("Retirer : " + dev.endlesssea.app.local.LocalNames.pretty(dir)) })
            }
        }
        Text("${cards.size} titre(s) · Ouvrez une affiche pour retrouver ses épisodes et modifier sa fiche.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp))
        if (state.localScanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            Text(state.localScanLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp))
        }
        if (cards.isEmpty() && !state.localScanning) {
            Text(if (query.isNotBlank()) "Aucun titre correspondant."
                else "Aucun dossier vidéo ici. Ajoutez un dossier contenant vos séries ou vos films.",
                modifier = Modifier.padding(24.dp))
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(130.dp), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(cards, key = { it.id }) { card ->
                val folder = card.id.removePrefix("local-folder:")
                MediaCard(item = card, onClick = { onFolder(folder) }, onLongClick = { selectedFolder = folder })
            }
        }
    }
    removeDir?.let { dir ->
        AlertDialog(onDismissRequest = { removeDir = null }, title = { Text("Retirer ce dossier de la bibliothèque ?") },
            text = { Text("Les fichiers ne seront pas supprimés.") },
            confirmButton = { TextButton(onClick = { viewModel.removeLocalDir(dir); removeDir = null }) { Text("Retirer") } },
            dismissButton = { TextButton(onClick = { removeDir = null }) { Text("Annuler") } })
    }
    selectedFolder?.let { folder ->
        AlertDialog(onDismissRequest = { selectedFolder = null }, title = { Text("Gérer ce titre") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { selectedFolder = null; onFolder(folder) }) { Text("Modifier la fiche et l'affiche") }
                    Text("Catégories", style = MaterialTheme.typography.titleSmall)
                    if (categories.isEmpty()) Text("Créez une catégorie avec « + Catégorie » dans Dossiers.")
                    categories.forEach { category ->
                        val members = remember(categoryTick, category) { viewModel.categoryItems(category).toSet() }
                        val included = "folder:$folder" in members || files.any { it.parentUri == folder && it.uri in members }
                        TextButton(onClick = { viewModel.setFolderCategory(category, folder, !included) }) {
                            Text((if (included) "✓ " else "+ ") + category)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { selectedFolder = null }) { Text("Fermer") } })
    }
}
