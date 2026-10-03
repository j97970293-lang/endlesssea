package dev.endlesssea.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Paramètres : stockage (SAF), téléchargement (réseau/segments), lecteur (vitesse,
 * sous-titres), genres (spec §9 : ajouter/renommer/réordonner/masquer), thème, sauvegarde.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item { SectionHeader("Stockage") }
        item {
            SettingRow(
                title = "Emplacement des téléchargements",
                subtitle = state.downloadPathDisplay,   // ex. "/Téléchargements/EndlessSea/"
                onClick = { viewModel.chooseStorage() },
            )
        }

        item { SectionHeader("Téléchargement") }
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
                title = "Connexions parallèles",
                subtitle = "${state.partsPerTask} segments par fichier · ${state.parallelTasks} tâches simultanées",
                onClick = { /* dialogue de réglages numériques */ },
            )
        }

        item { SectionHeader("Lecteur") }
        item {
            SettingRow(
                title = "Vitesse de lecture par défaut",
                subtitle = "${state.defaultSpeed}×",
                onClick = { },
            )
        }
        item {
            SettingSwitch(
                title = "Reprise automatique",
                subtitle = "Mémoriser la position de lecture (reprendre là où j'en étais)",
                checked = state.autoResume,
                onChange = viewModel::setAutoResume,
            )
        }

        item { SectionHeader("Genres") }
        state.genres.forEach { genre ->
            item {
                SettingSwitch(
                    title = genre,
                    subtitle = "visible dans l'accueil et les filtres",
                    checked = true,
                    onChange = { viewModel.toggleGenre(genre, it) },
                )
            }
        }
        item {
            SettingRow(title = "Gérer les genres", subtitle = "ajouter · renommer · réordonner · supprimer", onClick = { })
        }

        item { SectionHeader("Sauvegarde") }
        item {
            SettingRow(title = "Exporter la bibliothèque", subtitle = "JSON + références SAF", onClick = { })
        }
        item {
            SettingRow(title = "Importer une sauvegarde", subtitle = null, onClick = { })
        }

        item { SectionHeader("À propos") }
        item {
            SettingRow(
                title = "Endless Sea 0.1.0",
                subtitle = "GPL-3.0 · Aucune source incluse · github.com/<org>/endless-sea",
                onClick = { },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    HorizontalDivider()
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
