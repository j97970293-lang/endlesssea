package dev.endlesssea.app.ui.details

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.endlesssea.extensions.api.model.AudioLang
import dev.endlesssea.extensions.api.model.Quality

@Composable
internal fun BatchDownloadFilters(language: AudioLang?, quality: Quality?, onLanguage: (AudioLang?) -> Unit, onQuality: (Quality?) -> Unit) {
    Text("Langue", style = MaterialTheme.typography.titleSmall)
    Text("VF, VOSTFR ou une autre piste. Si elle n'existe pas, une autre langue téléchargeable est prise.", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = language == null, onClick = { onLanguage(null) }, label = { Text("Auto") })
        listOf(AudioLang.VF to "VF", AudioLang.VOSTFR to "VOSTFR", AudioLang.VO to "VO", AudioLang.MULTI to "MULTI", AudioLang.OTHER to "Autre").forEach { (lang, label) ->
            FilterChip(selected = language == lang, onClick = { onLanguage(lang) }, label = { Text(label) })
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Qualité", style = MaterialTheme.typography.titleSmall)
    Text("Si la qualité choisie n'existe pas, la meilleure disponible est utilisée.", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = quality == null, onClick = { onQuality(null) }, label = { Text("Meilleure") })
        listOf(Quality.Q1080, Quality.Q720, Quality.Q480, Quality.Q360).forEach { value ->
            FilterChip(selected = quality == value, onClick = { onQuality(value) }, label = { Text(value.label) })
        }
    }
}
