package dev.endlesssea.core.net

import java.net.InetAddress
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

/**
 * §9 « Paramètres → Réseau → DNS » : DNS système (défaut), DNS-over-HTTPS
 * Cloudflare ou Google, **avec repli automatique** sur le résolveur système —
 * un DoH en panne ne casse jamais la connexion.
 *
 * Appliqué à la prochaine création du client HTTP (redémarrage de l'app).
 */
object EsNet {

    /** "system" (défaut) | "cloudflare" | "google". Mis à jour par les réglages. */
    @JvmField
    @Volatile
    var dnsMode: String = "system"

    /** Résolveur pour le mode choisi ; null = garder le DNS système (défaut OkHttp). */
    val dns: Dns? get() = when (dnsMode) {
        "cloudflare" -> buildDoh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1")
        "google" -> buildDoh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4")
        else -> null
    }

    private fun buildDoh(url: String, vararg bootstrapIps: String): Dns {
        val bootstrap = bootstrapIps.mapNotNull { ip ->
            runCatching { InetAddress.getByName(ip) }.getOrNull()
        }
        val plain = OkHttpClient.Builder().build() // sans DNS personnalisé (évite la boucle)
        val doh = DnsOverHttps.Builder()
            .client(plain)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(bootstrap)
            .resolvePrivateAddresses(false)
            .build()
        // Repli : si DoH échoue → DNS système. Jamais de connexion cassée.
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                runCatching { doh.lookup(hostname) }
                    .getOrElse { Dns.SYSTEM.lookup(hostname) }
        }
    }

    // ------------------------------------------------- §taille-avant-téléchargement

    private val probeClient: OkHttpClient by lazy {
        HttpClients.baseBuilder().build()
    }

    private fun request(url: String, headers: Map<String, String>) =
        okhttp3.Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }

    /**
     * Taille d'une ressource en octets, ou null si le serveur ne la déclare pas.
     * HEAD d'abord ; si le serveur le refuse (405/501) on retente un GET
     * `Range: bytes=0-0` dont l'en-tête `Content-Range` porte la taille totale.
     */
    fun contentLength(url: String, headers: Map<String, String> = emptyMap()): Long? {
        runCatching {
            probeClient.newCall(request(url, headers).head().build()).execute().use { res ->
                val len = res.header("Content-Length")?.toLongOrNull()
                if (res.isSuccessful && len != null && len > 0) return len
            }
        }
        runCatching {
            val req = request(url, headers).header("Range", "bytes=0-0").get().build()
            probeClient.newCall(req).execute().use { res ->
                val range = res.header("Content-Range") ?: return@use
                return range.substringAfter('/', "").trim().toLongOrNull()
            }
        }
        return null
    }

    /** Corps texte d'une URL (playlists HLS), ou null en cas d'échec. */
    fun text(url: String, headers: Map<String, String> = emptyMap()): String? = runCatching {
        probeClient.newCall(request(url, headers).get().build()).execute().use { res ->
            if (res.isSuccessful) res.body?.string() else null
        }
    }.getOrNull()
}
