package dev.endlesssea.app.navigation

import dev.endlesssea.app.di.AppPrefs

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.endlesssea.app.ui.components.glass
import dev.endlesssea.app.ui.details.DetailsScreen
import dev.endlesssea.app.ui.downloads.DownloadsScreen
import dev.endlesssea.app.ui.explore.ExploreScreen
import dev.endlesssea.app.ui.extensions.ExtensionsScreen
import dev.endlesssea.app.ui.home.HomeScreen
import dev.endlesssea.app.ui.library.LibraryScreen
import dev.endlesssea.app.ui.search.SearchScreen
import dev.endlesssea.app.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Accueil", Icons.Filled.Home)
    data object Explore : Screen("explore", "Explorer", Icons.Filled.Explore)
    data object Search : Screen("search", "Recherche", Icons.Filled.Search)
    data object Library : Screen("library", "Bibliothèque", Icons.AutoMirrored.Filled.PlaylistPlay)
    data object Downloads : Screen("downloads", "Téléchargements", Icons.Filled.Download)
    data object Extensions : Screen("extensions", "Extensions", Icons.Filled.Explore)
    data object Settings : Screen("settings", "Paramètres", Icons.Filled.Home)
}

/** Toutes les entrées possibles de la barre, dans un ordre stable. */
val allTabScreens: List<Screen> =
    listOf(Screen.Home, Screen.Explore, Screen.Library, Screen.Downloads)

/** Barre de navigation flottante « verre » — onglets filtrés par les préférences utilisateur. */
@Composable
fun EsBottomBar(
    nav: NavHostController,
    currentRoute: String?,
    tabs: Set<String>,
    order: List<String> = AppPrefs.ALL_TAB_ROUTES,
    marginDp: Int = 16,
    /** §barre-dynamique : "dynamic" = pilule (icône+libellé sélectionné) · "classic" = icônes seules. */
    style: String = "dynamic",
    /** §anymex-ui : barre translucide (verre) ou fond plein pour un maximum de lisibilité. */
    translucent: Boolean = true,
) {
    val byRoute = allTabScreens.associateBy { it.route }
    val shown = order.mapNotNull { byRoute[it] }.filter { it.route in tabs }.ifEmpty { listOf(Screen.Home) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = marginDp.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = (
                if (translucent) Modifier.glass(cornerRadius = 30.dp)
                else Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ).padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            shown.forEach { screen ->
                val selected = currentRoute == screen.route
                if (style == "dynamic") {
                    // §barre-dynamique : l'onglet actif s'étire en pilule avec son libellé
                    Row(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                else Color.Transparent,
                            )
                            .clickable {
                                nav.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                            .animateContentSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            screen.icon,
                            contentDescription = screen.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        if (selected) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                screen.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else {
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(screen.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        // Icônes seules (style Anymex) — le rôle reste accessible via contentDescription
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun EsNavGraph(nav: NavHostController) {
    // L'id composite « ext:url » peut contenir des caractères spéciaux → encodage
    fun openDetails(nav: NavHostController, id: String) = nav.navigate("details/${Uri.encode(id)}")

    // §animations : transitions douces entre tous les écrans (fondu + léger glissé)
    val enterAnim = androidx.compose.animation.fadeIn(
        androidx.compose.animation.core.tween(220),
    ) + androidx.compose.animation.slideInHorizontally(
        androidx.compose.animation.core.tween(260),
    ) { it / 14 }
    val exitAnim = androidx.compose.animation.fadeOut(
        androidx.compose.animation.core.tween(180),
    ) + androidx.compose.animation.slideOutHorizontally(
        androidx.compose.animation.core.tween(220),
    ) { -it / 14 }
    val popEnterAnim = androidx.compose.animation.fadeIn(
        androidx.compose.animation.core.tween(220),
    ) + androidx.compose.animation.slideInHorizontally(
        androidx.compose.animation.core.tween(260),
    ) { -it / 14 }
    val popExitAnim = androidx.compose.animation.fadeOut(
        androidx.compose.animation.core.tween(180),
    ) + androidx.compose.animation.slideOutHorizontally(
        androidx.compose.animation.core.tween(220),
    ) { it / 14 }

    NavHost(
        navController = nav,
        startDestination = Screen.Home.route,
        enterTransition = { enterAnim },
        exitTransition = { exitAnim },
        popEnterTransition = { popEnterAnim },
        popExitTransition = { popExitAnim },
    ) {
        composable(Screen.Home.route) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            HomeScreen(
                onMediaClick = { id ->
                    // §historique-local : une carte locale relance directement la vidéo
                    if (id.startsWith("local:")) {
                        dev.endlesssea.app.ui.player.PlayerLaunchStore.resolver = null
                        val uri = id.removePrefix("local:")
                        dev.endlesssea.app.ui.player.PlayerLaunchStore.setQueue(emptyList(), 0)
                        dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
                            title = android.net.Uri.decode(uri.substringAfterLast("/")),
                            mediaId = null, episodeId = uri,
                            links = listOf(
                                dev.endlesssea.extensions.api.model.VideoLink(
                                    url = uri,
                                    streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                                    quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN,
                                    server = "Fichier local",
                                ),
                            ),
                            startIndex = 0,
                        )
                        ctx.startActivity(
                            android.content.Intent(
                                ctx, dev.endlesssea.app.ui.player.PlayerActivity::class.java,
                            ),
                        )
                    } else {
                        openDetails(nav, id)
                    }
                },
                onSeeAll = { pkg, category ->
                    nav.navigate("seeAll/${Uri.encode(pkg)}/${Uri.encode(category)}")
                },
            )
        }
        composable(Screen.Explore.route) {
            ExploreScreen(
                onMediaClick = { openDetails(nav, it) },
                onSeeAll = { pkg, category ->
                    nav.navigate("seeAll/${Uri.encode(pkg)}/${Uri.encode(category)}")
                },
                onSearch = { _, source -> nav.navigate("search/${Uri.encode(source)}") },
            )
        }
        composable("seeAll/{pkg}/{category}") { entry ->
            val pkg = Uri.decode(entry.arguments?.getString("pkg").orEmpty())
            dev.endlesssea.app.ui.explore.SeeAllScreen(
                onMediaClick = { openDetails(nav, it) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(onMediaClick = { openDetails(nav, it) })
        }
        // §recherche-source : « rechercher dans <extension> » depuis la barre du haut
        composable("search/{source}") { entry ->
            SearchScreen(
                onMediaClick = { openDetails(nav, it) },
                initialSource = entry.arguments?.getString("source") ?: "ALL",
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(onMediaClick = { openDetails(nav, it) })
        }
        composable(Screen.Downloads.route) { DownloadsScreen() }
        composable(Screen.Extensions.route) {
            ExtensionsScreen(onExplore = { nav.navigate(Screen.Explore.route) })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onOpenExtensions = { nav.navigate(Screen.Extensions.route) })
        }

        composable("details/{id}") { entry ->
            val id = Uri.decode(entry.arguments?.getString("id").orEmpty())
            DetailsScreen(
                mediaId = id,
                onBack = { nav.popBackStack() },
                onDownloadQueued = { nav.navigate(Screen.Downloads.route) },
            )
        }
    }
}
