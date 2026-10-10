package dev.endlesssea.extensions.loader

import android.content.Context
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader
import dev.endlesssea.extensions.api.EsExtension
import dev.endlesssea.extensions.api.ExtensionContext
import dev.endlesssea.extensions.api.EsRequest
import dev.endlesssea.extensions.api.EsResponse
import dev.endlesssea.extensions.api.ExtensionHttpClient
import dev.endlesssea.extensions.api.manifest.ExtensionManifest
import dev.endlesssea.extensions.api.manifest.ManifestParser
import dev.endlesssea.extensions.api.model.API_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/**
 * Installs, verifies and instantiates extensions (docs/en/04).
 *
 * Container: `.esx` = APK-shaped zip (classes.dex + assets/extension.json) stored in the
 * app's private dir and loaded via [PathClassLoader] — never installed system-wide.
 *
 * Verification layers:
 *  1. SHA-256 of the downloaded package == repo index `sha256`
 *  2. extension.apiVersion == app [API_VERSION] & minAppVersion gate
 *  3. signing-cert digest pinned at install; updates with a different signer are rejected
 */
class ExtensionLoader(
    private val context: Context,
    private val http: OkHttpClient,
    private val storeDir: File = File(context.filesDir, "extensions"),
    /** Réglages utilisateur par extension : (id extension) -> map clé/valeur. */
    private val settingsProvider: (String) -> Map<String, String> = { emptyMap() },
) {

    sealed interface InstallResult {
        data class Success(val extension: EsExtension, val manifest: ExtensionManifest) : InstallResult
        data class Rejected(val reason: String) : InstallResult
    }

    /** Downloads [url] (+expected sha256), verifies, instantiates the declared entry point. */
    suspend fun install(
        url: String,
        expectedSha256: String,
        pinnedCertSha256: String?,
        locale: String,
    ): InstallResult = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            http.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) return@withContext reject("http ${res.code}")
                res.body?.bytes() ?: return@withContext reject("http ${res.code} : corps vide")
            }
        }.getOrElse { return@withContext reject("téléchargement impossible : ${it.javaClass.simpleName}") }
        val digest = sha256(bytes)
        if (!digest.equals(expectedSha256, ignoreCase = true)) {
            return@withContext reject("checksum mismatch")
        }

        val tmp = File(storeDir, "download-${digest.take(12)}.esx").apply {
            parentFile?.mkdirs(); writeBytes(bytes)
        }

        val (manifest, cert) = inspectPackage(tmp)
            ?: return@withContext reject("invalid .esx package")

        if (manifest.apiVersion != API_VERSION) {
            tmp.delete(); return@withContext reject("apiVersion ${manifest.apiVersion} != $API_VERSION")
        }
        if (pinnedCertSha256 != null && cert != null && !pinnedCertSha256.equals(cert, true)) {
            tmp.delete(); return@withContext reject("signer changed (reinstall intentionally)")
        }

        val destDir = File(storeDir, "${manifest.id}/${manifest.version}").apply { mkdirs() }
        val dest = File(destDir, "${manifest.id}.esx")
        tmp.renameTo(dest)

        runCatching { instantiate(dest, manifest, locale) }.fold(
            onSuccess = { InstallResult.Success(it, manifest) },
            onFailure = { reject("load failed: ${it.javaClass.simpleName}") },
        )
    }

    /**
     * Installe un paquet `.esx` LOCAL (choisi via le sélecteur de fichiers) :
     * mêmes vérifications que [install] hors téléchargement et checksum externe
     * (l'intégrité est assurée par la copie + l'analyse ZIP + la vérification d'API).
     */
    suspend fun installLocal(src: File, locale: String): InstallResult = withContext(Dispatchers.IO) {
        val (manifest, _) = inspectPackage(src)
            ?: return@withContext reject("paquet .esx invalide (assets/extension.json absent ou illisible)")

        if (manifest.apiVersion != API_VERSION) {
            return@withContext reject("apiVersion ${manifest.apiVersion} ≠ $API_VERSION (extension incompatible avec cette version de l'app)")
        }

        val destDir = File(storeDir, "${manifest.id}/${manifest.version}").apply { mkdirs() }
        val dest = File(destDir, "${manifest.id}.esx")
        runCatching { src.copyTo(dest, overwrite = true) }
            .getOrElse { return@withContext reject("copie impossible : ${it.javaClass.simpleName}") }

        runCatching { instantiate(dest, manifest, locale) }.fold(
            onSuccess = { InstallResult.Success(it, manifest) },
            onFailure = { reject("chargement échoué : ${it.javaClass.simpleName}") },
        )
    }

    private fun reject(reason: String): InstallResult.Rejected {
        dev.endlesssea.core.diag.EsLog.e("Extension", "ExtensionLoader", "Installation refusée", reason.take(300))
        return InstallResult.Rejected(reason)
    }

    /** Re-instantiates a previously installed extension (fast path, no network). */
    fun instantiate(pkgFile: File, manifest: ExtensionManifest, locale: String): EsExtension {
        val entry = manifest.entryClass ?: error("declarative provider, use JsonProviderEngine")
        val loader = PathClassLoader(pkgFile.absolutePath, EsExtension::class.java.classLoader)
        val clazz = loader.loadClass(entry)
        val ctx = ExtensionContext(HttpFacade(context), File(storeDir, "${manifest.id}/files").apply { mkdirs() }, locale)
            .apply {
                settings = settingsProvider(manifest.id) +
                    mapOf("app.pref_lang" to AppEnv.preferredAudioLang)
                cacheDir = File(storeDir, "${manifest.id}/cache").apply { mkdirs() }
            }
        // Convention: provider constructors accept (ExtensionContext) or nothing.
        val instance = runCatching {
            clazz.getConstructor(ExtensionContext::class.java).newInstance(ctx)
        }.getOrElse { clazz.getDeclaredConstructor().newInstance() }
        require(instance is EsExtension) { "$entry does not implement EsExtension" }
        return instance
    }

    /** Reads assets/extension.json + signer digest from the package. */
    fun inspectPackage(pkg: File): Pair<ExtensionManifest, String?>? = runCatching {
        ZipFile(pkg).use { zip ->
            val entry = zip.getEntry("assets/extension.json") ?: return null
            val text = zip.getInputStream(entry).bufferedReader().readText()
            ManifestParser.parseManifest(text) to signerDigest(pkg)
        }
    }.onFailure {
        dev.endlesssea.core.diag.EsLog.e("Extension", "ExtensionLoader", "Paquet .esx illisible", it.javaClass.simpleName)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signerDigest(pkg: File): String? = runCatching {
        val pm: PackageManager = context.packageManager
        val info = if (android.os.Build.VERSION.SDK_INT >= 28) {
            pm.getPackageArchiveInfo(pkg.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageArchiveInfo(pkg.absolutePath, PackageManager.GET_SIGNATURES)
                ?.signatures?.firstOrNull()
        } ?: return null
        val cert = CertificateFactory.getInstance("X509")
            .generateCertificate(info.toByteArray().inputStream()) as X509Certificate
        sha256(cert.encoded)
    }.getOrNull()

    /**
     * HTTP facade des extensions. Le client partage le pont de cookies WebView
     * avec le reste de l'app : une vérification anti-bot résolue une fois vaut
     * aussi pour les retries d'extension et pour Coil.
     */
    private class HttpFacade(private val context: Context) : ExtensionHttpClient {
        private val client = dev.endlesssea.core.net.HttpClients.baseBuilder().build()
        override val userAgent: String = dev.endlesssea.core.net.HttpClients.USER_AGENT

        override suspend fun execute(request: EsRequest): EsResponse = withContext(Dispatchers.IO) {
            val body = request.body?.let { okhttp3.RequestBody.create(null, it) }
            val req = okhttp3.Request.Builder().url(request.url)
                .method(request.method.name, body)
                .apply { request.headers.forEach { (k, v) -> header(k, v) } }
                .build()
            try {
                client.newCall(req).execute().use { res ->
                    if (res.code >= 500) {
                        dev.endlesssea.core.diag.EsLog.e("Extension", "HttpFacade", "HTTP ${res.code}", recoverable = true)
                    }
                    EsResponse(
                        code = res.code,
                        body = res.body?.string().orEmpty(),
                        headers = res.headers.toMultimap().mapValues { it.value.firstOrNull().orEmpty() },
                        finalUrl = res.request.url.toString(),
                    )
                }
            } catch (e: Exception) {
                dev.endlesssea.core.diag.EsLog.e("Extension", "HttpFacade", "Requête extension échouée", e.javaClass.simpleName, recoverable = true)
                throw e
            }
        }

        override fun dumpCookies(host: String): Map<String, String> =
            dev.endlesssea.core.net.HttpClients.WebViewCookieJar.dump(host)
    }

    companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun sha256(file: File): String = FileInputStream(file).use {
            MessageDigest.getInstance("SHA-256").apply {
                val buf = ByteArray(64 * 1024)
                while (true) { val n = it.read(buf); if (n < 0) break; update(buf, 0, n) }
            }.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
