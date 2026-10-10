package dev.endlesssea.app.local

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * §stockage-public — où atterrissent les téléchargements.
 *
 * Objectif utilisateur : « je ne veux pas que ce soit stocké en privé » et
 * « ça doit marcher à partir d'Android 8 ». On reprend l'arborescence Aniyomi,
 * que les gestionnaires de fichiers (et l'app elle-même, hors ligne) savent lire :
 *
 * ```
 * <dossier choisi>/
 *   downloads/<Source>/<Titre de la série>/<Épisode>/<Épisode>.mp4
 *   localanime/<Titre de la série>/cover.jpg · details.json · ep01.mp4
 * ```
 *
 * Trois chemins, par ordre de préférence :
 *  1. dossier SAF choisi par l'utilisateur (API 21+, donc Android 8 inclus) ;
 *  2. dossier public « Movies/EndlessSea » en accès direct (API ≤ 28) ;
 *  3. dossier public via MediaStore (API 29+) si aucun dossier n'a été choisi.
 */
object DownloadStorage {

    const val DOWNLOADS_DIR = "downloads"
    const val LOCAL_DIR = "localanime"
    const val PUBLIC_ROOT = "EndlessSea"

    fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\u0000]"""), "_").trim().take(120).ifBlank { "sans-titre" }

    /** Racine SAF choisie par l'utilisateur, si elle est toujours accessible. */
    fun root(context: Context, rootUri: String?): DocumentFile? {
        val uri = rootUri?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uri)) }
            .getOrNull()?.takeIf { it.canWrite() }
    }

    /** Crée (ou retrouve) une chaîne de sous-dossiers. */
    fun mkdirs(parent: DocumentFile, segments: List<String>): DocumentFile? {
        var cur: DocumentFile? = parent
        for (seg in segments) {
            val name = sanitize(seg)
            val existing = cur?.findFile(name)
            cur = if (existing != null && existing.isDirectory) existing else cur?.createDirectory(name)
            if (cur == null) return null
        }
        return cur
    }

    /** Dossier public historique (visible dans tous les gestionnaires de fichiers). */
    fun publicDir(sub: List<String>): File {
        val base = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "EndlessSea",
        )
        val target = sub.fold(base) { acc, s -> File(acc, sanitize(s)) }
        target.mkdirs()
        return target
    }

    /**
     * Publie un fichier terminé à son emplacement définitif.
     * Retourne l'URI finale (content:// ou file://), ou null en cas d'échec.
     */
    fun publish(
        context: Context,
        rootUri: String?,
        relativeDirs: List<String>,
        fileName: String,
        source: File,
    ): String? {
        // §carte-sd : on tente le dossier choisi (SAF) ; si la carte refuse
        // l'écriture — cas fréquent des SD externes — on NE renvoie PAS d'échec :
        // on recopie le fichier dans le stockage public, comme le fait Aniyomi.
        val tree = root(context, rootUri)
        if (tree != null) {
            val viaTree = runCatching {
                val dir = mkdirs(tree, relativeDirs) ?: return@runCatching null
                dir.findFile(fileName)?.delete()
                val doc = dir.createFile(mimeFor(fileName), fileName) ?: return@runCatching null
                val written = context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                }
                if (written == null) {
                    runCatching { doc.delete() }
                    null
                } else {
                    doc.uri.toString()
                }
            }.getOrNull()
            if (viaTree != null) {
                source.delete()
                return viaTree
            }
            // sinon : on continue sur le stockage public (repli)
        }
        // Pas de dossier choisi (ou carte SD en lecture seule) : stockage public.
        return runCatching {
            if (Build.VERSION.SDK_INT <= 28 || Environment.isExternalStorageLegacy()) {
                val dir = publicDir(relativeDirs)
                val out = File(dir, sanitize(fileName))
                source.copyTo(out, overwrite = true)
                android.media.MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf(mimeFor(fileName)), null)
                source.delete()
                Uri.fromFile(out).toString()
            } else {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
                    put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, sanitize(fileName))
                    put(android.provider.MediaStore.Video.Media.MIME_TYPE, mimeFor(fileName))
                    put(
                        android.provider.MediaStore.Video.Media.RELATIVE_PATH,
                        (listOf(Environment.DIRECTORY_MOVIES, "EndlessSea") + relativeDirs.map { sanitize(it) })
                            .joinToString("/"),
                    )
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values,
                ) ?: return null
                try {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: throw java.io.IOException("Impossible d’ouvrir le fichier public")
                    output.use { out -> source.inputStream().use { it.copyTo(out) } }
                    val ready = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    check(context.contentResolver.update(uri, ready, null, null) > 0)
                    source.delete()
                    uri.toString()
                } catch (e: Exception) {
                    context.contentResolver.delete(uri, null, null)
                    throw e
                }
            }
        }.getOrNull()
    }

    /** Écrit un petit fichier texte (details.json) à côté de la série. */
    fun writeText(
        context: Context,
        rootUri: String?,
        relativeDirs: List<String>,
        fileName: String,
        content: String,
    ) {
        val tree = root(context, rootUri)
        runCatching {
            if (tree != null) {
                val dir = mkdirs(tree, relativeDirs) ?: return
                val existing = dir.findFile(fileName)
                val doc = existing ?: dir.createFile("application/json", fileName) ?: return
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use {
                    it.write(content.toByteArray())
                }
            } else {
                File(publicDir(relativeDirs), sanitize(fileName)).writeText(content)
            }
        }
    }

    /** Télécharge l'affiche à côté de la série (cover.jpg, format Aniyomi). */
    fun writeCover(
        context: Context,
        rootUri: String?,
        relativeDirs: List<String>,
        imageUrl: String?,
    ) {
        val url = imageUrl?.takeIf { it.startsWith("http") } ?: return
        runCatching {
            val client = dev.endlesssea.core.net.HttpClients.baseBuilder().build()
            client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { res ->
                val bytes = res.body?.bytes() ?: return
                val tree = root(context, rootUri)
                if (tree != null) {
                    val dir = mkdirs(tree, relativeDirs) ?: return
                    if (dir.findFile("cover.jpg") != null) return
                    val doc = dir.createFile("image/jpeg", "cover.jpg") ?: return
                    context.contentResolver.openOutputStream(doc.uri)?.use { it.write(bytes) }
                } else {
                    val f = File(publicDir(relativeDirs), "cover.jpg")
                    if (!f.exists()) f.writeBytes(bytes)
                }
            }
        }
    }

    private fun mimeFor(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "ts" -> "video/mp2t"
        "jpg", "jpeg" -> "image/jpeg"
        "json" -> "application/json"
        else -> "video/mp4"
    }
}
