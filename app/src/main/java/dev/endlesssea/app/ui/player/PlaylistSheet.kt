package dev.endlesssea.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.endlesssea.app.ui.player.themes.fmtTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSheet(state: PlayerUiState, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF0A0A0A)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Playlist", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(if (state.playlistIndex >= 0) "Épisode ${state.playlistIndex + 1} sur ${state.playlist.size}" else "Aucun épisode",
                        color = Color.LightGray)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fermer", tint = Color.White) }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(state.playlist) { index, item ->
                    val current = index == state.playlistIndex
                    val position = if (current) state.positionMs else item.positionMs
                    val duration = if (current) state.durationMs else item.durationMs
                    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (current) Color(0x2600BCD4) else Color(0xFF161616))
                        .clickable(enabled = !state.loading) { onSelect(index) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(100.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Gray)
                            AsyncImage(item.thumbnailUrl, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val episode = item.episodeNumber?.let { "E${it.toInt().toString().padStart(2, '0')}" }.orEmpty()
                            val season = item.season?.let { "S${it.toString().padStart(2, '0')}" }.orEmpty()
                            Text(listOf(season + episode, if (duration > 0) fmtTime(duration) else "Durée inconnue",
                                if (current) "En cours" else if (item.watched) "Vu" else "").filter { it.isNotBlank() }.joinToString(" · "),
                                color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                            if (position > 0) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Color(0xFF00BCD4))
                        }
                        if (item.downloaded) Icon(Icons.Default.DownloadDone, "Téléchargé", tint = Color(0xFF2E8B57), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
