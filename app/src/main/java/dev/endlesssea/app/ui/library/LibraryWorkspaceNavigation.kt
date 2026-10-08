package dev.endlesssea.app.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Primary areas stay distinct from collection types and user folder categories. */
@Composable
internal fun LibraryWorkspaceNavigation(
    navigation: LibraryNavigation,
    destinations: List<Pair<String, String>>,
    onArea: (LibraryArea) -> Unit,
    onDestination: (String) -> Unit,
    onAddCategory: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryArea.entries.forEach { area ->
                FilterChip(selected = navigation.area == area,
                    onClick = { onArea(area) }, label = { Text(area.label, maxLines = 1) })
            }
        }
        if (navigation.area != LibraryArea.DOWNLOADS) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                destinations.forEach { (label, key) ->
                    FilterChip(selected = navigation.destination == key,
                        onClick = { onDestination(key) }, label = { Text(label, maxLines = 1) })
                }
                if (navigation.area == LibraryArea.FOLDERS) {
                    AssistChip(onClick = onAddCategory, label = { Text("+ Catégorie", maxLines = 1) })
                }
            }
        }
    }
}
