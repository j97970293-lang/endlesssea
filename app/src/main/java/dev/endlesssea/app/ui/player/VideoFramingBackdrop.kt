package dev.endlesssea.app.ui.player

import android.app.ActivityManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.endlesssea.app.ui.local.LocalVideoThumbnail

/**
 * Optional backdrop for the no-crop framing mode. The foreground PlayerView stays FIT,
 * so every source pixel remains visible; this low-resolution still only fills the bars.
 * Blur is skipped on low-RAM and pre-Android-12 devices to keep the mode lightweight.
 */
@Composable
internal fun VideoFramingBackdrop(thumbnailUrl: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lowRam = remember(context) {
        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.isLowRamDevice == true
    }
    val useBlur = !lowRam && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val thumbnail = thumbnailUrl?.takeIf(String::isNotBlank)
    val localUri = remember(thumbnail) {
        thumbnail?.let { runCatching { Uri.parse(it).scheme in setOf("content", "file") }.getOrDefault(false) } == true
    }
    val remoteRequest = remember(context, thumbnail, localUri) {
        thumbnail?.takeUnless { localUri }?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(480, 270)
                .crossfade(false)
                .build()
        }
    }
    val imageModifier = Modifier
        .fillMaxSize()
        .graphicsLayer(scaleX = 1.10f, scaleY = 1.10f)
        .then(if (useBlur) Modifier.blur(28.dp) else Modifier)

    Box(modifier.clipToBounds()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0xFF202333), Color(0xFF090A0E), Color(0xFF171923)),
                ),
            ),
        )
        if (thumbnail != null) {
            if (localUri) {
                LocalVideoThumbnail(uri = thumbnail, bytes = 0L, modifier = imageModifier)
            } else if (remoteRequest != null) {
                AsyncImage(
                    model = remoteRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier,
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Color.Black.copy(alpha = if (useBlur) 0.48f else 0.60f),
                ),
            )
        }
    }
}
