package dev.endlesssea.app.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException

/**
 * §bibliothèque-locale — scanner de VIDÉOS dans des dossiers SAF choisis par
 * l'utilisateur (multi-répertoires, exigence utilisateur : « pas un seul, beaucoup »).
 *
 * - Sécurité : uniquement l'arborescence autorisée via OpenDocumentTree (persistée).
 * - Formats reconnus : mp4, mkv, ts, avi, webm, mov, m4v, mpg, mpeg, 3gp.
 * - Profondeur de récursion plafonnée (3 niveaux sous la racine) + 2 000 fichiers max par dossier,
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

/** Marqueurs de lecture : une valeur nulle signifie « non définie ». */
data class SeriesSkipMarkers(
    val introStartSec: Int? = null,
    val introEndSec: Int? = null,
    val outroStartSec: Int? = null,
)

/** Episode values win independently; each missing value inherits the series default. */
fun resolveEpisodeSkipMarkers(episode: SeriesSkipMarkers, series: SeriesSkipMarkers) = SeriesSkipMarkers(
    introStartSec = episode.introStartSec ?: series.introStartSec,
    introEndSec = episode.introEndSec ?: series.introEndSec,
    outroStartSec = episode.outroStartSec ?: series.outroStartSec,
)

/** Null in preferences inherits the file value; -1 is a persisted explicit clear. */
fun seriesMarkerValue(saved: Int?, fromDetailsFile: Int?): Int? = when {
    saved == null -> fromDetailsFile
    saved < 0 -> null
    else -> saved
}

/** §structure-aniyomi : métadonnées lues dans un dossier de série. */
data class SeriesMeta(
    val title: String? = null,
    val description: String? = null,
    val author: String? = null,
    val genres: List<String> = emptyList(),
    val coverUri: String? = null,
    val introStartSec: Int? = null,
    val introEndSec: Int? = null,
    val outroStartSec: Int? = null,
    /**
     * §episodes-json : titres d'épisodes publiés dans `episodes.json`
     * (format Aniyomi : `{"episode_number": 1, "name": "…"}`) —
     * clé = numéro d'épisode, valeur = titre lisible.
     */
    val episodeTitles: Map<Int, String> = emptyMap(),
)

object LocalVideos {

    /** Métadonnées par dossier (clé = URI du dossier), remplies pendant le scan. */
    val seriesMeta = java.util.concurrent.ConcurrentHashMap<String, SeriesMeta>()

    private val VIDEO_EXT = setOf(
        "mp4", "mkv", "ts", "avi", "webm", "mov", "m4v", "mpg", "mpeg", "3gp",
    )
    /**
     * §scan-aniyomi : Aniyomi ne parcourt que `racine/<Série>/<fichiers>` —
     * deux niveaux. Un balayage récursif profond sur une carte SD prend des
     * minutes pour rien. On garde une marge (3) pour `downloads/<Source>/<Série>/`.
     */
    private const val MAX_DEPTH = 3
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
        includeHidden: Boolean = false,
        /** Resume scans only the known series folder, never the entire SD card. */
        folderOnly: Boolean = false,
        /** Retour visuel : (dossiers explorés, vidéos trouvées, dossier courant). */
        onProgress: ((Int, Int, String) -> Unit)? = null,
    ): List<LocalVideoFile> = withContext(Dispatchers.IO) {
        val tree = Uri.parse(treeUriString)
        val rootId = runCatching {
            if (folderOnly) android.provider.DocumentsContract.getDocumentId(tree)
            else android.provider.DocumentsContract.getTreeDocumentId(tree)
        }.getOrNull() ?: return@withContext emptyList()
        val out = java.util.Collections.synchronizedList(mutableListOf<LocalVideoFile>())
        val scanned = java.util.concurrent.atomic.AtomicInteger(0)

        suspend fun readDirectory(docId: String): List<String> {
            val scanContext = currentCoroutineContext()
            fun metadataText(uri: String): String? = try {
                context.contentResolver.openInputStream(Uri.parse(uri))?.let { stream ->
                    readLocalMetadataText(stream, checkActive = { scanContext.ensureActive() })
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null } // Optional metadata must not hide playable videos.
            val children = android.provider.DocumentsContract
                .buildChildDocumentsUriUsingTree(tree, docId)
            val dirUri = android.provider.DocumentsContract
                .buildDocumentUriUsingTree(tree, docId).toString()
            val subDirs = mutableListOf<String>()
            try {
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
                        scanContext.ensureActive()
                        if (out.size >= MAX_FILES_PER_ROOT) break
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        // §fichiers-caches : ignorés sauf si l'utilisateur les demande
                        if (!includeHidden && name.startsWith(".")) continue
                        val mime = c.getString(2) ?: ""
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                            subDirs += id
                        } else if (name.equals("cover.jpg", true) || name.equals("cover.png", true)) {
                            // §structure-aniyomi : affiche de la série
                            seriesMeta.compute(dirUri) { _, old ->
                                (old ?: SeriesMeta()).copy(
                                    coverUri = android.provider.DocumentsContract
                                        .buildDocumentUriUsingTree(tree, id).toString(),
                                )
                            }
                        } else if (name.equals("details.json", true)) {
                            // §structure-aniyomi : métadonnées de la série
                            val docUri = android.provider.DocumentsContract
                                .buildDocumentUriUsingTree(tree, id).toString()
                            val text = metadataText(docUri)
                            if (text != null) {
                                val o = runCatching { org.json.JSONObject(text) }.getOrNull()
                                if (o != null) {
                                    seriesMeta.compute(dirUri) { _, old ->
                                        (old ?: SeriesMeta()).copy(
                                            title = o.optString("title").ifBlank { null },
                                            description = o.optString("description").ifBlank { null },
                                            author = o.optString("author").ifBlank { null },
                                            genres = (0 until (o.optJSONArray("genre")?.length() ?: 0))
                                                .mapNotNull { k -> o.optJSONArray("genre")?.optString(k) },
                                            introStartSec = o.optInt("introStartSec", -1).takeIf { o.has("introStartSec") && it >= 0 },
                                            introEndSec = o.optInt("introEndSec", -1).takeIf { o.has("introEndSec") && it >= 0 },
                                            outroStartSec = o.optInt("outroStartSec", -1).takeIf { o.has("outroStartSec") && it >= 0 },
                                        )
                                    }
                                }
                            }
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            // §episodes-json : titres d'épisodes du dossier (Aniyomi)
                            if (name.equals("episodes.json", true)) {
                                val docUri = android.provider.DocumentsContract
                                    .buildDocumentUriUsingTree(tree, id).toString()
                                val text = metadataText(docUri)
                                val titles = parseEpisodeTitles(text)
                                if (titles.isNotEmpty()) {
                                    seriesMeta.compute(dirUri) { _, old ->
                                        (old ?: SeriesMeta()).copy(episodeTitles = titles)
                                    }
                                }
                            } else if (ext in VIDEO_EXT || mime.startsWith("video/")) {
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
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* An inaccessible directory does not abort other roots. */ }
            scanContext.ensureActive()
            onProgress?.invoke(
                scanned.incrementAndGet(), out.size,
                Uri.decode(dirUri.substringAfterLast('/')).substringAfterLast('/'),
            )
            return subDirs
        }

        scanTreeBounded(rootId, if (folderOnly) 0 else MAX_DEPTH,
            shouldStop = { out.size >= MAX_FILES_PER_ROOT },
            readDirectory = ::readDirectory)
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

    // ------------------------------------------------ §episodes-json (Aniyomi)

    /**
     * §episodes-json : lit `episodes.json` d'un dossier de série.
     * Accepte les deux écritures répandues :
     *   `[{"episode_number":1,"name":"Le début"}, …]` et `[{"number":1,"title":"…"}]`.
     * Toute erreur renvoie une table vide : un JSON abîmé ne casse jamais le scan.
     */
    fun parseEpisodeTitles(text: String?): Map<Int, String> {
        val raw = text?.trim().orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val number = o.optInt("episode_number", o.optInt("number", 0))
                val name = o.optString("name").ifBlank { o.optString("title") }
                if (number > 0 && name.isNotBlank()) number to name else null
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    /**
     * §episodes-json : numéro d'épisode déduit d'un nom de fichier —
     * `S01E03`, `E03`, `Ep. 03`, `- 03`, `03 - titre`.
     */
    fun episodeNumber(fileName: String): Int? {
        val base = fileName.substringBeforeLast('.')
        if (Regex("\\d{1,5}").matches(base.trim())) return base.trim().toInt()
        if (Regex("(?i)(?:e(?:p(?:isode)?)?[ ._-]?|^|[ _-])\\d+[.,]\\d+(?![A-Za-z0-9])").containsMatchIn(base)) return null
        Regex("(?i)s(\\d{1,2})[ ._-]*e(\\d{1,5})").find(base)?.let { return it.groupValues[2].toInt() }
        Regex("(?i)\\bep?(?:isode)?[ ._-]?(\\d{1,5})\\b").find(base)?.let { return it.groupValues[1].toInt() }
        Regex("(?i)^(\\d{1,5})[ ._-]").find(base)?.let { return it.groupValues[1].toInt() }
        Regex("(?i)[ ._-](\\d{1,5})\\s*$").find(base)?.let { return it.groupValues[1].toInt() }
        return null
    }

    /** Saison conservée pour éviter de synchroniser S02E03 sur une entrée de saison 1. */
    fun episodeSeason(fileName: String): Int? =
        Regex("(?i)s(\\d{1,2})[ ._-]*e\\d{1,5}").find(fileName)?.groupValues?.get(1)?.toIntOrNull()

    /** Titre lisible d'un fichier une fois le numéro retiré (« Le début »). */
    fun episodeTitleFromFileName(fileName: String): String = fileName.substringBeforeLast('.')
        .replace(Regex("(?i)s\\d{1,2}[ ._-]*e\\d{1,3}"), " ")
        .replace(Regex("(?i)\\bep?(?:isode)?[ ._-]?\\d{1,3}\\b"), " ")
        .replace(Regex("^\\d{1,3}[ ._-]+"), "")
        .replace(Regex("[_.]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * §metadonnees-editees : écrit `details.json` dans le dossier de la série
     * (même contrat que ce que le scanner relit au démarrage). Renvoie false si
     * le fournisseur SAF refuse l'écriture — l'interface le dit alors à
     * l'utilisateur au lieu d'un échec silencieux.
     */
    fun writeSeriesMeta(
        context: Context,
        folderUriString: String,
        title: String?,
        description: String? = null,
        author: String? = null,
        genres: List<String> = emptyList(),
        introStartSec: Int? = null,
        introEndSec: Int? = null,
        outroStartSec: Int? = null,
    ): Boolean = runCatching {
        val folder = Uri.parse(folderUriString)
        val json = org.json.JSONObject().apply {
            title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            description?.takeIf { it.isNotBlank() }?.let { put("description", it) }
            author?.takeIf { it.isNotBlank() }?.let { put("author", it) }
            if (genres.isNotEmpty()) put("genre", org.json.JSONArray(genres))
            introStartSec?.coerceAtLeast(0)?.let { put("introStartSec", it) }
            introEndSec?.coerceAtLeast(0)?.let { put("introEndSec", it) }
            outroStartSec?.coerceAtLeast(0)?.let { put("outroStartSec", it) }
        }.toString(2)

        val docId = android.provider.DocumentsContract.getDocumentId(folder)
        val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(folder, docId)
        var existing: Uri? = null
        context.contentResolver.query(
            children,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1).equals("details.json", ignoreCase = true)) {
                    existing = android.provider.DocumentsContract
                        .buildDocumentUriUsingTree(folder, cursor.getString(0))
                    break
                }
            }
        }
        val target = existing ?: android.provider.DocumentsContract.createDocument(
            context.contentResolver, folder, "application/json", "details.json",
        ) ?: return@runCatching false
        val ok = context.contentResolver.openOutputStream(target, "wt")?.use { out ->
            out.write(json.toByteArray()); out.flush(); true
        } ?: false
        ok
    }.getOrDefault(false)

}
