package dev.endlesssea.core.net

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
        "EndlessSea/0.1 (Android; +https://github.com/endlesssea) OkHttp/4"

    /** Base builder: polite defaults reused by app + per-extension clients. */
    fun baseBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
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

    /** Client with an isolated, in-memory cookie jar (one per extension at runtime). */
    fun withCookieJar(persistent: CookieManager): OkHttpClient =
        baseBuilder()
            .cookieJar(JavaNetCookieJar(persistent.apply {
                setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            }))
            .build()

    /** Verbose client for debug builds — never shipped in release. */
    fun debug(): OkHttpClient =
        baseBuilder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build()
}
