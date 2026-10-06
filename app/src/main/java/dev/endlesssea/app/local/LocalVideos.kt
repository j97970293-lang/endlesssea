package dev.endlesssea.app.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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

    /**
     * §scan-rapide : liste toutes les vidéos d'un arbre SAF.
     *
     * `DocumentFile.listFiles()` déclenche UNE requête par enfant (et une de plus
     * par appel à `name`/`length()`), ce qui rendait le scan interminable sur une
     * carte SD. Ici on interroge directement le ContentResolver : UN curseur par
     * dossier qui ramène id + nom + type + taille d'un coup, et les sous-dossiers
     * sont parcourus en parallèle (8 à la fois).
     */
    fun scan(context: Context, treeUriString: String): List<LocalVideoFile> = runCatching {
        kotlinx.coroutines.runBlocking { scanAsync(context, treeUriString) }
    }.getOrDefault(emptyList())

    suspend fun scanAsync(
        context: Context,
        treeUriString: String,
    ): List<LocalVideoFile> = coroutineScope {
        val tree = Uri.parse(treeUriString)
        val rootId = runCatching {
            android.provider.DocumentsContract.getTreeDocumentId(tree)
        }.getOrNull() ?: return@coroutineScope emptyList()
        val out = java.util.Collections.synchronizedList(mutableListOf<LocalVideoFile>())
        val gate = Semaphore(8)

        suspend fun walk(docId: String, depth: Int): Unit {
            if (depth > MAX_DEPTH || out.size >= MAX_FILES_PER_ROOT) return
            val children = android.provider.DocumentsContract
                .buildChildDocumentsUriUsingTree(tree, docId)
            val dirUri = android.provider.DocumentsContract
                .buildDocumentUriUsingTree(tree, docId).toString()
            val subDirs = mutableListOf<String>()
            runCatching {
                context.contentResolver.query(
                    children,
                    arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                        android.provider.DocumentsContract.Document.COLUMN_SIZE,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (out.size >= MAX_FILES_PER_ROOT) break
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2) ?: ""
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                            subDirs += id
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            if (ext in VIDEO_EXT || mime.startsWith("video/")) {
                                out += LocalVideoFile(
                                    uri = android.provider.DocumentsContract
                                        .buildDocumentUriUsingTree(tree, id).toString(),
                                    displayName = name,
                                    sizeBytes = size,
                                    parentUri = dirUri,
                                )
                            }
                        }
                    }
                }
            }
            // sous-dossiers en parallèle (8 curseurs max simultanés)
            coroutineScope {
                val jobs = subDirs.map { sub ->
                    async(Dispatchers.IO) { gate.withPermit { walk(sub, depth + 1) } }
                }
                jobs.forEach { it.await() }
            }
        }

        gate.withPermit { walk(rootId, 0) }
        out.toList()
    }

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
