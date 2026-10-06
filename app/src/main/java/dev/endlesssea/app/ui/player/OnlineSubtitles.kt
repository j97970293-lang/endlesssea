package dev.endlesssea.app.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * §sous-titres-en-ligne — recherche de sous-titres sur l'API publique
 * d'OpenSubtitles (point d'entrée REST historique, sans clé d'API).
 *
 * Volontairement tolérant : en cas d'échec réseau ou de format inattendu, la
 * liste revient vide — le lecteur affiche simplement « aucun résultat ».
 */
data class OnlineSubtitle(
    val name: String,
    val lang: String,
    val downloadUrl: String,
    val format: String,
)

object OnlineSubtitles {

    private const val BASE = "https://rest.opensubtitles.org/search"

    /** Recherche par titre ; [langs] au format ISO 639-2 (« fre,eng »). */
    suspend fun search(query: String, langs: String = "fre,eng"): List<OnlineSubtitle> =
        withContext(Dispatchers.IO) {
            val clean = query.trim().replace(Regex("""\.[a-zA-Z0-9]{2,4}$"""), "")
                .replace(Regex("""[._]+"""), " ")
                .trim()
            if (clean.isBlank()) return@withContext emptyList()
            val url = "$BASE/query-" + android.net.Uri.encode(clean) +
                "/sublanguageid-" + langs
            val body = runCatching {
                val client = dev.endlesssea.core.net.HttpClients.baseBuilder().build()
                val req = okhttp3.Request.Builder()
                    .url(url)
                    // l'API exige un User-Agent déclaré
                    .header("User-Agent", "TemporaryUserAgent")
                    .build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.string()
                }
            }.getOrNull() ?: return@withContext emptyList()

            runCatching {
                val arr = org.json.JSONArray(body)
                buildList {
                    for (i in 0 until minOf(arr.length(), 30)) {
                        val o = arr.optJSONObject(i) ?: continue
                        val link = o.optString("SubDownloadLink")
                        if (link.isBlank()) continue
                        add(
                            OnlineSubtitle(
                                name = o.optString("SubFileName").ifBlank { "Sous-titre" },
                                lang = o.optString("LanguageName").ifBlank { o.optString("SubLanguageID") },
                                downloadUrl = link,
                                format = o.optString("SubFormat").ifBlank { "srt" },
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

    /** Télécharge (et dézippe si besoin) un sous-titre dans le cache ; renvoie le chemin. */
    suspend fun download(
        context: android.content.Context,
        sub: OnlineSubtitle,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val client = dev.endlesssea.core.net.HttpClients.baseBuilder().build()
            val req = okhttp3.Request.Builder()
                .url(sub.downloadUrl)
                .header("User-Agent", "TemporaryUserAgent")
                .build()
            client.newCall(req).execute().use { r ->
                val stream = r.body?.byteStream() ?: return@withContext null
                val dir = java.io.File(context.cacheDir, "subs").apply { mkdirs() }
                val out = java.io.File(dir, "sub_" + System.currentTimeMillis() + "." + sub.format)
                // Les liens « download » sont gzippés.
                val input = runCatching { java.util.zip.GZIPInputStream(stream) }
                    .getOrElse { stream }
                out.outputStream().use { o -> input.copyTo(o) }
                if (out.length() <= 0) null else android.net.Uri.fromFile(out).toString()
            }
        }.getOrNull()
    }
}
