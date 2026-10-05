package dev.endlesssea.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.app.navigation.EsBottomBar
import dev.endlesssea.app.navigation.EsNavGraph
import dev.endlesssea.app.navigation.Screen
import dev.endlesssea.app.ui.theme.EndlessSeaTheme
import dev.endlesssea.app.update.AppUpdateInfo
import dev.endlesssea.app.update.AppUpdateInstaller
import dev.endlesssea.app.update.UpdateChecker
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: AppPrefs
    @Inject lateinit var updateChecker: UpdateChecker

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by prefs.themeMode.collectAsState()
            val barTabs by prefs.barTabs.collectAsState()
            val tabOrder by prefs.tabOrder.collectAsState()
            val barMargin by prefs.barMargin.collectAsState()
            val accentName by prefs.accent.collectAsState()
            val autoCheckUpdate by prefs.updateAutoCheck.collectAsState()
            val glassOverlay by prefs.glassOverlay.collectAsState()
            val glassScrim by prefs.glassScrim.collectAsState()
            val cardStyle by prefs.cardStyle.collectAsState()

            // Réglages « verre »/cartes → composants globaux (sans ré-injection)
            val preferredAudioLang by prefs.preferredAudioLang.collectAsState()
            val glassVariant by prefs.glassVariant.collectAsState()
            val fontId by prefs.fontId.collectAsState()
            androidx.compose.runtime.LaunchedEffect(glassOverlay, glassScrim, cardStyle, preferredAudioLang, glassVariant) {
                dev.endlesssea.app.ui.components.UiTuning.update(
                    glassOverlay, glassScrim, cardStyle,
                    tintArgb = if (glassVariant == "auto") null else AppPrefs.GLASS_VARIANTS[glassVariant],
                )
                dev.endlesssea.extensions.loader.AppEnv.preferredAudioLang = preferredAudioLang
            }
            val pendingUpdate = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf<AppUpdateInfo?>(null)
            }
            /** Dernière MAJ tentée — survit à la fermeture de la boîte §5 (bouton Réessayer). */
            val lastUpdateAttempt = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf<AppUpdateInfo?>(null)
            }
            val context = androidx.compose.ui.platform.LocalContext.current

            // Vérification automatique à l'ouverture (« mise à jour auto »)
            androidx.compose.runtime.LaunchedEffect(autoCheckUpdate) {
                if (autoCheckUpdate && pendingUpdate.value == null) {
                    updateChecker.latest()?.let { latest ->
                        if (updateChecker.isNewer(latest.tag)) pendingUpdate.value = latest
                    }
                }
            }

            EndlessSeaTheme(
                themeMode = themeMode,
                accentArgb = AppPrefs.ACCENTS[accentName] ?: 0xFFB9C1FF,
                fontId = fontId,
            ) {
                // ---- Splash animé (logo qui grandit en fondu, ~800 ms) puis application
                val logoTint by prefs.logoTint.collectAsState()
                var showSplash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
                if (showSplash) {
                    dev.endlesssea.app.ui.components.SplashScreen(
                        tintArgb = dev.endlesssea.app.ui.components.logoTintArgb(logoTint),
                    ) { showSplash = false }
                    return@EndlessSeaTheme
                }
                // Boîte « nouvelle version »
                pendingUpdate.value?.let { update ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { pendingUpdate.value = null },
                        confirmButton = {
                            // §5 : tout se passe dans l'app (dialogue de progression)
                            androidx.compose.material3.Button(onClick = {
                                lastUpdateAttempt.value = update
                                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    updateChecker.downloadUpdate(context, update)
                                }
                                pendingUpdate.value = null
                            }) { Text("Télécharger ici") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { pendingUpdate.value = null }) {
                                Text("Plus tard")
                            }
                        },
                        title = { Text("Mise à jour ${update.tag} disponible") },
                        text = {
                            Text(update.notes.take(500).ifBlank { "Nouvelle version publiée sur GitHub Releases." })
                        },
                    )
                }

                dev.endlesssea.app.update.UpdateProgressDialog(
                    onRetry = (pendingUpdate.value ?: lastUpdateAttempt.value)?.let { update ->
                        {
                            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                updateChecker.downloadUpdate(context, update)
                            }
                        }
                    },
                )

                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val amoled = themeMode == AppPrefs.THEME_AMOLED
                val titleRoute = route ?: Screen.Home.route
                val title = when (titleRoute) {
                    in setOf("home", "explore", "search", "library", "downloads", "extensions", "settings") ->
                        listOf(
                            Screen.Home, Screen.Explore, Screen.Search,
                            Screen.Library, Screen.Downloads, Screen.Extensions, Screen.Settings,
                        ).first { it.route == titleRoute }.label
                    else -> "Endless Sea"
                }

                // ---- Fond d'écran personnalisé (image choisie dans Réglages), assombri pour la lisibilité
                val bgImage by prefs.bgImageUri.collectAsState()
                val bgDim by prefs.bgDim.collectAsState()
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    bgImage?.let { uri ->
                        coil.compose.AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = (bgDim / 100f * 0.92f))),
                        )
                    }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = if (bgImage != null) Color.Transparent
                    else if (amoled) Color.Black else MaterialTheme.colorScheme.background,
                    topBar = {
                        // §réglages-pleine-page : sur Paramètres, AUCUNE barre — page dédiée.
                        if (route != Screen.Settings.route) {
                        CenterAlignedTopAppBar(
                            modifier = Modifier.statusBarsPadding(),
                            title = { Text(title) },
                            actions = {
                                IconButton(onClick = { nav.navigate(Screen.Extensions.route) }) {
                                    Icon(Icons.Filled.Extension, contentDescription = "Extensions")
                                }
                                IconButton(onClick = { nav.navigate(Screen.Settings.route) }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Paramètres")
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        }
                    },
                    bottomBar = {
                        if (route != Screen.Settings.route) {
                            Box(Modifier.navigationBarsPadding()) {
                                EsBottomBar(nav, currentRoute = route, tabs = barTabs, order = tabOrder, marginDp = barMargin)
                            }
                        }
                    },
                ) { padding ->
                    EsNavGraphContainer(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        nav = nav,
                    )
                }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun EsNavGraphContainer(
    modifier: Modifier,
    nav: androidx.navigation.NavHostController,
) {
    androidx.compose.foundation.layout.Box(modifier) { EsNavGraph(nav) }
}

@Suppress("unused")
@androidx.compose.runtime.Composable
private fun IsDark(amoled: Boolean): Boolean = amoled || isSystemInDarkTheme()
