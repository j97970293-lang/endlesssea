package dev.endlesssea.app.ui.local

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFramePercent
import kotlinx.coroutines.Dispatchers

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(2)

/** Explicit video decoder: SAF providers may report application/octet-stream, not a video MIME type. */
@Composable
internal fun LocalVideoThumbnail(uri: String, bytes: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(uri, bytes) {
        ImageRequest.Builder(context).data(uri).size(320, 180)
            .memoryCacheKey("local-frame-v1:$uri:$bytes")
            .videoFramePercent(.10)
            .decoderDispatcher(thumbnailDispatcher)
            .decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            .build()
    }
    SubcomposeAsyncImage(model = request, imageLoader = dev.endlesssea.app.EsImages.imageLoader(context),
        contentDescription = "Miniature extraite de la vidéo", contentScale = ContentScale.Crop, modifier = modifier,
        loading = { ThumbnailFallback() }, error = { ThumbnailFallback() })
}

@Composable
private fun ThumbnailFallback() {
    Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Movie, "Miniature vidéo indisponible", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
