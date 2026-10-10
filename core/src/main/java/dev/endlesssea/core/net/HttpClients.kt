package dev.endlesssea.core.net

import android.webkit.CookieManager as WebCookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP plumbing (docs/en/02). Extensions NEVER create their own OkHttp clients;
 * the loader hands each extension an ExtensionHttpClient facade over this pool.
 */
object HttpClients {

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 EndlessSea/0.33"

    /**
     * Pont bidirectionnel WebView ↔ OkHttp.
     *
     * Une vérification Cloudflare/Turnstile est résolue dans un WebView Android,
     * dont les cookies vivent dans [android.webkit.CookieManager]. OkHttp et Coil
     * utilisaient auparavant des jars séparés : le retry repartait donc sans
     * `cf_clearance` et les posters protégés restaient en 403. Ce jar unique est
     * installé sur tous les clients créés par [baseBuilder].
     */
    object WebViewCookieJar : CookieJar {
        private fun manager(): WebCookieManager? = runCatching {
            WebCookieManager.getInstance().apply { setAcceptCookie(true) }
        }.getOrNull()

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val header = runCatching { manager()?.getCookie(url.toString()) }.getOrNull().orEmpty()
            if (header.isBlank()) return emptyList()
            return header.split(';').mapNotNull { pair ->
                Cookie.parse(url, pair.trim())
            }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val manager = manager() ?: return
            cookies.forEach { cookie ->
                runCatching { manager.setCookie(url.toString(), cookie.toString()) }
            }
            runCatching { manager.flush() }
        }

        /** Cookies visibles pour le domaine, au format attendu par ExtensionHttpClient. */
        fun dump(hostOrUrl: String): Map<String, String> {
            val base = when {
                hostOrUrl.startsWith("http://") || hostOrUrl.startsWith("https://") -> hostOrUrl
                else -> "https://${hostOrUrl.trim().trimEnd('/')}/"
            }
            val header = runCatching { manager()?.getCookie(base) }.getOrNull().orEmpty()
            if (header.isBlank()) return emptyMap()
            return buildMap {
                header.split(';').forEach { raw ->
                    val part = raw.trim()
                    val split = part.indexOf('=')
                    if (split > 0) put(part.substring(0, split).trim(), part.substring(split + 1).trim())
                }
            }
        }
    }

    /** Base builder: polite defaults reused by app + per-extension clients. */
    fun baseBuilder(): OkHttpClient.Builder {
        // §serveurs-lents : OkHttp limite par défaut à 5 requêtes simultanées PAR HÔTE
        // (et 64 au total) — la résolution des serveurs d'un épisode, qui tape
        // souvent 10-20 URLs du même domaine, se retrouvait sérialisée. On élargit
        // le dispatcher et le pool, et on échoue plus vite sur un hôte mort.
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 96
            maxRequestsPerHost = 24
        }
        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(okhttp3.ConnectionPool(32, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cookieJar(WebViewCookieJar)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
            }
        // §9 DNS-over-HTTPS (bypass des blocages DNS côté FAI) avec replis sûrs.
        EsNet.dns?.let { builder.dns(it) }
        return builder
    }

    /** Client avec jar Java explicite pour les rares intégrations qui l'exigent. */
    fun withCookieJar(persistent: CookieManager): OkHttpClient =
        baseBuilder()
            .cookieJar(JavaNetCookieJar(persistent.apply {
                setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            }))
            .build()

    /** Client verbeux de diagnostic (les builds release retirent les logs via R8). */
    fun debug(): OkHttpClient =
        baseBuilder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build()
}
