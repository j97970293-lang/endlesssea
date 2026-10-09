package dev.endlesssea.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun HomeTopBar(activeExtensionName: String?, onOpenSettings: () -> Unit, onOpenSearch: () -> Unit, onOpenMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        dev.endlesssea.app.ui.components.SectionMenuButton(home = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Accueil", style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(activeExtensionName?.let { "Extension : $it" } ?: "Toutes les extensions",
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Search, "Rechercher") }
        IconButton(onClick = onOpenMenu) { Icon(Icons.Default.MoreVert, "Changer de source") }
    }
}
