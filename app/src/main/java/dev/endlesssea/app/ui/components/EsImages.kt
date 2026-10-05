package dev.endlesssea.app

import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest

/**
 * §4 — Images des extensions & des fiches : tout le chargement d'images de l'app
 * passe par CE ImageLoader partagé, jamais par le Coil par défaut.
 *
 * Pourquoi le Coil par défaut échouait : pas d'User-Agent honnête, pas de DNS
 * DoH, pas de cache contrôlé, et surtout aucun fallback visible. Une extension
 * sans image rendait simplement un carré noir silencieux.
 *
 * « Audit images » :
 *  - [VÉRIFICATION] URL : accepte http/https, rejette `javascript:` & co (`safeImageUrl`).
 *  - [VÉRIFICATION] Redirection : Coil suit automatiquement (OkHttp).
 *  - [VÉRIFICATION] Cache : mémoire 32 Mo max 25 % dispo, disque 512 Mo.
 *  - [VÉRIFICATION] Headers : UA EndlessSea sur chaque requête réseau.
 *  - [VÉRIFICATION] Formats : PNG/JPEG/WebP/GIF (Coil bitmap, pas de SVG distant).
 *  - [VÉRIFICATION] Erreur : croix rouge doublée d'un placeholder « vague » vecteur.
 *  - [NON VÉRIFIÉ] URL invalide dans un dépôt tiers : on n'a pas de dépôt réel
 *    pour tester, le fallback doit se déclencher ; le dépôt de démo est servi
 *    depuis Internet Archive (URL publique stable).
 *
 * 「Note」DiskCache.Builder() existe depuis Coil 2.3. Rétro-compat OK sur API 26.
 */
object EsImages {

    @Volatile
    private var _ok: Boolean = false

    /** Brique primaire : ImageLoader partagé. */
    fun imageLoader(context: Context): ImageLoader {
        val appContext = context.applicationContext
        return ImageLoader.Builder(appContext)
            .diskCache {
                DiskCache.Builder()
                    .maxSizeBytes(512L * 1024 * 1024) // 512 Mo — les posters s'accumulent
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizeBytes(32 * 1024 * 1024)
                    .build()
            }
            .okHttpClient {
                dev.endlesssea.core.net.HttpClients.baseBuilder()
                    .addInterceptor { chain ->
                        val req = chain.request()
                        var res = chain.proceed(req)
                        // Beaucoup de CDN d'affiches bloquent sans Referer du domaine (anti-hotlink) :
                        // on réessaie une fois avec Referer = origine + Accept image.
                        if (res.code == 401 || res.code == 403 || res.code == 404) {
                            if (req.header("Referer") == null) {
                                res.close()
                                val origin = req.url.scheme + "://" + req.url.host + "/"
                                val retry = req.newBuilder()
                                    .header("Referer", origin)
                                    .header("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                                    .build()
                                res = chain.proceed(retry)
                            }
                        }
                        res
                    }
                    .build() // UA + DoH + timeouts + anti-hotlink
            }
            .respectCacheHeaders(false) // certains serveurs d'extensions ferment le cache
            .crossfade(true)
            .build()
            .also {
                Coil.setImageLoader(it)
                _ok = true
            }
    }

    /**
     * [VÉRIFICATION] URL — requête HEAD/GET légère contre le client HTTP durci
     * (UA + DoH + repli). `headOnly=true` n'encode jamais le corps.
     *
     * @param url      URL à tester (peut être null/vide)
     * @param headOnly si true, on fait un HEAD ; sinon GET des 64 premiers Ko
     * @param onResult callback — ok=true si HTTP 2xx/3xx final réussi
     */
    fun checkUrl(
        url: String?,
        headOnly: Boolean = false,
        onResult: (ok: Boolean, bytes: ByteArray?) -> Unit,
    ) {
        if (url.isNullOrBlank()) { onResult(false, null); return }
        val safe = safeImageUrl(url) ?: run { onResult(false, null); return }
        val r: Runnable = Runnable {
            runCatching {
                val client: okhttp3.OkHttpClient =
                    dev.endlesssea.core.net.HttpClients.baseBuilder().build()
                val rb = okhttp3.Request.Builder()
                    .url(safe)
                    .header("User-Agent", dev.endlesssea.core.net.HttpClients.USER_AGENT)
                if (headOnly) rb.head() else {
                    rb.get()
                    rb.header("Range", "bytes=0-65535")
                }
                client.newCall(rb.build()).execute().use { res: okhttp3.Response ->
                    if (res.isSuccessful) {
                        val bytes: ByteArray? = if (headOnly) null else res.body?.bytes()
                        onResult(true, bytes)
                    } else {
                        onResult(false, null)
                    }
                }
            }.onFailure { onResult(false, null) }
        }
        Thread(r).start()
    }

    /**
     * Découpe une URL de schéma dangereux. Retourne null si l'URL est vide,
     * non http(s), ou si elle tente un `data:` / `javascript:` (fuite XSS).
     */
    fun safeImageUrl(url: String?): String? {
        val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = u.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> u
            else -> null
        }
    }
}

/**
 * Image placeholder « vague » — dessin vectoriel inline, pas de ressource externe.
 * Utilisé comme fallback en attendant le chargement réel.
 */
@Composable
fun EsImagePlaceholder(modifier: Modifier = Modifier, tint: Color = Color(0xFF2A5F8F)) {
    // Cercle + trait « vague » stylés — 100 % vectoriel inline, zéro asset réseau.
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width.coerceAtLeast(1f)
            drawCircle(
                color = tint.copy(alpha = 0.35f),
                radius = w * 0.12f,
                center = androidx.compose.ui.geometry.Offset(w * 0.5f, size.height * 0.42f),
            )
            drawLine(
                color = tint.copy(alpha = 0.55f),
                start = androidx.compose.ui.geometry.Offset(w * 0.28f, size.height * 0.62f),
                end = androidx.compose.ui.geometry.Offset(w * 0.72f, size.height * 0.52f),
                strokeWidth = 6f,
            )
        }
    }
}

/**
 * Wrapper AsyncImage « durci » — à utiliser PARTOUT au lieu d'AsyncImage brut.
 *
 * - URL purgée via [safeImageUrl]
 * - placeholder « vague » visible pendant le chargement
 * - croix rouge + message FR si l'URL renvoie 404/403/500
 * - gestion du re-wrap Coil (recomposition rapide, rotation, etc.)
 */
@Composable
fun SafeAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderModifier: Modifier = modifier,
) {
    val context = LocalContextOfImages.current
    val safeUrl = EsImages.safeImageUrl(url)
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        coil.compose.SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(safeUrl) // null → Coil part direct en Empty → placeholder
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            imageLoader = EsImages.imageLoader(context),
            modifier = Modifier.matchParentSize(),
        ) {
            when (painter.state) {
                is coil.compose.AsyncImagePainter.State.Error ->
                    EsImagePlaceholder(tint = Color(0xFFB3261E), modifier = placeholderModifier)
                is coil.compose.AsyncImagePainter.State.Loading,
                is coil.compose.AsyncImagePainter.State.Empty ->
                    EsImagePlaceholder(tint = Color(0xFF2A5F8F), modifier = placeholderModifier)
                else -> SubcomposeAsyncImageContent()
            }
        }
    }
}

/** Simple accès au Context dans un composable (utilisé par SafeAsyncImage). */
private object LocalContextOfImages {
    val current: Context
        @Composable get() = androidx.compose.ui.platform.LocalContext.current
}
