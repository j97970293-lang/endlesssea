package dev.endlesssea.app.ui.details

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.endlesssea.app.ui.theme.EndlessSeaTheme
import java.net.URI

/** Lecteur distinct : ne remplace pas la file du lecteur principal. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TrailerActivity : ComponentActivity() {
    private var engine: ExoPlayer? = null
    private var webView: WebView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url").orEmpty()
        val embed = TrailerUrls.embed(url)
        setContent {
            EndlessSeaTheme {
                Surface {
                    Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = { finish() }) { Text("Retour à la fiche") }
                        when {
                            embed != null -> AndroidView(factory = { context ->
                                WebView(context).apply {
                                    webView = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = true
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                            request.isForMainFrame && request.url.toString() != embed
                                    }
                                    loadUrl(embed)
                                }
                            }, modifier = Modifier.fillMaxWidth().weight(1f))
                            TrailerUrls.isDirect(url) -> AndroidView(factory = { context ->
                                PlayerView(context).apply {
                                    player = ExoPlayer.Builder(context).build().also {
                                        engine = it
                                        it.setMediaItem(MediaItem.fromUri(url))
                                        it.prepare()
                                        it.playWhenReady = true
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth().weight(1f))
                            else -> Text("Cette source de bande-annonce n'est pas prise en charge.", modifier = Modifier.padding(16.dp))
                        }
                        if (embed != null) Text("La lecture dépend de l'autorisation d'intégration de l'hébergeur.", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
    override fun onPause() { engine?.pause(); webView?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); webView?.onResume() }
    override fun onDestroy() {
        engine?.release()
        webView?.apply { stopLoading(); destroy() }
        webView = null
        super.onDestroy()
    }
}

/** Validation exacte de l'hôte : youtube.com.evil est refusé. */
internal object TrailerUrls {
    fun embed(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty().trim('/').split('/')
        val id = when (host) {
            "youtu.be" -> path.firstOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com" -> {
                if (path.firstOrNull() in listOf("embed", "shorts", "v")) path.getOrNull(1)
                else uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.substringAfter('=')
            }
            "vimeo.com", "www.vimeo.com", "player.vimeo.com" -> path.lastOrNull()?.takeIf { it.matches(Regex("[0-9]+")) }
            "dailymotion.com", "www.dailymotion.com" -> path.getOrNull(1)?.substringBefore('_')
            "dai.ly" -> path.firstOrNull()
            else -> null
        } ?: return null
        return when (host) {
            "youtu.be", "youtube.com", "www.youtube.com", "m.youtube.com" ->
                id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://www.youtube.com/embed/$it?playsinline=1&rel=0" }
            "vimeo.com", "www.vimeo.com", "player.vimeo.com" -> "https://player.vimeo.com/video/$id"
            else -> id.takeIf { it.matches(Regex("[A-Za-z0-9]+")) }?.let { "https://www.dailymotion.com/embed/video/$it" }
        }
    }
    fun isDirect(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host != null &&
            uri.path.orEmpty().substringAfterLast('.').lowercase() in setOf("mp4", "webm", "m3u8", "mpd", "mkv")
    }
}
