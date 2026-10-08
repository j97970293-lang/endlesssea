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
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale as androidxScale
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
            val liquidGlass by prefs.liquidGlass.collectAsState()
            val bloom by prefs.bloom.collectAsState()
            val grain by prefs.grain.collectAsState()
            val immersiveMode by prefs.immersiveMode.collectAsState()
            // §anymex-immersif : barres système (statut + navigation) masquées quand demandé
            androidx.compose.runtime.DisposableEffect(immersiveMode) {
                val wc = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                if (immersiveMode) {
                    wc.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                } else {
                    wc.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
                onDispose { }
            }
            // §anymex-ui : réglages de la page « Interface »
            val glowMult by prefs.glowMultiplier.collectAsState()
            val radiusMult by prefs.radiusMultiplier.collectAsState()
            val blurMult by prefs.blurMultiplier.collectAsState()
            val cardRoundness by prefs.cardRoundness.collectAsState()
            val cardAnim by prefs.cardAnimation.collectAsState()
            val carouselStyle by prefs.carouselStyle.collectAsState()
            val historyCardStyle by prefs.historyCardStyle.collectAsState()
            val animationsOn by prefs.enableAnimation.collectAsState()
            val navBarLayout by prefs.navBarLayout.collectAsState()
            val translucentNav by prefs.translucentNav.collectAsState()
            val legacyHeader by prefs.legacyHeader.collectAsState()
            val borderStrength by prefs.borderStrength.collectAsState()
            val accentGlassLink by prefs.accentLinkedToGlass.collectAsState()
            val uiBrightness by prefs.uiBrightness.collectAsState()
            val textOutline by prefs.textOutline.collectAsState()
            androidx.compose.runtime.LaunchedEffect(
                glassOverlay, glassScrim, cardStyle, preferredAudioLang,
                glassVariant, liquidGlass, accentGlassLink, accentName, bloom,
                glowMult, radiusMult, blurMult, cardRoundness, cardAnim,
                carouselStyle, historyCardStyle, animationsOn, borderStrength,
            ) {
                // §couleurs : « fusion accent/verre » — si liées, l'accent teinte aussi le verre ;
                // sinon la variante Glass saturée choisie s'applique (beaucoup de couleurs).
                // §couleurs-uniques : une seule couleur pilote tout (le sélecteur
                // de « teinte de verre » a été supprimé) — c'est l'accent.
                val tint: Long? = AppPrefs.ACCENTS[accentName]
                dev.endlesssea.app.ui.components.UiTuning.update(
                    glassOverlay, glassScrim, cardStyle,
                    tintArgb = tint, liquid = liquidGlass,
                )
                dev.endlesssea.app.ui.components.UiTuning.updateBloom(bloom)
                dev.endlesssea.app.ui.components.UiTuning.updateStyles(
                    glow = glowMult, radius = radiusMult, blur = blurMult,
                    roundness = cardRoundness, cardAnimation = cardAnim,
                    carouselStyle = carouselStyle, historyCardStyle = historyCardStyle,
                )
                dev.endlesssea.app.ui.components.UiTuning.updateAnimations(animationsOn)
                dev.endlesssea.app.ui.components.UiTuning.updateBorders(borderStrength)
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
                uiBrightness = uiBrightness,
                textOutline = textOutline,
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
                val darkBg = amoled || themeMode == AppPrefs.THEME_DARK ||
                    (themeMode == AppPrefs.THEME_SYSTEM &&
                        isSystemInDarkTheme())
                // §verre-liquide-fond : le fond sombre entier devient du verre liquide animé
                val showLiquid = darkBg && liquidGlass > 0
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
                    if (showLiquid && bgImage == null) {
                        dev.endlesssea.app.ui.components.LiquidBackground(Modifier.fillMaxSize())
                    }
                    // §anymex-theme : texture « film grain » par-dessus tout
                    if (grain) {
                        dev.endlesssea.app.ui.components.GrainOverlay()
                    }
                    val bgBlur by prefs.bgBlur.collectAsState()
                    bgImage?.let { uri ->
                        coil.compose.AsyncImage(
                            model = uri,
                            contentDescription = null,
                            // §fond-flou : flou réglable sur l'image d'arrière-plan
                            modifier = Modifier.fillMaxSize()
                                .then(
                                    if (bgBlur > 0) {
                                        dev.endlesssea.app.ui.components.blurModifier(bgBlur.dp)
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = (bgDim / 100f * 0.92f))),
                        )
                    }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = if (bgImage != null || showLiquid) Color.Transparent
                    else if (amoled) Color.Black else MaterialTheme.colorScheme.background,
                    topBar = {
                        // §barre-haut : PLUS DE BARRE DU TOUT. Les deux boutons
                        // (menu logo + recherche) flottent au-dessus du contenu,
                        // sans conteneur ni voile translucide.
                    },
                    bottomBar = {
                        val immersive = route?.startsWith("details/") == true ||
                            route?.startsWith("localDetails/") == true
                        if (route != Screen.Settings.route && !immersive) {
                            Box(Modifier.navigationBarsPadding()) {
                                EsBottomBar(
                                    nav, currentRoute = route,
                                    // §anymex-ui : disposition « moderne » = pas d'onglet
                                    // Recherche dédié (la recherche vit dans Explorer).
                                    tabs = if (navBarLayout == "modern") barTabs - "search" else barTabs,
                                    order = tabOrder, marginDp = barMargin,
                                    translucent = translucentNav,
                                    // §barre-dynamique : pilule avec libellé par défaut (réf. Anymex)
                                    style = prefs.navBarStyle.collectAsState().value,
                                )
                            }
                        }
                    },
                ) { padding ->
                    val immersive = route?.startsWith("details/") == true ||
                            route?.startsWith("localDetails/") == true
                    val showFloating = route != Screen.Settings.route && !immersive

                    Box(Modifier.fillMaxSize()) {
                        EsNavGraphContainer(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            nav = nav,
                        )
                        if (showFloating) {
                            // ---- Boutons flottants (aucune barre derrière)
                            var logoMenu by androidx.compose.runtime.remember {
                                androidx.compose.runtime.mutableStateOf(false)
                            }
                            var logoIn by androidx.compose.runtime.remember {
                                androidx.compose.runtime.mutableStateOf(false)
                            }
                            androidx.compose.runtime.LaunchedEffect(Unit) { logoIn = true }
                            val logoScale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (logoIn) 1f else 0.6f,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = 0.55f,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                                ),
                                label = "logoIn",
                            )
                            Row(
                                Modifier.align(Alignment.TopStart)
                                    .statusBarsPadding()
                                    .padding(start = 8.dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box {
                                    IconButton(onClick = { logoMenu = true }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.PlaylistPlay,
                                            contentDescription = "Menu EndlessSea",
                                            modifier = Modifier
                                                .size(32.dp)
                                                .androidxScale(logoScale),
                                        )
                                    }
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = logoMenu,
                                        onDismissRequest = { logoMenu = false },
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Paramètres") },
                                            leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                            onClick = {
                                                logoMenu = false
                                                nav.navigate(Screen.Settings.route)
                                            },
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Extensions") },
                                            leadingIcon = { Icon(Icons.Filled.Extension, null) },
                                            onClick = {
                                                logoMenu = false
                                                nav.navigate(Screen.Extensions.route)
                                            },
                                        )
                                    }
                                }
                                // §barre-haut (conversation 4) : le
                                // sélecteur de source vit désormais dans la
                                // barre en verre de l'écran Accueil.
                            }
                            // §barre-haut (conversation 4) : la recherche
                            // de l'accueil est dans la barre en verre.
                        }
                    }
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
