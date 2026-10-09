package dev.endlesssea.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Extension
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import dev.endlesssea.app.ui.motion.motionClickable
import dev.endlesssea.app.ui.motion.motionReveal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material.icons.filled.ArrowBack

/**
 * Paramètres — chaque entrée est fonctionnelle et persistée :
 * stockage (SAF), moteur de téléchargement, lecteur, thème (AMOLED inclus),
 * contenu de la barre flottante, genres (ajout/renommage/réordonnancement),
 * sauvegarde export/import.
 */
@Composable
fun SettingsScreen(
    /** §extensions-déplacées : l'icône de la barre du haut a cédé la place au
     *  sélecteur de fournisseur — on ouvre la gestion des extensions d'ici. */
    onOpenExtensions: () -> Unit = {},
    /** §suivi (conversation 11) : écran « Comptes & suivi ». */
    onOpenTrackers: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }

    // §réglages-recherche : filtre instantané sur le titre et le sous-titre
    var settingsQuery by remember { mutableStateOf("") }
    var showNumbersDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showGenreManager by remember { mutableStateOf(false) }
    /** §megaskip : éditeur des boutons de saut personnalisés. */
    var showSkipDialog by remember { mutableStateOf(false) }
    /** §réglages-pleine-page : page d'accueil = liste des catégories ;
     *  un tap ouvre la catégorie EN PLEINE PAGE (plus de pile dépliée, plus de barres). */
    var openCategoryFull: String? by remember { mutableStateOf(null) }
    val openCategories = if (openCategoryFull == null) emptySet() else setOf(openCategoryFull!!)
    fun toggleCategory(key: String) {
        openCategoryFull = if (openCategoryFull == key) null else key
    }

    // Image de fond personnalisée (persistée longue durée)
    val bgImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.setBgImage(it.toString())
        }
    }

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

    // ViewModel state survives Activity recreation (including imported theme changes).
    val backupState by viewModel.backupState.collectAsState()
    val backupBusy = backupState.busy
    LaunchedEffect(backupState.message) {
        backupState.message?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.clearBackupMessage(message)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.previewBackup(uri)
    }
    backupState.pending?.let { snapshot ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupPreview,
            title = { Text("Fusionner cette sauvegarde ?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${snapshot.library.size} entrée(s) de bibliothèque · ${snapshot.history.size} épisode(s) dans l'historique\n" +
                        "${snapshot.media.size} fiche(s) · ${snapshot.customCategories.size} catégorie(s) personnelle(s) · ${snapshot.localMetadata.size} fichier(s) annoté(s).")
                    Spacer(Modifier.height(12.dp))
                    Text("Les fiches et annotations déjà présentes sont conservées. Pour la progression, la date la plus récente gagne. Aucun élément existant n'est supprimé.")
                    Spacer(Modifier.height(8.dp))
                    Text("Les vidéos, images et jetons de connexion ne sont pas inclus. Sur un autre appareil, réautorise les dossiers et réinstalle les extensions.")
                    if (snapshot.preferences != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = backupState.restorePreferences, enabled = !backupBusy, onCheckedChange = viewModel::setBackupRestorePreferences)
                        Text("Importer aussi les réglages inclus (apparence, lecteur et enregistrement de l'historique)")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !backupBusy, onClick = viewModel::confirmBackupRestore) {
                    Text(if (backupBusy) "Fusion en cours…" else "Fusionner")
                }
            },
            dismissButton = { TextButton(enabled = !backupBusy, onClick = viewModel::dismissBackupPreview) { Text("Annuler") } },
        )
    }

    // Retour système sur la page de catégorie → revient à la liste (§réglages-pleine-page).
    androidx.activity.compose.BackHandler(enabled = openCategoryFull != null) {
        openCategoryFull = null
    }

    androidx.compose.foundation.layout.Box {
        LazyColumn(
            modifier = Modifier.fillMaxSize().motionReveal(openCategoryFull),
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp),
        ) {
            // Entête « ← Paramètres » quand on est DANS une catégorie.
            if (openCategoryFull != null) {
                item {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { openCategoryFull = null }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ArrowBack, "Retour", tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Paramètres",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            // §réglages-recherche : une barre de recherche en haut de la page
            if (openCategoryFull == null) {
                item {
                    androidx.compose.material3.OutlinedTextField(
                        value = settingsQuery,
                        onValueChange = { settingsQuery = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (settingsQuery.isNotEmpty()) {
                                IconButton(onClick = { settingsQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Effacer")
                                }
                            }
                        },
                        placeholder = { Text("Rechercher un réglage…") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            if (openCategoryFull == null && matchesQuery(settingsQuery, "Historique", "Supprimer les vidéos vues")) item {
                SettingCategory(Icons.Filled.Delete, "Historique", "Consulter, effacer ou désactiver l'historique", expanded = false) { onOpenHistory() }
            }
            // ------------------------------------------------------ APPARENCE
            if (openCategoryFull == null && settingsQuery.isBlank()) {
                item { GroupTitle("Apparence & interface") }
            }
            // §anymex-ui : deux entrées distinctes comme AnyMEX — « Interface » (mise
            // en page, styles de cartes, multiplicateurs) et « Thème » (couleurs, verre).
            if (openCategoryFull == null && matchesQuery(settingsQuery, "Extensions installées", "Ajouter un dépôt, activer, mettre à jour, supprimer")) item {
                SettingCategory(Icons.Filled.Extension, "Extensions installées",
                    "Ajouter un dépôt, activer, mettre à jour, supprimer", expanded = false) { onOpenExtensions() }
            }
            // §suivi (conversation 11) : AniList, MyAnimeList, Shikimori, TMDB
            if (openCategoryFull == null && matchesQuery(settingsQuery, "Comptes & suivi", "AniList, MyAnimeList, Shikimori, TMDB, épisodes vus, bandes-annonces")) item {
                SettingCategory(Icons.Filled.Person, "Comptes & suivi",
                    "Synchroniser les épisodes vus, récupérer affiches et bandes-annonces",
                    expanded = false) { onOpenTrackers() }
            }
            if (openCategoryFull == null && matchesQuery(settingsQuery, "Interface", "Styles de cartes, carrousel, barre, arrondis, halos, logo, icône")) item {
                SettingCategory(Icons.Filled.Dashboard, "Interface et thème",
                    "Couleurs, luminosité, cartes, carrousel, barre, arrondis, police, fond",
                    expanded = "ui" in openCategories) { toggleCategory("ui") }
            }
            if ("ui" in openCategories) {
                item { UiSettingsSection(viewModel, state) }
                item { LogoSettingsSection(viewModel.appLogo.collectAsState().value, viewModel::setAppLogo) }
            }
            // §fusion-reglages : « Thème » n'est plus une page séparée — tout
            // est dans « Interface » (une seule page d'apparence).
            if ("interface" in openCategories || "ui" in openCategories) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    // §luminosite : « l'interface est trop sombre »
                    val brightness = viewModel.uiBrightness.collectAsState().value
                    Text("Luminosité de l'interface", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (brightness == 0) "Sombre d'origine"
                        else "Surfaces éclaircies de $brightness %",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = brightness.toFloat(),
                        onValueChange = { viewModel.setUiBrightness(it.toInt()) },
                        valueRange = 0f..40f,
                    )
                    Spacer(Modifier.height(8.dp))
                    // §lisibilite : contour/ombre sur les textes
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Contour des textes", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Ombre portée pour lire les titres sur les affiches",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = viewModel.textOutline.collectAsState().value,
                            onCheckedChange = { viewModel.setTextOutline(it) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Thème", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            AppPrefs.THEME_SYSTEM to "Système",
                            AppPrefs.THEME_LIGHT to "Clair",
                            AppPrefs.THEME_DARK to "Sombre",
                            AppPrefs.THEME_AMOLED to "AMOLED",
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label, maxLines = 1, softWrap = false) },
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
                    // §fusion-reglages : le style des cartes vit UNIQUEMENT dans
                    // la page « Interface » (il y était en double).
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Image d'arrière-plan", style = MaterialTheme.typography.bodyLarge)
                    // §fond-flou : flou de l'image de fond (Android 12+)
                    val blurNow = viewModel.bgBlur.collectAsState().value
                    Text(
                        "Flou : ${blurNow} dp" + if (blurNow == 0) " (net)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = blurNow.toFloat(),
                        onValueChange = { viewModel.setBgBlur(it.toInt()) },
                        valueRange = 0f..25f,
                    )
                    Text(
                        if (state.bgImageUri != null) "Personnalisée — dessinée derrière toute l'application"
                        else "Aucune — fond uni du thème",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.OutlinedButton(onClick = { bgImagePicker.launch("image/*") }) {
                            Text("Choisir…")
                        }
                        if (state.bgImageUri != null) {
                            TextButton(onClick = { viewModel.setBgImage(null) }) { Text("Retirer") }
                        }
                    }
                    if (state.bgImageUri != null) {
                        Text(
                            "Assombrissement : ${state.bgDim} % (lisibilité)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Slider(
                            value = state.bgDim.toFloat(),
                            onValueChange = { viewModel.setBgDim(it.toInt()) },
                            valueRange = 0f..90f,
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Variante Glass (teinte du verre)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Domine la couleur des surfaces « verre » (barres, cartes, pilules).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // §couleurs-uniques : plus de « teinte de verre » séparée — la
                    // couleur d'accent ci-dessus teinte TOUT (verre compris).
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // §verre-liquide : intensité de l'effet « goutte de liquide »
                    val liquid = viewModel.liquidGlass.collectAsState().value
                    Text("Effet verre liquide", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Reflet « goutte » sur les cartes en verre, partout (accueil, fiches, paramètres).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Slider(
                        value = liquid / 100f,
                        onValueChange = { viewModel.setLiquidGlass((it * 100).toInt()) },
                    )
                    Text(
                        when {
                            liquid == 0 -> "Désactivé"
                            liquid < 40 -> "Discret ($liquid %)"
                            liquid < 75 -> "Marqué ($liquid %)"
                            else -> "Très liquide ($liquid %)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Police de l'application", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Polices téléchargées à la volée (Google Fonts) — nécessite Internet au 1er affichage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "system" to "Système", "outfit" to "Outfit (moderne)",
                            "rubik" to "Rubik (rond)", "lora" to "Lora (élégante)",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = state.fontId == key,
                                onClick = { viewModel.setFontId(key) },
                                label = { Text(label, maxLines = 1, softWrap = false) },
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

            // ------------------------------------------------- THÈME AnyMEX
            if ("interface" in openCategories) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        // ---- §anymex-theme : les réglages de la capture Theme (AnyMEX)
                        Text("Options de thème", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        val amoledOn = state.themeMode == dev.endlesssea.app.di.AppPrefs.THEME_AMOLED
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Mode OLED", style = MaterialTheme.typography.bodyMedium)
                                Text("Noir vraiment pur (« Super Dark Mode »)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = amoledOn, onCheckedChange = { on ->
                                viewModel.setThemeMode(
                                    if (on) dev.endlesssea.app.di.AppPrefs.THEME_AMOLED
                                    else dev.endlesssea.app.di.AppPrefs.THEME_DARK
                                )
                            })
                        }
                        val liquidNow = viewModel.liquidGlass.collectAsState().value
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Mode Liquide", style = MaterialTheme.typography.bodyMedium)
                                Text("Fond vivant + reflets « goutte » partout",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = liquidNow > 0, onCheckedChange = { on ->
                                viewModel.setLiquidGlass(if (on) 60 else 0)
                            })
                        }
                        val posterCol = viewModel.usePosterColor.collectAsState().value
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Couleur de l'affiche", style = MaterialTheme.typography.bodyMedium)
                                Text("La fiche s'illumine de la dominante colorée de l'affiche",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = posterCol,
                                onCheckedChange = { viewModel.setUsePosterColor(it) })
                        }
                        val bloomOn = viewModel.bloom.collectAsState().value
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Bloom", style = MaterialTheme.typography.bodyMedium)
                                Text("Halos de lumière accentués sur le fond liquide",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = bloomOn,
                                onCheckedChange = { viewModel.setBloom(it) })
                        }
                        val immersion = viewModel.immersiveMode.collectAsState().value
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Mode immersif", style = MaterialTheme.typography.bodyMedium)
                                Text("Masque les barres système (statut + navigation) dans tout l'app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = immersion,
                                onCheckedChange = { viewModel.setImmersiveMode(it) })
                        }
                        val grainOn = viewModel.grain.collectAsState().value
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Texture « grain de film »", style = MaterialTheme.typography.bodyMedium)
                                Text("Léger bruit discret sur toute l'interface",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = grainOn,
                                onCheckedChange = { viewModel.setGrain(it) })
                        }
                        if (state.bgImageUri != null) {
                            TextButton(onClick = { viewModel.setBgImage(null) }) {
                                Text("Affiche par défaut (retirer l'image de fond)")
                            }
                        }
                    }
                }
            }
            // ------------------------------------------------------- STOCKAGE
            }
            // --------------------------------------------------- TÉLÉCHARGEMENT
            if (openCategoryFull == null && settingsQuery.isBlank()) {
                item { GroupTitle("Média & lecture") }
            }
            if (openCategoryFull == null && matchesQuery(settingsQuery, "Téléchargement", "Wi-Fi seul, segments, tâches parallèles et résolveur DNS")) item {
                SettingCategory(Icons.Filled.Download, "Téléchargement",
                    "Wi-Fi seul, segments, tâches parallèles et résolveur DNS", expanded = "download" in openCategories) { toggleCategory("download") }
            }
            if ("download" in openCategories) {
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
            item {
                // §debit (conversation 10) : plafond partagé par toute la file
                val limit = viewModel.downloadSpeedLimitKb.collectAsState().value
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Limite de bande passante", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (limit == 0) "Illimité — le téléchargement prend toute la connexion disponible."
                        else "Plafonné à ${if (limit >= 1024) "${limit / 1024} Mo/s" else "$limit Ko/s"} " +
                            "(partagé entre les segments et les tâches).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0 to "Illimité", 256 to "256 Ko/s", 512 to "512 Ko/s", 1024 to "1 Mo/s", 2048 to "2 Mo/s", 5120 to "5 Mo/s")
                            .forEach { (kb, label) ->
                                FilterChip(
                                    selected = limit == kb,
                                    onClick = { viewModel.setDownloadSpeedLimitKb(kb) },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                )
                            }
                    }
                }
            }
            item {
                // §netto-automatique (conversation 10) : éviter la saturation du stockage
                val days = viewModel.downloadAutoCleanDays.collectAsState().value
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Nettoyage automatique", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (days == 0) "Jamais — les épisodes téléchargés restent jusqu'à suppression manuelle."
                        else "Les fichiers terminés depuis plus de $days jours sont supprimés au démarrage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0 to "Jamais", 3 to "3 jours", 7 to "7 jours", 30 to "30 jours", 90 to "90 jours")
                            .forEach { (d, label) ->
                                FilterChip(
                                    selected = days == d,
                                    onClick = { viewModel.setDownloadAutoCleanDays(d) },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                )
                            }
                    }
                }
            }
                item {
                    SettingRow(
                        title = "Dossier de stockage (téléchargements + hors ligne)",
                        subtitle = state.storageUri?.let {
                            "Dossier choisi : " + android.net.Uri.decode(it.substringAfterLast("/")) +
                                " — arborescence downloads/<Source>/<Série>/<Épisode>"
                        } ?: "Aucun dossier choisi — les vidéos vont dans Movies/EndlessSea " +
                            "(visible dans le gestionnaire de fichiers). Touchez pour choisir.",
                        onClick = { storagePicker.launch(null) },
                    )
                }

            // --------------------------------------------------------- LECTEUR
            }
            if (openCategoryFull == null && matchesQuery(settingsQuery, "Lecteur", "Vitesse, gestes, mégaskip, filtres vidéo, orientation")) item {
                SettingCategory(Icons.Filled.PlayCircle, "Lecteur",
                    "Vitesse, gestes, mégaskip, filtres vidéo, orientation", expanded = "player" in openCategories) { toggleCategory("player") }
            }
            if ("player" in openCategories) {
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
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10, 15, 30, 85).forEach { sec ->
                            FilterChip(
                                selected = state.skipSeconds == sec,
                                onClick = { viewModel.setSkipSeconds(sec) },
                                label = { Text("$sec s", maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // §mégaskip : grand saut façon anime (+85 par défaut), réglable
                    val mega = viewModel.megaSkipSeconds.collectAsState().value
                    Text("Grand saut « mégaskip »", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Boutons dédiés du lecteur : ±$mega s (utile pour les génériques 90 s).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Slider(
                        value = mega.toFloat(),
                        onValueChange = { viewModel.setMegaSkipSeconds(it.toInt()) },
                        valueRange = 30f..180f,
                    )
                }
            }
            item {
                // §auto-skip
                SettingSwitch(
                    title = "Passer automatiquement les intros",
                    subtitle = "Saute seul les plages intro/générique marquées (fichiers locaux édités)",
                    checked = viewModel.autoSkipMarkers.collectAsState().value,
                    onChange = { viewModel.setAutoSkipMarkers(it) },
                )
            }
            // ---- §megaskip : segments communautaires (intro/récap/générique) en ligne,
            // mis en cache en base → fonctionnent ensuite SANS réseau.
            item { GroupTitle("Megaskip (segments en ligne)") }
            item {
                SettingSwitch(
                    title = "Sauter l'intro automatiquement",
                    subtitle = "Intro détectée par TheIntroDB / IntroDB / AniSkip, puis mise en cache",
                    checked = viewModel.skipAutoIntro.collectAsState().value,
                    onChange = { viewModel.setSkipAutoIntro(it) },
                )
            }
            item {
                SettingSwitch(
                    title = "Sauter le récap automatiquement",
                    subtitle = "Résumé de l'épisode précédent, quand la base le connaît",
                    checked = viewModel.skipAutoRecap.collectAsState().value,
                    onChange = { viewModel.setSkipAutoRecap(it) },
                )
            }
            item {
                SettingSwitch(
                    title = "Sauter le générique automatiquement",
                    subtitle = "Générique de fin — enchaîne l'épisode suivant plus vite",
                    checked = viewModel.skipAutoCredits.collectAsState().value,
                    onChange = { viewModel.setSkipAutoCredits(it) },
                )
            }
            item {
                SettingSwitch(
                    title = "Sauter les aperçus automatiquement",
                    subtitle = "Bande-annonce de l'épisode suivant en fin d'épisode",
                    checked = viewModel.skipAutoPreview.collectAsState().value,
                    onChange = { viewModel.setSkipAutoPreview(it) },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // §megaskip : délai avant le saut automatique (0 = immédiat)
                    val countdown = viewModel.skipCountdown.collectAsState().value
                    Text("Délai avant le saut automatique", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (countdown == 0) "Saut immédiat (aucun compte à rebours)"
                        else "La pastille « Passer » reste affichée $countdown s — saut si vous ne cliquez pas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Slider(
                        value = countdown.toFloat(),
                        onValueChange = { viewModel.setSkipCountdown(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Bases de segments", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Désactivez une base si elle renvoie de mauvaises plages ; les autres restent interrogées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = viewModel.skipProviderTheIntroDb.collectAsState().value,
                            onClick = {
                                viewModel.setSkipProviderTheIntroDb(
                                    !viewModel.skipProviderTheIntroDb.value,
                                )
                            },
                            label = { Text("TheIntroDB", maxLines = 1, softWrap = false) },
                        )
                        FilterChip(
                            selected = viewModel.skipProviderIntroDb.collectAsState().value,
                            onClick = {
                                viewModel.setSkipProviderIntroDb(!viewModel.skipProviderIntroDb.value)
                            },
                            label = { Text("IntroDB", maxLines = 1, softWrap = false) },
                        )
                        FilterChip(
                            selected = viewModel.skipProviderAniSkip.collectAsState().value,
                            onClick = {
                                viewModel.setSkipProviderAniSkip(!viewModel.skipProviderAniSkip.value)
                            },
                            label = { Text("AniSkip (anime)", maxLines = 1, softWrap = false) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    SettingSwitch(
                        title = "Afficher la pastille « Passer »",
                        subtitle = "Même quand le saut automatique est actif (pour annuler d'un tap)",
                        checked = viewModel.skipShowButton.collectAsState().value,
                        onChange = { viewModel.setSkipShowButton(it) },
                    )
                }
            }
            item {
                val buttons = viewModel.skipButtons
                    .collectAsState(initial = emptyList()).value
                SettingRow(
                    title = "Boutons de saut personnalisés",
                    subtitle = if (buttons.isEmpty()) {
                        "Ajoutez « +85 s », « Opening », « Filler »… (hors-ligne)"
                    } else {
                        buttons.joinToString(" · ") { it.label }
                    },
                    onClick = { showSkipDialog = true },
                )
            }
            // ---- §gestes-lecteur : zoom par pincement, inverseur volume/luminosité, stats
            item { GroupTitle("Gestes et surimpressions") }
            item {
                SettingSwitch(
                    title = "Pincer pour zoomer",
                    subtitle = "Zoom 1×–3× au pincement + déplacement à deux doigts, pendant la lecture",
                    checked = viewModel.pinchZoom.collectAsState().value,
                    onChange = { viewModel.setPinchZoom(it) },
                )
            }
            item {
                SettingSwitch(
                    title = "Inverser volume et luminosité",
                    subtitle = "Glisser à droite = luminosité, à gauche = volume (défaut : l'inverse)",
                    checked = viewModel.swapVolumeBrightness.collectAsState().value,
                    onChange = { viewModel.setSwapVolumeBrightness(it) },
                )
            }
            item {
                SettingSwitch(
                    title = "Statistiques de lecture",
                    subtitle = "Résolution, débit, codec, images/s et images perdues en surimpression",
                    checked = viewModel.playerStats.collectAsState().value,
                    onChange = { viewModel.setPlayerStats(it) },
                )
            }
            item {
                SettingRow(
                    title = "Vider le cache des segments",
                    subtitle = "Force une nouvelle recherche auprès des bases au prochain épisode",
                    onClick = { viewModel.clearSkipCache() },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // §orientation-lecteur : paysage par défaut, choix utilisateur
                    Text("Orientation du lecteur", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Le lecteur démarre à l'horizontale par défaut — changeable pendant la lecture (bouton rotation).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val orient = viewModel.playerOrientation.collectAsState().value
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = orient != "portrait",
                            onClick = { viewModel.setPlayerOrientation("landscape") },
                            label = { Text("Paysage (défaut)", maxLines = 1, softWrap = false) },
                        )
                        FilterChip(
                            selected = orient == "portrait",
                            onClick = { viewModel.setPlayerOrientation("portrait") },
                            label = { Text("Portrait", maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // §reprise-fiche / §historique-local / §fichiers-caches
                    val resumeOn = viewModel.resumePrompt.collectAsState().value
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Proposer de reprendre", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "À l'ouverture d'une fiche déjà commencée, une boîte propose de reprendre.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = resumeOn, onCheckedChange = { viewModel.setResumePrompt(it) })
                    }
                    val localHist = viewModel.localInHistory.collectAsState().value
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Fichiers locaux dans l'historique", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Les vidéos du téléphone apparaissent dans « Reprendre la lecture ».",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = localHist, onCheckedChange = { viewModel.setLocalInHistory(it) })
                    }
                    val hidden = viewModel.showHiddenFiles.collectAsState().value
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Afficher les fichiers cachés", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Inclut les fichiers et dossiers commençant par un point.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = hidden, onCheckedChange = { viewModel.setShowHiddenFiles(it) })
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // §placements-lecteur : l'utilisateur décide où vont les contrôles
                    Text("Disposition des contrôles", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Barre de progression, rangée d'outils et pastille de grand saut : " +
                            "chacun en haut ou en bas, à votre main.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val progPos = viewModel.progressPosition.collectAsState().value
                    Text("Barre de progression (anciens thèmes)", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = progPos != "top",
                            onClick = { viewModel.setProgressPosition("bottom") },
                            label = { Text("En bas", maxLines = 1, softWrap = false) },
                        )
                        FilterChip(
                            selected = progPos == "top",
                            onClick = { viewModel.setProgressPosition("top") },
                            label = { Text("En haut", maxLines = 1, softWrap = false) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val toolsPos = viewModel.toolsPosition.collectAsState().value
                    Text("Outils en haut / bas (anciens thèmes)", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = toolsPos == "top",
                            onClick = { viewModel.setToolsPosition("top") },
                            label = { Text("En haut", maxLines = 1, softWrap = false) },
                        )
                        FilterChip(
                            selected = toolsPos != "top",
                            onClick = { viewModel.setToolsPosition("bottom") },
                            label = { Text("En bas", maxLines = 1, softWrap = false) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val megaSide = viewModel.megaSkipSide.collectAsState().value
                    Text("Pastille de grand saut", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = megaSide != "left",
                            onClick = { viewModel.setMegaSkipSide("right") },
                            label = { Text("À droite", maxLines = 1, softWrap = false) },
                        )
                        FilterChip(
                            selected = megaSide == "left",
                            onClick = { viewModel.setMegaSkipSide("left") },
                            label = { Text("À gauche", maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // §barre-progression : épaisseur + bouts arrondis
                    val thick = viewModel.progressThickness.collectAsState().value
                    val rounded = viewModel.progressRounded.collectAsState().value
                    Text("Barre de progression — style", style = MaterialTheme.typography.bodyLarge)
                    Text("Épaisseur : $thick dp", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = thick.toFloat(),
                        onValueChange = { viewModel.setProgressThickness(it.toInt()) },
                        valueRange = 2f..14f,
                        steps = 11,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = rounded, onCheckedChange = { viewModel.setProgressRounded(it) })
                        Spacer(Modifier.width(10.dp))
                        Text("Bouts arrondis")
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // §theme-lecteur : habillages type Netflix / Crunchyroll / …
                    Text("Thème du lecteur", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Chaque thème est une mise en page complète des commandes du lecteur " +
                            "(barres, formes, disposition, accents) — pas seulement une couleur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val cur = viewModel.playerTheme.collectAsState().value
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dev.endlesssea.app.di.AppPrefs.PLAYER_THEMES.forEach { (key, v) ->
                            FilterChip(
                                selected = cur == key,
                                onClick = { viewModel.setPlayerTheme(key) },
                                label = { Text(v.second, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // §amelioration-video : profil d'image par défaut
                    Text("Amélioration de l'image", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Appliquée à chaque lecture. Pas d'upscale « anime 4K » (trop lourd " +
                            "pour un téléphone) : ce sont des traitements légers — netteté, " +
                            "éclat, lissage du grain, rendu cinéma, mode nuit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val enh = viewModel.videoEnhance.collectAsState().value
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "none" to "Aucun", "anime" to "Anime",
                            "anime_fort" to "Anime fort", "net" to "Netteté douce",
                            "eclat" to "Éclat", "doux" to "Anti-grain",
                            "cinema" to "Cinéma", "nuit" to "Nuit",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = enh == key,
                                onClick = { viewModel.setVideoEnhance(key) },
                                label = { Text(label, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // §rendu-vidéo : choix de la surface de rendu
                    Text("Rendu vidéo", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Texture ou Surface : deux sorties vidéo disponibles. " +
                            "Les filtres utilisent le GPU dans les deux modes ; fluidité et consommation dépendent de l’appareil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    val render = viewModel.videoRender.collectAsState().value
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = render != "surface",
                            onClick = { viewModel.setVideoRender("texture") },
                            label = { Text("Texture (filtres)", maxLines = 1, softWrap = false) },
                        )
                        FilterChip(
                            selected = render == "surface",
                            onClick = { viewModel.setVideoRender("surface") },
                            label = { Text("Surface (perf)", maxLines = 1, softWrap = false) },
                        )
                    }
                }
            }
            // §fusion-filtres : les filtres vidéo sont ICI, avec les autres
            // réglages du lecteur (plus de page séparée).
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    // §upscale-niveaux (conversation 1) : un seul choix pour
                    // l'agrandissement, du plus léger au plus lourd.
                    val level = viewModel.upscaleLevel.collectAsState().value
                    Text("Zoom et netteté", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Le zoom rapproche l'image et peut rogner les bords. La netteté renforce les contours, " +
                            "sans créer de détails absents de la source. Aucun upscale IA n'est appliqué.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dev.endlesssea.player.UpscalingLevel.entries.forEach { lvl ->
                            FilterChip(
                                selected = level == lvl.id,
                                onClick = { viewModel.setUpscaleLevel(lvl.id) },
                                label = { Text(lvl.label, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                    // §thermique : le niveau redescend tout seul si l'appareil chauffe.
                    val guard = viewModel.thermalGuard.collectAsState().value
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Réduire si l'appareil chauffe", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Pression thermique sévère détectée → zoom/netteté réduits d'un cran " +
                                    "(puis coupé si l'appareil reste chaud).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = guard, onCheckedChange = { viewModel.setThermalGuard(it) })
                    }
                    Spacer(Modifier.height(8.dp))
                    // §upscale : réglage fin manuel (échelle + contours)
                    val scale = viewModel.videoScale.collectAsState().value
                    Text(
                        "Réglage fin — agrandissement : x%.2f".format(scale),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1f to "Natif", 1.25f to "x1.25", 1.5f to "x1.5", 2f to "x2")
                            .forEach { (value, label) ->
                                FilterChip(
                                    selected = kotlin.math.abs(scale - value) < 0.01f,
                                    onClick = { viewModel.setVideoScale(value) },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                )
                            }
                    }
                    val sharpen = viewModel.videoSharpen.collectAsState().value
                    Text(
                        "Renforcement des contours : %d %%".format((sharpen * 100).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = sharpen,
                        onValueChange = { viewModel.setVideoSharpen(it) },
                        valueRange = 0f..2f,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Filtres vidéo", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Appliqués à toutes les lectures (ajustables aussi en cours de vidéo).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val contrast = viewModel.videoContrast.collectAsState().value
                    val gamma = viewModel.videoGamma.collectAsState().value
                    val sharp = viewModel.videoSharp.collectAsState().value
                    val temp = viewModel.videoTemp.collectAsState().value
                    Text("Contraste : %.2f".format(contrast), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = contrast,
                        onValueChange = { viewModel.setVideoAdvanced(it, gamma, sharp, temp) },
                        valueRange = 0.5f..1.8f,
                    )
                    Text("Gamma : %.2f".format(gamma), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = gamma,
                        onValueChange = { viewModel.setVideoAdvanced(contrast, it, sharp, temp) },
                        valueRange = 0.5f..1.8f,
                    )
                    Text("Netteté : %.2f".format(sharp), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = sharp,
                        onValueChange = { viewModel.setVideoAdvanced(contrast, gamma, it, temp) },
                        valueRange = 0f..1f,
                    )
                    Text("Température : %.2f".format(temp), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = temp,
                        onValueChange = { viewModel.setVideoAdvanced(contrast, gamma, sharp, it) },
                        valueRange = -1f..1f,
                    )
                    TextButton(onClick = { viewModel.setVideoAdvanced(1f, 1f, 0f, 0f) }) {
                        Text("Réinitialiser les filtres")
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Langue audio préférée", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Utilisée pour trier les serveurs (VF/VOSTFR en premier) et transmise aux sources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("auto" to "Auto", "vf" to "VF", "vostfr" to "VOSTFR", "vo" to "VO")
                            .forEach { (key, label) ->
                                FilterChip(
                                    selected = state.preferredAudioLang == key,
                                    onClick = { viewModel.setPreferredAudioLang(key) },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                )
                            }
                    }
                }
            }

            // ---------------------------------------------------------- GENRES
            }
            // §fusion-reglages : « Genres » n'est plus une page à part — elle vit
            // désormais à la fin de la page « Interface ».
            if ("genres" in openCategories || "ui" in openCategories) {
            item {
                SettingRow(
                    title = "Gérer les genres (${state.genres.size})",
                    subtitle = "ajouter · renommer · réordonner · afficher/masquer · supprimer",
                    onClick = { showGenreManager = true },
                )
            }

            // -------------------------------------- RÉGLAGES PAR EXTENSION
            }
            // §fusion-reglages : les réglages par source sont désormais DANS la
            // page Extensions (plus de doublon ici).
            if ("sources" in openCategories) {
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
                            ) { dev.endlesssea.app.ui.components.EsLoadingIndicator(Modifier.width(28.dp).height(28.dp), strokeWidth = 2.dp) }
                        }
                    }
                }
            }

            // ------------------------------------------------------- SAUVEGARDE
            }
            if (openCategoryFull == null && settingsQuery.isBlank()) {
                item { GroupTitle("Préférences & système") }
            }
            if ("download" in openCategories) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Résolveur DNS", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Contourne certains blocages de sources côté FAI. En cas d'échec, EndlessSea " +
                            "retombe automatiquement sur le DNS système — jamais de coupure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("system" to "Système", "cloudflare" to "Cloudflare (1.1.1.1)", "google" to "Google (8.8.8.8)")
                            .forEach { (key, label) ->
                                FilterChip(
                                    selected = state.dnsMode == key,
                                    onClick = { viewModel.setDnsMode(key) },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                )
                            }
                    }
                    Text(
                        "Pris en compte au prochain démarrage de l'app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            if (openCategoryFull == null && matchesQuery(settingsQuery, "Sauvegarde", "Export / import JSON + sauvegarde automatique locale")) item {
                SettingCategory(Icons.Filled.Save, "Sauvegarde",
                    "Export / import JSON + sauvegarde automatique locale", expanded = "backup" in openCategories) { toggleCategory("backup") }
            }
            if ("backup" in openCategories) {
            item {
                SettingSwitch(
                    title = "Sauvegarde automatique locale",
                    subtitle = "À l'ouverture des réglages, au plus une fois par jour. Fichier daté dans le dossier privé de l'app ; supprimé à la désinstallation. Exporte une copie ailleurs." +
                        (if (state.lastBackupAt > 0)
                            "\nDernière : ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE).format(java.util.Date(state.lastBackupAt))}"
                        else "") + (state.autoBackupError?.let { "\nÉchec de la copie automatique : $it. Exporte une copie manuelle." } ?: ""),
                    checked = state.autoBackup,
                    onChange = viewModel::setAutoBackup,
                )
            }
            item {
                SettingRow(
                    title = if (backupBusy) "Sauvegarde en cours…" else "Exporter mes données",
                    subtitle = "Bibliothèque, historique complet, fiches, genres, catégories, annotations locales et réglages sélectionnés. JSON non chiffré, sans vidéos, images ni jetons.",
                    onClick = { if (!backupBusy) exportLauncher.launch("endless-sea-backup.json") },
                )
            }
            item {
                SettingRow(
                    title = "Importer une sauvegarde",
                    subtitle = "Vérification et aperçu avant fusion · JSON de 20 Mio maximum · anciennes sauvegardes acceptées",
                    onClick = { if (!backupBusy) importLauncher.launch(arrayOf("application/json", "text/plain")) },
                )
            }

            // --------------------------------------------------- MISES À JOUR
            }
            if ("about" in openCategories) {
            item {
                val diagTick = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
                val entries = androidx.compose.runtime.remember(diagTick.value) {
                    dev.endlesssea.core.diag.EsLog.entries()
                }
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { diagTick.value++ }) { Text("Rafraîchir") }
                        TextButton(
                            onClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(dev.endlesssea.core.diag.EsLog.export()))
                                viewModel.toastState("Journal copié")
                            },
                            enabled = entries.isNotEmpty(),
                        ) { Text("Copier") }
                        TextButton(
                            onClick = {
                                runCatching {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, dev.endlesssea.core.diag.EsLog.export())
                                    }
                                    context.startActivity(android.content.Intent.createChooser(send, "Exporter le journal"))
                                }
                            },
                            enabled = entries.isNotEmpty(),
                        ) { Text("Exporter") }
                        TextButton(
                            onClick = { dev.endlesssea.core.diag.EsLog.clear(); diagTick.value++ },
                            enabled = entries.isNotEmpty(),
                        ) { Text("Effacer", color = MaterialTheme.colorScheme.error) }
                    }
                    if (entries.isEmpty()) {
                        Text(
                            "Aucune erreur enregistrée depuis le lancement",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        entries.take(30).forEach { entry ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    "[${entry.category}/${entry.component}]",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                                if (entry.details.isNotBlank()) {
                                    Text(
                                        entry.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
            if ("about" in openCategories) {
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
            }
            if (openCategoryFull == null && matchesQuery(settingsQuery, "À propos", "Version, mises à jour, journal d'erreurs, licence")) item {
                SettingCategory(Icons.Filled.Info, "À propos",
                    "Version, mises à jour, journal d'erreurs, licence", expanded = "about" in openCategories) { toggleCategory("about") }
            }
            if ("about" in openCategories) {
            item {
                SettingRow(
                    title = "Endless Sea 0.13.0",
                    subtitle = "GPL-3.0 · aucune source incluse · github.com/j97970293-lang/endlesssea",
                    onClick = { },
                )
            }
            }
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ------------------------------------------------- Progression MAJ (in-app)
    dev.endlesssea.app.update.UpdateProgressDialog(
        onRetry = state.lastFailedUpdate?.let { info ->
            { viewModel.retryUpdateDownload(context, info) }
        },
    )

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
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        segments.forEach { n ->
                            FilterChip(
                                selected = state.partsPerTask == n,
                                onClick = { viewModel.setPartsPerTask(n) },
                                label = { Text("$n", maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                    Text("Tâches simultanées", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tasks.forEach { n ->
                            FilterChip(
                                selected = state.parallelTasks == n,
                                onClick = { viewModel.setParallelTasks(n) },
                                label = { Text("$n", maxLines = 1, softWrap = false) },
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
                        } else if (entry.type == dev.endlesssea.extensions.api.model.ExtensionSetting.Type.LIST &&
                            entry.options.isNotEmpty()
                        ) {
                            // Liste à puces : sélection = application immédiate
                            Column(Modifier.fillMaxWidth()) {
                                Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                                entry.summary?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    entry.options.forEach { opt ->
                                        FilterChip(
                                            selected = entry.value == opt,
                                            onClick = { viewModel.saveExtSetting(editing.pkg, entry.key, opt) },
                                            label = { Text(opt, maxLines = 1, softWrap = false) },
                                        )
                                    }
                                }
                            }
                        } else {
                            var text by remember(entry.key) { mutableStateOf(entry.value) }
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
                Button(onClick = { viewModel.startUpdate(context) }) { Text("Télécharger ici") }
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
                Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        FilterChip(
                            selected = state.defaultSpeed == s,
                            onClick = { viewModel.setDefaultSpeed(s) },
                            label = { Text("${s}×", maxLines = 1, softWrap = false) },
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

    // §megaskip : boutons de saut personnalisés (même éditeur que dans le lecteur)
    if (showSkipDialog) {
        val buttons = viewModel.skipButtons.collectAsState(initial = emptyList()).value
        dev.endlesssea.app.ui.player.CustomSkipDialog(
            buttons = buttons,
            onAdd = { label, seconds -> viewModel.addSkipButton(label, seconds) },
            onUpdate = { viewModel.updateSkipButton(it) },
            onDelete = { viewModel.deleteSkipButton(it) },
            onDismiss = { showSkipDialog = false },
        )
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


/** Titre de section de la colonne d'accueil (style AnyMEX — capitales sobres). */
/**
 * §réglages-recherche : une catégorie reste visible si la requête est vide ou si
 * elle apparaît dans son titre ou son sous-titre (sans accents ni casse).
 */
private fun matchesQuery(query: String, title: String, subtitle: String): Boolean {
    if (query.isBlank()) return true
    fun norm(v: String) = java.text.Normalizer.normalize(v, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
    val q = norm(query)
    return norm(title).contains(q) || norm(subtitle).contains(q)
}

@Composable
private fun GroupTitle(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** §réglages-maison-capture : carte d'entrée pleine largeur — icône en tuile
 *  arrondie, titre, sous-titre, chevron. Ouvre la page dédiée de la section. */
@Composable
private fun SettingCategory(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 3.dp)
            .motionClickable(onClick = onToggle),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        tonalElevation = if (expanded) 2.dp else 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp).height(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Replier" else "Ouvrir la page",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().motionClickable(onClick = onClick).padding(16.dp),
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
