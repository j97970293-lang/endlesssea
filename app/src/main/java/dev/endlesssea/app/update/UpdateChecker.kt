package dev.endlesssea.app.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dev.endlesssea.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Information sur une version publiée sur GitHub Releases. */
data class AppUpdateInfo(
    val tag: String,            // ex. "v0.3.0"
    val name: String,           // titre de la release
    val notes: String,          // changelog Markdown
    val apkUrl: String?,        // asset .apk direct (browser_download_url)
)

/**
 * Vérification des mises à jour (mise à jour automatique dès l'ouverture,
 * spec : « mis à jour auto ») : compare la dernière release GitHub à la version installée.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val http: OkHttpClient,
) {

    companion object {
        const val RELEASES_API =
            "https://api.github.com/repos/j97970293-lang/endlesssea/releases/latest"
        const val RELEASES_PAGE = "https://github.com/j97970293-lang/endlesssea/releases/latest"
    }

    /** Dernière version publiée, ou null si la requête échoue (hors-ligne…). */
    suspend fun latest(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(
                Request.Builder()
                    .url(RELEASES_API)
                    .header("Accept", "application/vnd.github+json")
                    .build(),
            ).execute().use { res ->
                if (!res.isSuccessful) {
                    dev.endlesssea.core.diag.EsLog.e("Update", "UpdateChecker", "GitHub API : HTTP ${res.code}")
                    return@withContext null
                }
                val body = res.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null)
                            break
                        }
                    }
                }
                AppUpdateInfo(
                    tag = json.optString("tag_name", ""),
                    name = json.optString("name", ""),
                    notes = json.optString("body", ""),
                    apkUrl = apkUrl,
                )
            }
        }.getOrNull()
    }

    /** §5 : lance le téléchargement in-app (progression visible via [UpdateDownloadState]). */
    suspend fun downloadUpdate(context: Context, info: AppUpdateInfo) =
        AppUpdateInstaller.download(context.applicationContext, info, http)

    /** `true` si [remoteTag] est strictement plus récent que la version installée. */
    fun isNewer(remoteTag: String): Boolean {
        val installed = BuildConfig.VERSION_NAME
        return compareSemver(remoteTag.trimStart('v'), installed.trimStart('v')) > 0
    }

    /** Comparaison sémantique segmentée « major.minor.patch » (segments manquants = 0). */
    fun compareSemver(a: String, b: String): Int {
        val pa = a.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}

// ---------------------------------------------------------------- état partagé

/** États du téléchargement de MAJ in-app (§5 : tout reste DANS l'app, visible). */
sealed class UpdateDl {
    data object Idle : UpdateDl()
    data class Downloading(val progress: Float, val doneKb: Long, val totalKb: Long) : UpdateDl()
    data class Ready(val file: java.io.File, val info: AppUpdateInfo) : UpdateDl()
    data class Failed(val message: String, val technical: String) : UpdateDl()
}

object UpdateDownloadState {
    val state = kotlinx.coroutines.flow.MutableStateFlow<UpdateDl>(UpdateDl.Idle)
    fun reset() { state.value = UpdateDl.Idle }
}

/**
 * §5 : téléchargement + validation + installation **entièrement dans l'application**.
 * Plus d'intent navigateur ni de bascule hors app au clic « Mettre à jour ».
 */
object AppUpdateInstaller {

    /** Lance (ou relance) le téléchargement de l'APK. Jamais appelé sur le thread UI. */
    suspend fun download(context: Context, info: AppUpdateInfo, http: OkHttpClient) =
        withContext(Dispatchers.IO) {
            val url = info.apkUrl ?: run {
                UpdateDownloadState.state.value = UpdateDl.Failed(
                    "Aucun fichier d'installation dans la release ${info.tag}.",
                    "apkUrl null",
                ); return@withContext
            }
            val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
            val target = File(dir, "endless-sea-${info.tag}.apk")
            runCatching {
                UpdateDownloadState.state.value = UpdateDl.Downloading(0f, 0, 0)
                http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    if (!res.isSuccessful) {
                        error("HTTP ${res.code} — réessaie dans quelques minutes")
                    }
                    val body = res.body ?: error("flux de téléchargement vide")
                    val total = body.contentLength()
                    val magic = java.io.ByteArrayOutputStream()
                    target.outputStream().buffered().use { out ->
                        val buf = ByteArray(65_536)
                        var done = 0L
                        var first = true
                        while (true) {
                            val n = body.byteStream().read(buf)
                            if (n < 0) break
                            if (first) {
                                first = false
                                magic.write(buf, 0, minOf(n, 4))
                            }
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                UpdateDownloadState.state.value =
                                    UpdateDl.Downloading(done.toFloat() / total, done / 1024, total / 1024)
                            }
                        }
                        out.flush()
                    }
                    if (done < 100_000) {
                        target.delete()
                        error("Fichier trop petit (${done / 1024} Ko) — page d'erreur du réseau ?")
                    }
                    if (total > 0 && done != total) {
                        target.delete()
                        error("Téléchargement incomplet (${done / 1024}/${total / 1024} Ko)")
                    }
                }
                // ---- Validation §5 : magic bytes APK (ZIP : « PK ») + package attendu
                val header = target.inputStream().use { it.readBytecode4() }
                if (header == null || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                    target.delete()
                    error("Le fichier reçu n'est pas un paquet Android valide.")
                }
                val pm = context.packageManager
                val pkgInfo = pm.getPackageArchiveInfo(target.absolutePath, 0)
                if (pkgInfo == null || pkgInfo.packageName != context.packageName) {
                    target.delete()
                    error("Le paquet téléchargé ne correspond pas à Endless Sea.")
                }
                dev.endlesssea.core.diag.EsLog.e(
                    "Update", "Installer", "Mise à jour ${info.tag} téléchargée et validée",
                )
                UpdateDownloadState.state.value = UpdateDl.Ready(target, info)
            }.onFailure { e ->
                target.delete()
                dev.endlesssea.core.diag.EsLog.e(
                    "Update", "Installer", "MAJ impossible : ${e.message}",
                    details = e.javaClass.simpleName, recoverable = true,
                )
                UpdateDownloadState.state.value = UpdateDl.Failed(
                    "Le téléchargement de la mise à jour a échoué. ${e.message.orEmpty().ifBlank { "" }}",
                    e.javaClass.simpleName + ": " + (e.message ?: ""),
                )
            }
        }

    /** Boîte d'installation du système, alimentée par FileProvider (aucune permission stockage). */
    fun install(context: Context, file: File) {
        runCatching {
            if (context.packageManager.canRequestPackageInstalls().not()) {
                android.widget.Toast.makeText(
                    context,
                    "Autorise l'installation pour Endless Sea, puis reviens.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            UpdateDownloadState.reset()
        }.onFailure { e ->
            dev.endlesssea.core.diag.EsLog.e(
                "Update", "Installer", "Installation impossible : ${e.message}",
                details = e.javaClass.simpleName, recoverable = true,
            )
            UpdateDownloadState.state.value = UpdateDl.Failed(
                "Impossible de lancer l'installation. ${e.message ?: ""}",
                e.javaClass.simpleName,
            )
        }
    }

    private fun java.io.InputStream.readBytecode4(): ByteArray? {
        val b = ByteArray(4)
        var got = 0
        while (got < 4) {
            val n = read(b, got, 4 - got)
            if (n < 0) return if (got == 0) null else b
            got += n
        }
        return b
    }
}
