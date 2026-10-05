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
        return Dns { hostname ->
            runCatching { doh.lookup(hostname) }
                .getOrElse { Dns.SYSTEM.lookup(hostname) }
        }
    }
}
