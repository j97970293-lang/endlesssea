package dev.endlesssea.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.search.SearchItemUi

/** Titles never share the poster's badge area. One status line maximum. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CatalogPoster(item: SearchItemUi, onClick: () -> Unit, offline: Boolean = false,
    caption: String? = null, videoPoster: Boolean = false, onLongClick: (() -> Unit)? = null) {
    val downloaded by DownloadedRegistry.ids.collectAsState()
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f/3f).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            Icon(Icons.Default.Movie,null,Modifier.align(Alignment.Center).size(36.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            if(videoPoster && item.posterUrl != null) dev.endlesssea.app.ui.local.LocalVideoThumbnail(item.posterUrl,0L,Modifier.fillMaxSize())
            else coil.compose.AsyncImage(item.posterUrl, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (offline || item.id in downloaded) Surface(Modifier.align(Alignment.TopEnd).padding(7.dp),
                shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.DownloadDone, "Disponible hors ligne", Modifier.padding(6.dp).size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(item.title, Modifier.padding(top = 9.dp), style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        if (!caption.isNullOrBlank()) Text(caption, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
