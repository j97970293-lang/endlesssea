package dev.endlesssea.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
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
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: AppPrefs

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by prefs.themeMode.collectAsState()
            val barTabs by prefs.barTabs.collectAsState()

            EndlessSeaTheme(themeMode = themeMode) {
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

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = if (amoled) Color.Black else MaterialTheme.colorScheme.background,
                    topBar = {
                        CenterAlignedTopAppBar(
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
                    },
                    bottomBar = { EsBottomBar(nav, currentRoute = route, tabs = barTabs) },
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
