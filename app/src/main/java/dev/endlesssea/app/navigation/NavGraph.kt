package dev.endlesssea.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

val bottomItems = listOf(Screen.Home, Screen.Explore, Screen.Search, Screen.Library, Screen.Downloads)

@Composable
fun EsBottomBar(nav: NavHostController, currentRoute: String?) {
    NavigationBar {
        bottomItems.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    nav.navigate(screen.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) },
            )
        }
    }
}

@Composable
fun EsNavGraph(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(onMediaClick = { nav.navigate("details/$it") })
        }
        composable(Screen.Explore.route) { ExploreScreen(onMediaClick = { nav.navigate("details/$it") }) }
        composable(Screen.Search.route) { SearchScreen(onMediaClick = { nav.navigate("details/$it") }) }
        composable(Screen.Library.route) { LibraryScreen(onMediaClick = { nav.navigate("details/$it") }) }
        composable(Screen.Downloads.route) { DownloadsScreen() }
        composable(Screen.Extensions.route) { ExtensionsScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}
