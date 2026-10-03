package dev.endlesssea.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.endlesssea.app.navigation.EsBottomBar
import dev.endlesssea.app.navigation.EsNavGraph
import dev.endlesssea.app.navigation.Screen
import dev.endlesssea.app.ui.theme.EndlessSeaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EndlessSeaTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Endless Sea") },
                            actions = {
                                IconButton(onClick = { nav.navigate(Screen.Extensions.route) }) {
                                    Icon(Icons.Filled.Extension, contentDescription = "Extensions")
                                }
                                IconButton(onClick = { nav.navigate(Screen.Settings.route) }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Paramètres")
                                }
                            },
                        )
                    },
                    bottomBar = { EsBottomBar(nav, currentRoute = route) },
                ) { padding ->
                    EsNavHost(Modifier.padding(padding), nav)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun EsNavHost(modifier: Modifier, nav: androidx.navigation.NavHostController) {
    androidx.compose.foundation.layout.Box(modifier) {
        EsNavGraph(nav)
    }
}
