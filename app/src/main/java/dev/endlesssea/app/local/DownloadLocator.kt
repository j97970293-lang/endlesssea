package dev.endlesssea.app.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * §emplacement : retrouve un fichier téléchargé même si l'emplacement de
 * téléchargement a changé depuis.
 *
 * Un téléchargement enregistre une URI absolue. Si l'utilisateur déplace son
 * dossier (interne → carte SD, par exemple), cette URI peut ne plus répondre.
 * On cherche alors le même nom de fichier dans tous les emplacements connus,
 * pour que lecture, pastille « hors ligne » et suppression se comportent
 * EXACTEMENT de la même façon qu'avant le changement.
 */
object DownloadLocator {

    /** Vrai si l'URI est encore lisible telle quelle. */
    fun exists(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return runCatching {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "content" -> context.contentResolver.openInputStream(uri)?.use { true } ?: false
                "file", null -> java.io.File(uri.path ?: uriString).let { it.isFile && it.canRead() }
                else -> false
            }
        }.getOrDefault(false)
    }

    /**
     * URI réellement lisible pour ce téléchargement : l'originale si elle
     * répond, sinon le même nom de fichier retrouvé dans un emplacement connu.
     */
    fun resolve(context: Context, targetUri: String?, fileName: String, roots: List<String>): String? {
        if (exists(context, targetUri)) return targetUri
        val wanted = fileName.removeSuffix(".part")
        roots.filter { it.isNotBlank() }.forEach { root ->
            val found = runCatching { search(context, Uri.parse(root), wanted, 0) }.getOrNull()
            if (found != null) return found
        }
        return targetUri
    }

    private fun search(context: Context, treeUri: Uri, fileName: String, depth: Int): String? {
        if (depth > 3) return null
        val doc = runCatching {
            if (android.provider.DocumentsContract.isTreeUri(treeUri)) {
                DocumentFile.fromTreeUri(context, treeUri)
            } else {
                DocumentFile.fromSingleUri(context, treeUri)
            }
        }.getOrNull() ?: return null
        doc.listFiles().forEach { child ->
            if (child.isDirectory) {
                search(context, child.uri, fileName, depth + 1)?.let { return it }
            } else if (child.name.equals(fileName, ignoreCase = true)) {
                return child.uri.toString()
            }
        }
        return null
    }
}
