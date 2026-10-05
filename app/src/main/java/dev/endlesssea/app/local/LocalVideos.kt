package dev.endlesssea.app.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * §bibliothèque-locale — scanner de VIDÉOS dans des dossiers SAF choisis par
 * l'utilisateur (multi-répertoires, exigence utilisateur : « pas un seul, beaucoup »).
 *
 * - Sécurité : uniquement l'arborescence autorisée via OpenDocumentTree (persistée).
 * - Formats reconnus : mp4, mkv, ts, avi, webm, mov, m4v, mpg, mpeg, 3gp.
 * - Profondeur de récursion plafonnée (6 niveaux) + 2 000 fichiers max par dossier,
 * pour éviter les scans runaway sur les grosses cartes SD.
 */
data class LocalVideoFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val parentUri: String,
    /** Durée récupérée à la demande (coûteuse) — null tant qu'inconnue. */
    val durationMs: Long? = null,
)

object LocalVideos {

    private val VIDEO_EXT = setOf(
        "mp4", "mkv", "ts", "avi", "webm", "mov", "m4v", "mpg", "mpeg", "3gp",
    )
    private const val MAX_DEPTH = 6
    private const val MAX_FILES_PER_ROOT = 2000

    /** Liste toutes les vidéos d'un arbre SAF (récursion bornée). Jamais de throw. */
    fun scan(context: Context, treeUriString: String): List<LocalVideoFile> = runCatching {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return emptyList()
        val out = mutableListOf<LocalVideoFile>()
        fun walk(dir: DocumentFile, depth: Int) {
            if (depth > MAX_DEPTH || out.size >= MAX_FILES_PER_ROOT) return
            val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
            for (f in children) {
                if (out.size >= MAX_FILES_PER_ROOT) return
                if (f.isDirectory) {
                    walk(f, depth + 1)
                } else {
                    val name = f.name ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in VIDEO_EXT) {
                        out += LocalVideoFile(
                            uri = f.uri.toString(),
                            displayName = name,
                            sizeBytes = f.length(),
                            parentUri = dir.uri.toString(),
                        )
                    }
                }
            }
        }
        walk(root, 0)
        out
    }.getOrDefault(emptyList())

    /** Durée d'une vidéo via MediaMetadataRetriever (appel à la demande, hors thread UI). */
    fun durationMs(context: Context, uriString: String): Long? = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, Uri.parse(uriString))
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            runCatching { mmr.release() }
        }
    }.getOrNull()

    /** Affichage humain de la taille (« 1.2 Go »). */
    fun humanSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.1f Go".format(mb / 1024.0) else "%.0f Mo".format(mb)
    }

    fun humanDuration(ms: Long?): String {
        ms ?: return "" ; val totalMin = ms / 60_000
        val h = totalMin / 60; val m = totalMin % 60
        return if (h > 0) "${h}h${"%02d".format(m)}" else "${m} min"
    }
}
