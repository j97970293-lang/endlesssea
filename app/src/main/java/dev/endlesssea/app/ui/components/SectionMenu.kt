package dev.endlesssea.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

data class SectionMenuActions(val settings: () -> Unit = {}, val extensions: () -> Unit = {},
    val trackers: () -> Unit = {}, val logo: Int = dev.endlesssea.app.R.drawable.logo_blue_white)
val LocalSectionMenu = staticCompositionLocalOf { SectionMenuActions() }

@Composable
fun SectionMenuButton(icon: ImageVector? = null, home: Boolean = false) {
    val actions = LocalSectionMenu.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { expanded = true }, contentAlignment = Alignment.Center) {
            if (home) Image(painterResource(actions.logo), "Menu de l'accueil : paramètres, extensions et comptes", Modifier.size(40.dp))
            else Icon(icon ?: Icons.Default.Menu, "Menu de la section : paramètres, extensions et comptes", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("Paramètres") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { expanded = false; actions.settings() })
            DropdownMenuItem(text = { Text("Extensions") }, leadingIcon = { Icon(Icons.Default.Extension, null) }, onClick = { expanded = false; actions.extensions() })
            DropdownMenuItem(text = { Text("Comptes & suivi") }, leadingIcon = { Icon(Icons.Default.AccountCircle, null) }, onClick = { expanded = false; actions.trackers() })
        }
    }
}
