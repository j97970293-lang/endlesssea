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

/**
 * Télécharge l'APK via le gestionnaire système → notification « toucher pour installer ».
 *
 * §5 spec : jamais de crash. Dossier PRIVÉ de l'app (aucune permission de stockage requise,
 * même sur Android 8) + repli « ouvrir la page Releases » au moindre échec.
 */
object AppUpdateInstaller {

    fun download(context: Context, info: AppUpdateInfo) {
        val url = info.apkUrl ?: run {
            openInBrowser(context); return
        }
        runCatching {
            if (context.packageManager.canRequestPackageInstalls().not()) {
                // Guider l'utilisateur vers l'autorisation « Sources inconnues » (Android 8+).
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: error("gestionnaire de téléchargement système indisponible")
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Endless Sea ${info.tag}")
                .setDescription("Téléchargement de la mise à jour — touchez la notification pour installer.")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "endless-sea-${info.tag}.apk")
            dm.enqueue(request)
            dev.endlesssea.core.diag.EsLog.e("Update", "Installer", "Téléchargement ${info.tag} démarré")
        }.onFailure { e ->
            dev.endlesssea.core.diag.EsLog.e(
                "Update", "Installer", "Téléchargement impossible : ${e.message}",
                details = e.javaClass.simpleName, recoverable = true,
            )
            android.widget.Toast.makeText(
                context, "Mise à jour : ouverture de la page de téléchargement…",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            openInBrowser(context)
        }
    }

    private fun openInBrowser(context: Context) {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    Uri.parse(UpdateChecker.RELEASES_PAGE),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
