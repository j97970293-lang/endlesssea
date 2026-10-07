package dev.endlesssea.app.ui.tracking

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EpisodeMatchingControls(
    enabled: Boolean, season: Int?, seasons: List<Int?>,
    onChange: (Boolean, Int?) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Associer les épisodes automatiquement", Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { onChange(it, season) })
        }
        Text(
            "À 90 % de lecture, le numéro d'épisode devient la progression du tracker. " +
                "Choisis uniquement la saison correspondant au titre distant, avec des numéros commençant à 1. " +
                "Les spéciaux et numéros inconnus sont ignorés. Désactivé : suivi manuel uniquement.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (enabled) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (seasons + season).distinct().sortedWith(compareBy { it ?: 0 }).forEach { s ->
                    FilterChip(selected = season == s, onClick = { onChange(true, s) },
                        label = { Text(s?.let { "Saison $it" } ?: "Sans saison renseignée") })
                }
            }
        }
    }
}
