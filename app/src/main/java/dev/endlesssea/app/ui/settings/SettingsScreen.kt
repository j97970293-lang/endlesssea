package dev.endlesssea.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.app.navigation.allTabScreens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Paramètres — chaque entrée est fonctionnelle et persistée :
 * stockage (SAF), moteur de téléchargement, lecteur, thème (AMOLED inclus),
 * contenu de la barre flottante, genres (ajout/renommage/réordonnancement),
 * sauvegarde export/import.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }

    var showNumbersDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showGenreManager by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    // SAF : choix de l'emplacement des téléchargements (persistance longue durée incluse)
    val storagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.onStorageChosen(it.toString())
        } ?: viewModel.onStorageChosen(null)
    }

    // Sauvegarde : export (créer un fichier) + import (ouvrir un fichier)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.buildBackupJson()
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)!!
                            .use { it.write(json.toByteArray()) }
                    }.isSuccess
                }
                viewModel.onBackupExported(ok)
            }
        } else viewModel.onBackupExported(false)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }.getOrNull()
                }
                if (text != null) viewModel.onBackupImported(viewModel.restoreBackup(text))
                else viewModel.onBackupImported("Impossible de lire ce fichier")
            }
        }
    }

    androidx.compose.foundation.layout.Box {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp),
        ) {
            // ------------------------------------------------------ APPARENCE
            item { SectionHeader("Apparence") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Thème", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            AppPrefs.THEME_SYSTEM to "Système",
                            AppPrefs.THEME_LIGHT to "Clair",
                            AppPrefs.THEME_DARK to "Sombre",
                            AppPrefs.THEME_AMOLED to "AMOLED",
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (state.themeMode == AppPrefs.THEME_AMOLED) {
                        Text(
                            "Noir pur — parfait pour les écrans OLED/AMOLED",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    // Couleur d'accent (boutons, onglets actifs, badges)
                    Text("Couleur d'accent", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppPrefs.ACCENTS.forEach { (name, argb) ->
                            val selected = state.accent == name
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { viewModel.setAccent(name) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(36.dp)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = CircleShape,
                                        )
                                        .padding(3.dp)
                                        .background(androidx.compose.ui.graphics.Color(argb), CircleShape),
                                )
                                Text(
                                    name.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Liquid Mode (verre)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Opacité : ${state.glassOverlay} %   ·   intensité du flou : ${state.glassScrim} %",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = state.glassOverlay.toFloat(),
                        onValueChange = { viewModel.setGlassOverlay(it.toInt()) },
                        valueRange = 0f..30f,
                    )
                    Slider(
                        value = state.glassScrim.toFloat(),
                        onValueChange = { viewModel.setGlassScrim(it.toInt()) },
                        valueRange = 0f..100f,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Style des cartes", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("detail" to "Détaillé", "poster" to "Poster seul", "minimal" to "Minimal")
                            .forEach { (key, label) ->
                                FilterChip(
                                    selected = state.cardStyle == key,
                                    onClick = { viewModel.setCardStyle(key) },
                                    label = { Text(label) },
                                )
                            }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Barre de navigation flottante", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Choisissez les onglets affichés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    state.tabOrder.mapNotNull { route -> allTabScreens.find { it.route == route } }
                        .forEach { screen ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(screen.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Text(screen.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                // Ordre : monter / descendre dans la barre
                                IconButton(onClick = { viewModel.moveTab(screen.route, -1) }) {
                                    Icon(Icons.Filled.KeyboardArrowUp, "Monter")
                                }
                                IconButton(onClick = { viewModel.moveTab(screen.route, 1) }) {
                                    Icon(Icons.Filled.KeyboardArrowDown, "Descendre")
                                }
                                Switch(
                                    checked = screen.route in state.barTabs,
                                    onCheckedChange = { viewModel.setBarTab(screen.route, it) },
                                )
                            }
                        }
                    Text(
                        "Marge de la barre : ${state.barMargin} dp",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Slider(
                        value = state.barMargin.toFloat(),
                        onValueChange = { viewModel.setBarMargin(it.toInt()) },
                        valueRange = 0f..64f,
                    )
                }
            }

            // ------------------------------------------------------- STOCKAGE
            item { SectionHeader("Stockage") }
            item {
                SettingRow(
                    title = "Emplacement des téléchargements",
                    subtitle = state.storageUri?.let { "Dossier choisi (SAF) : ${it.substringAfterLast("/")}" }
                        ?: "Automatique : stockage privé de l'app (EndlessSea/)",
                    onClick = { storagePicker.launch(null) },
                )
            }

            // --------------------------------------------------- TÉLÉCHARGEMENT
            item { SectionHeader("Téléchargement") }
            item {
                SettingSwitch(
                    title = "Wi-Fi uniquement",
                    subtitle = "Suspendre la file sur données mobiles",
                    checked = state.wifiOnly,
                    onChange = viewModel::setWifiOnly,
                )
            }
            item {
                SettingRow(
                    title = "Connexions",
                    subtitle = "${state.partsPerTask} segments par fichier · ${state.parallelTasks} tâche(s) simultanée(s)",
                    onClick = { showNumbersDialog = true },
                )
            }

            // --------------------------------------------------------- LECTEUR
            item { SectionHeader("Lecteur") }
            item {
                SettingRow(
                    title = "Vitesse par défaut",
                    subtitle = "${state.defaultSpeed}×",
                    onClick = { showSpeedDialog = true },
                )
            }
            item {
                SettingSwitch(
                    title = "Reprise automatique",
                    subtitle = "Reprendre là où j'en étais (position mémorisée)",
                    checked = state.autoResume,
                    onChange = viewModel::setAutoResume,
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Saut du double appui (lecteur)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Appui double à gauche/droite : ±${state.skipSeconds} s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30, 85).forEach { sec ->
                            FilterChip(
                                selected = state.skipSeconds == sec,
                                onClick = { viewModel.setSkipSeconds(sec) },
                                label = { Text("${sec}s") },
                            )
                        }
                    }
                    Text(
                        "Filtres vidéo (luminosité/teinte/saturation) : réglés dans le lecteur, bouton 🎛, avec préréglages sauvegardés.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // ---------------------------------------------------------- GENRES
            item { SectionHeader("Genres") }
            item {
                SettingRow(
                    title = "Gérer les genres (${state.genres.size})",
                    subtitle = "ajouter · renommer · réordonner · afficher/masquer · supprimer",
                    onClick = { showGenreManager = true },
                )
            }

            // -------------------------------------- RÉGLAGES PAR EXTENSION
            item { SectionHeader("Extensions — réglages par source") }
            item {
                if (state.extWithSettings.isEmpty()) {
                    Text(
                        "Aucune extension activée. Les réglages déclarés par une source (adresse du site, options…) apparaissent ici.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                } else {
                    Column {
                        state.extWithSettings.forEach { (pkg, name) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { viewModel.openExtSettings(pkg, name) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Extension, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodyLarge)
                                    Text("Options de la source", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (state.extSettingsLoading) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) { androidx.compose.material3.CircularProgressIndicator(Modifier.width(28.dp).height(28.dp), strokeWidth = 2.dp) }
                        }
                    }
                }
            }

            // ------------------------------------------------------- SAUVEGARDE
            item { SectionHeader("Sauvegarde") }
            item {
                SettingSwitch(
                    title = "Sauvegarde automatique locale",
                    subtitle = "Chaque jour : bibliothèque + historique + favoris → endlesssea-backup-AAAAMMJJ.json " +
                        "(dossier privé de l'app)" +
                        if (state.lastBackupAt > 0)
                            "\nDernière : ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE).format(java.util.Date(state.lastBackupAt))}"
                        else "",
                    checked = state.autoBackup,
                    onChange = viewModel::setAutoBackup,
                )
            }
            item {
                SettingRow(
                    title = "Exporter la bibliothèque + historique",
                    subtitle = "fichier JSON dans le dossier de votre choix",
                    onClick = { exportLauncher.launch("endless-sea-backup.json") },
                )
            }
            item {
                SettingRow(
                    title = "Importer une sauvegarde",
                    subtitle = "restaure bibliothèque, favoris et positions de lecture",
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                )
            }

            // --------------------------------------------------- MISES À JOUR
            item { SectionHeader("Mises à jour de l'application") }
            item {
                SettingSwitch(
                    title = "Mise à jour automatique",
                    subtitle = "Avertir à l'ouverture quand une nouvelle version est publiée",
                    checked = state.updateAutoCheck,
                    onChange = viewModel::setUpdateAutoCheck,
                )
            }
            item {
                SettingRow(
                    title = if (state.updateChecking) "Vérification en cours…" else "Vérifier maintenant",
                    subtitle = "Dernière release publiée sur GitHub",
                    onClick = { if (!state.updateChecking) viewModel.checkForUpdate() },
                )
            }

            // --------------------------------------------------------- À PROPOS
            item { SectionHeader("À propos") }
            item {
                SettingRow(
                    title = "Endless Sea 0.3.0",
                    subtitle = "GPL-3.0 · aucune source incluse · github.com/j97970293-lang/endlesssea",
                    onClick = { },
                )
            }
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // -------------------------------------------------- Dialogue connexions
    if (showNumbersDialog) {
        val segments = listOf(1, 2, 4, 8, 16)
        val tasks = listOf(1, 2, 3, 4)
        AlertDialog(
            onDismissRequest = { showNumbersDialog = false },
            confirmButton = { TextButton(onClick = { showNumbersDialog = false }) { Text("Fermer") } },
            title = { Text("Connexions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Segments par fichier", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        segments.forEach { n ->
                            FilterChip(
                                selected = state.partsPerTask == n,
                                onClick = { viewModel.setPartsPerTask(n) },
                                label = { Text("$n") },
                            )
                        }
                    }
                    Text("Tâches simultanées", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tasks.forEach { n ->
                            FilterChip(
                                selected = state.parallelTasks == n,
                                onClick = { viewModel.setParallelTasks(n) },
                                label = { Text("$n") },
                            )
                        }
                    }
                }
            },
        )
    }

    // ---------------------------------------- Réglages d'une extension
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
                        } else {
                            var text by remember(entry.key) { mutableStateOf(entry.value) }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text(entry.title) },
                                supportingText = entry.summary?.let { { Text(it) } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (entry.type == dev.endlesssea.extensions.api.model.ExtensionSetting.Type.PASSWORD)
                                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                                else androidx.compose.ui.text.input.VisualTransformation.None,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
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

    // ------------------------------------------------- Mise à jour trouvée
    state.availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdate() },
            confirmButton = {
                Button(onClick = {
                    dev.endlesssea.app.update.AppUpdateInstaller.download(context, update)
                    viewModel.dismissUpdate()
                }) { Text("Télécharger") }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissUpdate() }) { Text("Plus tard") } },
            title = { Text("Mise à jour ${update.tag}") },
            text = {
                Text(
                    update.notes.take(600).ifBlank { "Nouvelle version disponible sur GitHub Releases." },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }

    // ------------------------------------------------------ Dialogue vitesse
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            confirmButton = { TextButton(onClick = { showSpeedDialog = false }) { Text("Fermer") } },
            title = { Text("Vitesse de lecture par défaut") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        FilterChip(
                            selected = state.defaultSpeed == s,
                            onClick = { viewModel.setDefaultSpeed(s) },
                            label = { Text("${s}×") },
                        )
                    }
                }
            },
        )
    }

    // ----------------------------------------------------- Gestion des genres
    if (showGenreManager) {
        GenreManagerDialog(state.genres, viewModel) { showGenreManager = false }
    }
}

@Composable
private fun GenreManagerDialog(
    genres: List<GenreUi>,
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
) {
    var newGenre by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<GenreUi?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Terminé") } },
        title = { Text("Genres") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newGenre,
                        onValueChange = { newGenre = it },
                        placeholder = { Text("Nouveau genre…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.addGenre(newGenre); newGenre = "" }) {
                        Icon(Icons.Filled.Add, "Ajouter")
                    }
                }
                LazyColumn(Modifier.height(260.dp)) {
                    items(genres, key = { it.id }) { genre ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { viewModel.moveGenre(genre.id, -1) }) {
                                Icon(Icons.Filled.KeyboardArrowUp, "Monter")
                            }
                            IconButton(onClick = { viewModel.moveGenre(genre.id, +1) }) {
                                Icon(Icons.Filled.KeyboardArrowDown, "Descendre")
                            }
                            Column(Modifier.weight(1f).clickable { renameTarget = genre; renameText = genre.name }) {
                                Text(genre.name, style = MaterialTheme.typography.bodyMedium)
                                Text("visible · position ${genre.position}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = genre.visible, onCheckedChange = { viewModel.toggleGenre(genre.id, it) })
                            IconButton(onClick = { viewModel.deleteGenre(genre.id) }) {
                                Icon(Icons.Filled.Clear, "Supprimer", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
    )

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            confirmButton = {
                Button(onClick = { viewModel.renameGenre(target.id, renameText); renameTarget = null }) {
                    Text("Renommer")
                }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Annuler") } },
            title = { Text("Renommer le genre") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    HorizontalDivider()
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
