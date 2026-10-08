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
    Text("Langue audio (choix strict)")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = language == null, onClick = { onLanguage(null) }, label = { Text("Toutes") })
        AudioLang.entries.forEach { lang ->
            FilterChip(selected = language == lang, onClick = { onLanguage(lang) }, label = { Text(lang.name) })
        }
    }
    Text("Qualité (choix strict)")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = quality == null, onClick = { onQuality(null) }, label = { Text("Meilleure") })
        Quality.entries.filter { it != Quality.UNKNOWN }.forEach { value ->
            FilterChip(selected = quality == value, onClick = { onQuality(value) }, label = { Text(value.label) })
        }
    }
}
