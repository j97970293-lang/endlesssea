package dev.endlesssea.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.branding.AppLogos

@Composable
internal fun LogoSettingsSection(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Logos de l'application", style = MaterialTheme.typography.titleMedium)
        Text("Choisis le logo de l'application : il s'affiche dans l'interface et comme icône du téléphone. Le lanceur peut mettre quelques secondes à actualiser l'icône.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppLogos.all.chunked(2).forEach { choices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { logo ->
                    OutlinedCard(onClick = { onSelect(logo.id) }, modifier = Modifier.weight(1f),
                        colors = CardDefaults.outlinedCardColors(containerColor = if (selected == logo.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(logo.drawable),
                                contentDescription = logo.label,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(128.dp),
                            )
                            Text(logo.label, style = MaterialTheme.typography.labelLarge)
                            Text(if (selected == logo.id) "Sélectionné" else "Choisir", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
