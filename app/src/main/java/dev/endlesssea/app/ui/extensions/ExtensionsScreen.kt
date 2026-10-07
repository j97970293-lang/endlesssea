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
import androidx.compose.material.icons.filled.Search
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

    // ---- §extensions-onglets (conversation 8) : « Installées », « Non installées »
    // et « Dépôts » ne sont PLUS dans le même panier — chacun a son onglet et
    // SES filtres (langue, type, statut, mises à jour, recherche).
    var tab by remember { mutableStateOf(0) }
    var installedFilter by remember { mutableStateOf("ALL") }   // ALL | ON | OFF | UPDATE
    var entryFilter by remember { mutableStateOf("ALL") }       // ALL | UPDATE
    var repoFilter by remember { mutableStateOf("ALL") }        // ALL | ON | OFF
    var query by remember { mutableStateOf("") }
    /** Langues/type choisis pour l'onglet « Non installées » (vides = tout). */
    var entryLangs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var entryTypes by remember { mutableStateOf<Set<String>>(emptySet()) }

    val installedList = state.extensions
    val availableList = state.repoEntries
    val updatesCount = installedList.count { ext ->
        val entry = availableList.firstOrNull { it.entry.id == ext.pkg }?.entry
        entry != null && entry.version > ext.version
    }
    val filteredInstalled = installedList
        .filter { ext ->
            when (installedFilter) {
                "ON" -> ext.enabled
                "OFF" -> !ext.enabled
                "UPDATE" -> {
                    val entry = availableList.firstOrNull { it.entry.id == ext.pkg }?.entry
                    entry != null && entry.version > ext.version
                }
                else -> true
            }
        }
        .filter { ext ->
            query.isBlank() || ext.name.contains(query, true) || ext.pkg.contains(query, true)
        }
    val filteredEntries = availableList
        .filter { e ->
            val installed = installedList.any { x -> x.pkg == e.entry.id }
            val updatable = e.installedVersion in 1 until e.entry.version
            (!installed || updatable) &&
                (entryFilter != "UPDATE" || updatable) &&
                (entryLangs.isEmpty() || e.entry.languages.isEmpty() ||
                    e.entry.languages.any { l -> l.lowercase() in entryLangs }) &&
                (entryTypes.isEmpty() || e.entry.types.any { t -> t.lowercase() in entryTypes }) &&
                (query.isBlank() || e.entry.name.contains(query, true) ||
                    (e.entry.description.values.firstOrNull()?.contains(query, true) ?: false)) &&
                (selectedLangs.isEmpty() || e.entry.languages.isEmpty() ||
                    e.entry.languages.any { l -> l.lowercase() in selectedLangs })
        }
    val filteredRepos = state.repos.filter {
        when (repoFilter) {
            "ON" -> it.enabled
            "OFF" -> !it.enabled
            else -> true
        }
    }.filter { query.isBlank() || it.name.contains(query, true) || it.url.contains(query, true) }

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

            // ------------------------------------------------- Onglets + recherche
            item {
                Column(Modifier.fillMaxWidth()) {
                    androidx.compose.material3.TabRow(selectedTabIndex = tab) {
                        listOf(
                            "Installées" to installedList.size,
                            "Non installées" to filteredEntries.size,
                            "Dépôts" to state.repos.size,
                        ).forEachIndexed { index, (label, count) ->
                            androidx.compose.material3.Tab(
                                selected = tab == index,
                                onClick = { tab = index; query = "" },
                                text = {
                                    Text(
                                        if (count > 0) "$label ($count)" else label,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                when (tab) {
                                    0 -> "Filtrer mes extensions…"
                                    1 -> "Filtrer les extensions disponibles…"
                                    else -> "Filtrer les dépôts…"
                                },
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Effacer") }
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    // Filtres propres à l'onglet courant
                    Row(
                        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (tab) {
                            0 -> listOf(
                                "ALL" to "Toutes",
                                "ON" to "Actives",
                                "OFF" to "Inactives",
                                "UPDATE" to "Mises à jour ($updatesCount)",
                            ).forEach { (key, label) ->
                                androidx.compose.material3.FilterChip(
                                    selected = installedFilter == key,
                                    onClick = { installedFilter = key },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                )
                            }
                            1 -> {
                                androidx.compose.material3.FilterChip(
                                    selected = entryFilter == "UPDATE",
                                    onClick = { entryFilter = if (entryFilter == "UPDATE") "ALL" else "UPDATE" },
                                    label = { Text("Mises à jour", maxLines = 1, softWrap = false) },
                                )
                                val langs = availableList.flatMap { it.entry.languages }
                                    .map { it.lowercase() }.distinct().sorted()
                                langs.forEach { l ->
                                    androidx.compose.material3.FilterChip(
                                        selected = l in entryLangs,
                                        onClick = {
                                            entryLangs = if (l in entryLangs) entryLangs - l else entryLangs + l
                                        },
                                        label = { Text(l.uppercase(), maxLines = 1, softWrap = false) },
                                    )
                                }
                                val types = availableList.flatMap { it.entry.types }
                                    .map { it.lowercase() }.distinct().sorted()
                                types.forEach { t ->
                                    androidx.compose.material3.FilterChip(
                                        selected = t in entryTypes,
                                        onClick = {
                                            entryTypes = if (t in entryTypes) entryTypes - t else entryTypes + t
                                        },
                                        label = { Text(t, maxLines = 1, softWrap = false) },
                                    )
                                }
                            }
                            else -> listOf(
                                "ALL" to "Tous",
                                "ON" to "Activés",
                                "OFF" to "Désactivés",
                            ).forEach { (key, label) ->
                                androidx.compose.material3.FilterChip(
                                    selected = repoFilter == key,
                                    onClick = { repoFilter = key },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                )
                            }
                        }
                    }
                }
            }

            // --------------------------------------------------------- Dépôts
            if (tab == 2) {
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
            items(filteredRepos, key = { it.url }) { repo ->
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

            }   // ---- fin de l'onglet « Dépôts »

            // --------------------------------------- Extensions disponibles (dépôts)
            if (tab == 1) {
            item {
                Text(
                    "Disponibles (non installées)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (filteredEntries.isEmpty()) {
                item {
                    Text(
                        if (availableList.isEmpty()) {
                            "Aucune extension disponible : ajoutez et synchronisez un dépôt (onglet « Dépôts »)."
                        } else {
                            "Aucun résultat avec ces filtres."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(filteredEntries, key = { "${it.repoUrl}|${it.entry.id}" }) { e ->
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
            if (tab == 0) {
            item { Text("Extensions installées", style = MaterialTheme.typography.titleMedium) }
            if (filteredInstalled.isEmpty()) {
                item {
                    Text(
                        if (installedList.isEmpty()) {
                            "Rien pour l'instant. Installez depuis un dépôt (synchronisez-le d'abord) ou un fichier .esx."
                        } else {
                            "Aucune extension ne correspond à ce filtre."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(filteredInstalled, key = { it.pkg }) { ext ->
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
            }   // ---- fin de l'onglet « Installées »
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
