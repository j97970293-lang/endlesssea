package dev.endlesssea.app.ui.extensions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.components.GlassCard
import dev.endlesssea.app.SafeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extensions (spec §18, doc 04) : dépôts (ajouter/synchroniser/activer/supprimer),
 * installation depuis les dépôts OU depuis un fichier .esx local, activation,
 * permissions visibles, journal d'erreurs. Toute action donne un retour visible.
 */
@Composable
fun ExtensionsScreen(
    onExplore: () -> Unit = {},
    viewModel: ExtensionsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val selectedLangs by viewModel.languages.collectAsState()
    var addDialog by remember { mutableStateOf(false) }
    var repoUrl by remember { mutableStateOf("") }

    // Retours visibles — « quand j'ajoute n'importe quoi, ça donne quelque chose »
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    // Sélecteur de fichier .esx
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val tmp = withContext(Dispatchers.IO) {
                    runCatching {
                        val out = File(context.cacheDir, "picked-${System.currentTimeMillis()}.esx")
                        context.contentResolver.openInputStream(uri)!!.use { input ->
                            out.outputStream().use { input.copyTo(it) }
                        }
                        out
                    }.getOrNull()
                }
                if (tmp != null) viewModel.installFromFile(tmp)
                else snackbar.showSnackbar("Impossible de lire ce fichier")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbar) },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                ) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Fichier .esx")
                }
                ExtendedFloatingActionButton(onClick = { addDialog = true }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Dépôt")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.busy) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
            }

            // --------------------------------------------------------- Dépôts
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dépôts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.syncAll() }) {
                        Icon(Icons.Filled.Refresh, "Synchroniser")
                    }
                }
            }
            if (state.repos.isEmpty()) {
                item {
                    GlassCard(contentPadding = PaddingValues(16.dp)) {
                        Column {
                            Text(
                                "Aucun dépôt configuré. Un dépôt est simplement une URL qui sert un index JSON listant des extensions.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = { viewModel.addRepo(ExtensionsViewModel.DEMO_REPO_URL) }) {
                                Text("Ajouter le dépôt de démonstration (Internet Archive)")
                            }
                        }
                    }
                }
            }
            items(state.repos, key = { it.url }) { repo ->
                GlassCard(contentPadding = PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // §4 — Icône du dépôt : SafeAsyncImage charge en parallèle,
                        // affiche la « vague » pendant le load, croix rouge si échec.
                        SafeAsyncImage(
                            url = repo.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.width(44.dp).height(44.dp),
                            placeholderModifier = Modifier.width(44.dp).height(44.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(repo.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                repo.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Switch(checked = repo.enabled, onCheckedChange = { viewModel.setRepoEnabled(repo.url, it) })
                        IconButton(onClick = { viewModel.removeRepo(repo.url) }) {
                            Icon(Icons.Filled.Close, "Supprimer", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // --------------------------------------- Extensions disponibles (dépôts)
            if (state.repoEntries.isNotEmpty()) {
                item { Text("Disponibles en ligne", style = MaterialTheme.typography.titleMedium) }
            val shownEntries = state.repoEntries.filter { e ->
                selectedLangs.isEmpty() ||
                    e.entry.languages.isEmpty() ||
                    e.entry.languages.any { l -> l.lowercase() in selectedLangs }
            }
            items(shownEntries, key = { "${it.repoUrl}|${it.entry.id}" }) { e ->
                val upToDate = e.installedVersion >= e.entry.version
                GlassCard(contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SafeAsyncImage(
                            url = e.entry.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.width(44.dp).height(44.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${e.entry.name} · v${e.entry.versionName}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${e.entry.languages.joinToString("/").ifBlank { "multi" }} · " +
                                    (e.entry.types.joinToString("/").ifBlank { "tous types" }) +
                                    if (e.entry.permissions.isNotEmpty()) " · ${e.entry.permissions.size} permission(s)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (upToDate) {
                            Text("Installée", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        } else {
                            Button(onClick = { viewModel.installEntry(e.entry) }) {
                                Text(if (e.installedVersion > 0) "Mettre à jour" else "Installer")
                            }
                        }
                    }
                }
            }
            }

            // --------------------------------------------- Extensions installées
            item { Text("Extensions installées", style = MaterialTheme.typography.titleMedium) }
            item {
                // §langues-extensions : ne garder que certaines langues de sources
                val langs = remember(state.repoEntries) {
                    state.repoEntries.flatMap { it.entry.languages }.map { it.lowercase() }
                        .distinct().sorted()
                }
                if (langs.isNotEmpty()) {
                    Column {
                        Text(
                            "Langues des sources",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            langs.forEach { l ->
                                androidx.compose.material3.FilterChip(
                                    selected = selectedLangs.isEmpty() || l in selectedLangs,
                                    onClick = { viewModel.toggleLanguage(l) },
                                    label = { Text(l.uppercase(), maxLines = 1, softWrap = false) },
                                )
                            }
                        }
                    }
                }
            }
            if (state.extensions.isEmpty()) {
                item {
                    Text(
                        "Rien pour l'instant. Installez depuis un dépôt (synchronisez-le d'abord) ou un fichier .esx.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.extensions, key = { it.pkg }) { ext ->
                GlassCard(contentPadding = PaddingValues(14.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SafeAsyncImage(
                                url = ext.iconUrl,
                                contentDescription = null,
                                modifier = Modifier.width(44.dp).height(44.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${ext.name} · v${ext.versionName}", style = MaterialTheme.typography.bodyLarge)
                                // §metadonnees-extensions : tout ce que le dépôt déclare
                                val meta = state.repoEntries.firstOrNull { it.entry.id == ext.pkg }?.entry
                                Text(
                                    listOfNotNull(
                                        meta?.author?.name?.takeIf { it.isNotBlank() }?.let { "par $it" },
                                        meta?.languages?.takeIf { it.isNotEmpty() }
                                            ?.joinToString("/") { l -> l.uppercase() },
                                        meta?.types?.takeIf { it.isNotEmpty() }
                                            ?.joinToString(", ") { t -> t.lowercase() },
                                        "API " + (meta?.apiVersion?.toString() ?: "1"),
                                        if (meta?.nsfw == true) "18+" else null,
                                    ).joinToString(" · ").ifBlank { "Aucune métadonnée publiée" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                meta?.description?.values?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { d ->
                                    Text(
                                        d,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    "Permissions : ${ext.permissions.ifEmpty { "aucune" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                ext.lastError?.let {
                                    Text(
                                        "Dernière erreur : $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Switch(checked = ext.enabled, onCheckedChange = { viewModel.setExtensionEnabled(ext.pkg, it) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onExplore, enabled = ext.enabled) { Text("Ouvrir") }
                            TextButton(
                                onClick = { viewModel.openExtSettings(ext.pkg, ext.name) },
                                enabled = ext.enabled,
                            ) { Text("Réglages") }
                            TextButton(onClick = { viewModel.uninstall(ext.pkg) }) {
                                Text("Désinstaller", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // Boîte de réglages d'extension (SWITCH = application immédiate, champs = « Enregistrer » par entrée)
    state.editingExt?.let { editing ->
        AlertDialog(
            onDismissRequest = { viewModel.closeExtSettings() },
            confirmButton = { TextButton(onClick = { viewModel.closeExtSettings() }) { Text("Fermer") } },
            title = { Text("Réglages — ${editing.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    editing.entries.forEach { entry ->
                        if (entry.type == dev.endlesssea.extensions.api.model.ExtensionSetting.Type.SWITCH) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                                    entry.summary?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = entry.value == "true",
                                    onCheckedChange = { viewModel.saveExtSetting(editing.pkg, entry.key, it.toString()) },
                                )
                            }
                        } else if (entry.type == dev.endlesssea.extensions.api.model.ExtensionSetting.Type.LIST &&
                            entry.options.isNotEmpty()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                                entry.summary?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    entry.options.forEach { opt ->
                                        androidx.compose.material3.FilterChip(
                                            selected = entry.value == opt,
                                            onClick = { viewModel.saveExtSetting(editing.pkg, entry.key, opt) },
                                            label = { Text(opt, maxLines = 1, softWrap = false) },
                                        )
                                    }
                                }
                            }
                        } else {
                            var text by androidx.compose.runtime.remember(entry.key) {
                                androidx.compose.runtime.mutableStateOf(entry.value)
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text(entry.title, maxLines = 1, softWrap = false) },
                                supportingText = entry.summary?.let { { Text(it) } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (entry.type == dev.endlesssea.extensions.api.model.ExtensionSetting.Type.PASSWORD)
                                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                                else androidx.compose.ui.text.input.VisualTransformation.None,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = { viewModel.saveExtSetting(editing.pkg, entry.key, text) },
                                    enabled = text != entry.value,
                                ) { Text("Enregistrer") }
                            }
                        }
                    }
                    Text(
                        "Pris en compte par la source à la prochaine ouverture de catalogue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("Ajouter un dépôt") },
            text = {
                Column {
                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        placeholder = { Text("https://exemple.com/index.json") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Astuce : pour tester, utilisez le dépôt de démonstration (bouton « Ajouter le dépôt de démonstration » sur l'écran vide).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addRepo(repoUrl)
                    repoUrl = ""
                    addDialog = false
                }) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Annuler") } },
        )
    }
}
