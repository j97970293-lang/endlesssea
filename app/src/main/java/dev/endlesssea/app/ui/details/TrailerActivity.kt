package dev.endlesssea.app.ui.details

import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    private var fullScreen by mutableStateOf(false)
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var failure by mutableStateOf<String?>(null)

    private fun applyFullScreen(enabled: Boolean) {
        fullScreen = enabled
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun exitCustomView() {
        customView?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        customView = null
        val callback = customViewCallback
        customViewCallback = null
        callback?.onCustomViewHidden()
        applyFullScreen(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) exitCustomView()
                else if (fullScreen) applyFullScreen(false)
                else finish()
            }
        })
        val url = intent.getStringExtra("url").orEmpty()
        val embed = TrailerUrls.embed(url)
        setContent {
            EndlessSeaTheme {
                Surface {
                    Column(Modifier.fillMaxSize()) {
                        if (!fullScreen) TextButton(onClick = { finish() }) { Text("Retour à la fiche") }
                        when {
                            embed != null -> AndroidView(factory = { context ->
                                WebView(context).apply {
                                    webView = this
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                            request.isForMainFrame && !request.url.toString().startsWith(TrailerUrls.embedBase(embed))
                                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                                            if (request.isForMainFrame) failure = "Lecture indisponible : ${error.description}"
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                            if (customView != null) { callback.onCustomViewHidden(); return }
                                            customView = view
                                            customViewCallback = callback
                                            view.setBackgroundColor(android.graphics.Color.BLACK)
                                            (window.decorView as android.view.ViewGroup).addView(view,
                                                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                                            applyFullScreen(true)
                                        }
                                        override fun onHideCustomView() = exitCustomView()
                                    }
                                    loadDataWithBaseURL(TrailerUrls.embedBase(embed), TrailerUrls.embedDocument(embed), "text/html", "utf-8", null)
                                }
                            }, modifier = Modifier.fillMaxWidth().weight(1f))
                            TrailerUrls.isDirect(url) -> AndroidView(factory = { context ->
                                PlayerView(context).apply {
                                    setFullscreenButtonClickListener { applyFullScreen(!fullScreen) }
                                    player = ExoPlayer.Builder(context).build().also {
                                        engine = it
                                        it.addListener(object : androidx.media3.common.Player.Listener {
                                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                                failure = "Lecture indisponible : " + error.errorCodeName
                                            }
                                        })
                                        it.setMediaItem(MediaItem.fromUri(url))
                                        it.prepare()
                                        it.playWhenReady = true
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth().weight(1f))
                            else -> Text("Cette source de bande-annonce n'est pas prise en charge.", modifier = Modifier.padding(16.dp))
                        }
                        failure?.let {
                            Text(it, modifier = Modifier.padding(12.dp))
                            TextButton(onClick = {
                                val page = embed ?: return@TextButton
                                failure = null
                                webView?.loadDataWithBaseURL(TrailerUrls.embedBase(page), TrailerUrls.embedDocument(page), "text/html", "utf-8", null)
                            }) { Text("Réessayer la bande-annonce") }
                        }
                        if (embed != null && !fullScreen) Text("Lecture dans l'application. Si l'hébergeur bloque l'intégration, réessayez.", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
    override fun onPause() { engine?.pause(); webView?.onPause(); webView?.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.pause();});", null); super.onPause() }
    override fun onResume() { super.onResume(); webView?.onResume() }
    override fun onDestroy() {
        exitCustomView()
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
                id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://www.youtube-nocookie.com/embed/$it?autoplay=1&playsinline=1&rel=0&modestbranding=1" }
            "vimeo.com", "www.vimeo.com", "player.vimeo.com" -> "https://player.vimeo.com/video/$id"
            else -> id.takeIf { it.matches(Regex("[A-Za-z0-9]+")) }?.let { "https://www.dailymotion.com/embed/video/$it" }
        }
    }
    fun youtubeId(url: String): String? {
        val embed = embed(url) ?: return null
        if (!embed.contains("youtube")) return null
        return embed.substringAfter("/embed/", "").substringBefore('?')
            .takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }

    fun thumbnail(url: String): String? = youtubeId(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

    fun embedDocument(embedUrl: String): String {
        val src = embedUrl.replace("&", "&amp;").replace("\"", "")
        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style></head>" +
            "<body><iframe src=\"$src\" allow=\"autoplay; encrypted-media; fullscreen; picture-in-picture\" allowfullscreen></iframe></body></html>"
    }

    fun embedBase(embedUrl: String): String = when {
        embedUrl.contains("youtube") -> "https://www.youtube-nocookie.com"
        embedUrl.contains("vimeo") -> "https://player.vimeo.com"
        else -> "https://www.dailymotion.com"
    }

    fun isDirect(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host != null &&
            uri.path.orEmpty().substringAfterLast('.').lowercase() in setOf("mp4", "webm", "m3u8", "mpd", "mkv")
    }
}
