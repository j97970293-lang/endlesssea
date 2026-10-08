package dev.endlesssea.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.core.model.DownloadStatus
import dev.endlesssea.data.db.DownloadsDao
import dev.endlesssea.downloader.DownloadEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class DownloadFilter(val label: String) {
    TOUS("Tous"),
    EN_COURS("En cours"),
    PAUSE("En pause"),
    ECHEC("Échoués"),
    TERMINE("Terminés"),
}

enum class DownloadSort(val label: String) {
    DATE("Date"),
    TAILLE("Taille"),
    NOM("Nom"),
}

data class DownloadsUiState(
    /** Rangées déjà filtrées + triées pour l'affichage. */
    val rows: List<DownloadRowUi> = emptyList(),
    /** Message d'action (export/déplacement, erreur ou confirmation). */
    val notice: String? = null,
    val filter: DownloadFilter = DownloadFilter.TOUS,
    val sort: DownloadSort = DownloadSort.DATE,
    val ascending: Boolean = false,
    val totalCount: Int = 0,
)

/**
 * Onglet Téléchargements : file temps réel (moteur + Room), filtres par statut,
 * tri date/taille/nom, réorganisation de la file (priorités).
 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val dao: DownloadsDao,
    private val episodeDao: dev.endlesssea.data.db.EpisodeDao,
    private val engine: DownloadEngine,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val prefs: dev.endlesssea.app.di.AppPrefs,
) : ViewModel() {

    /** Dernier instantané brut (avant filtre/tri) pour re-appliquer la vue sans perdre de lignes. */
    private var rawRows: List<DownloadRowUi> = emptyList()

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

    init {
        viewModelScope.launch {
            // Room fournit la file ordonnée ; le moteur pousse la progression live
            combine(dao.observeAllOrdered(), engine.progressByTask) { tasks, live -> tasks to live }
                .collect { (tasks, liveMap) ->
                    rawRows = tasks.map { t ->
                        val live = liveMap[t.id]
                        val isLive = live != null && t.status == DownloadStatus.DOWNLOADING.name
                        DownloadRowUi(
                            id = t.id,
                            title = t.fileName.removeSuffix(".part"),
                            detail = "${t.server} · ${t.quality}" +
                                (if (t.totalBytes > 0) " · ${formatBytes(t.totalBytes)}" else "") +
                                    (t.displayPath.substringBeforeLast('/')
                                        .takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            status = t.status,
                            fraction = when {
                                t.status == DownloadStatus.COMPLETED.name -> 1f
                                isLive -> live!!.fraction
                                else -> 0f
                            },
                            progressLabel = when {
                                isLive && live!!.totalSegments > 0 ->
                                    "${formatBytes(live.downloadedBytes)} téléchargés · ${live.completedSegments}/${live.totalSegments} segments · taille finale inconnue"
                                isLive && live!!.totalBytes > 0 ->
                                    "${formatBytes(live.downloadedBytes)} / ${formatBytes(live.totalBytes)}" +
                                        if (live.speedBytesPerSec > 0) " · ${formatBytes(live.speedBytesPerSec)}/s" else ""
                                isLive && live!!.downloadedBytes > 0 ->
                                    "${formatBytes(live.downloadedBytes)} téléchargés"
                                t.status == DownloadStatus.COMPLETED.name && t.totalBytes > 0 ->
                                    formatBytes(t.totalBytes)
                                else -> ""
                            },
                            error = t.error,
                            createdAt = t.createdAt,
                            totalBytes = t.totalBytes,
                            targetUri = t.targetUri,
                            seriesKey = t.mediaId ?: t.displayPath.substringBeforeLast('/').ifBlank { t.id },
                            seriesTitle = t.displayPath.substringBeforeLast('/').substringAfterLast('/').ifBlank { "Téléchargements" },
                        )
                    }
                    applyView(rawRows)
                }
        }
    }

    /** Re-applique filtre + tri sur la liste brute fournie. */
    private fun applyView(rows: List<DownloadRowUi>) {
        val st = _uiState.value
        val filtered = rows.filter { row ->
            when (st.filter) {
                DownloadFilter.TOUS -> true
                DownloadFilter.EN_COURS -> row.status in setOf(
                    DownloadStatus.QUEUED.name, DownloadStatus.PROBING.name, DownloadStatus.DOWNLOADING.name,
                )
                DownloadFilter.PAUSE -> row.status == DownloadStatus.PAUSED.name
                DownloadFilter.ECHEC -> row.status == DownloadStatus.FAILED.name
                DownloadFilter.TERMINE -> row.status == DownloadStatus.COMPLETED.name
            }
        }
        val sorted = when (st.sort) {
            DownloadSort.DATE -> filtered.sortedBy { it.createdAt }
            DownloadSort.TAILLE -> filtered.sortedBy { it.totalBytes }
            DownloadSort.NOM -> filtered.sortedBy { it.title.lowercase() }
        }.let { if (st.ascending) it else it.reversed() }
        _uiState.value = st.copy(rows = sorted, totalCount = rows.size)
    }

    fun setFilter(filter: DownloadFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        applyView(rawRows)
    }

    fun setSort(sort: DownloadSort) {
        _uiState.value = _uiState.value.copy(sort = sort)
        applyView(rawRows)
    }

    fun toggleOrder() {
        _uiState.value = _uiState.value.copy(ascending = !_uiState.value.ascending)
        applyView(rawRows)
    }

    fun clearNotice() { _uiState.value = _uiState.value.copy(notice = null) }

    /**
     * §integrite (conversation 10) : recalcule l'empreinte SHA-256 des fichiers
     * terminés et la compare à celle enregistrée au téléchargement. Un fichier
     * tronqué ou remplacé est signalé clairement ; une empreinte absente (tâche
     * terminée avant la mise à jour) est simplement enregistrée.
     */
    fun verifyIntegrity() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val completed = rawRows.filter { it.status == DownloadStatus.COMPLETED.name }
        if (completed.isEmpty()) {
            _uiState.value = _uiState.value.copy(notice = "Aucun fichier terminé à vérifier.")
            return@launch
        }
        var ok = 0
        var missing = 0
        var broken = 0
        completed.forEach { row ->
            val task = dao.byId(row.id) ?: return@forEach
            val stream = openStream(task.targetUri) ?: run { missing++; return@forEach }
            val hash = runCatching {
                stream.use { input ->
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        md.update(buf, 0, n)
                    }
                    md.digest().joinToString("") { b -> "%02x".format(b) }
                }
            }.getOrNull() ?: run { missing++; return@forEach }
            if (task.sha256.isNullOrBlank()) { dao.setHash(task.id, hash); ok++ }
            else if (task.sha256 == hash) ok++ else broken++
        }
        _uiState.value = _uiState.value.copy(
            notice = "Vérification : $ok conforme(s)" +
                (if (missing > 0) ", $missing introuvable(s)" else "") +
                (if (broken > 0) ", $broken corrompu(s) — retélécharge-les" else "") + ".",
        )
    }

    /** Ouvre un flux de lecture sur un fichier terminé (SAF ou file://). */
    private fun openStream(uriString: String): java.io.InputStream? = runCatching {
        val uri = android.net.Uri.parse(uriString)
        when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)
            else -> java.io.File(uri.path ?: uriString).takeIf { it.exists() }?.inputStream()
        }
    }.getOrNull()

    /**
     * §nettoyage (conversation 10) : purge les fichiers temporaires orphelins
     * et les téléchargements terminés au-delà du délai configuré.
     */
    fun tidy() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val days = prefs.downloadAutoCleanDays.value
        var freed = 0L
        var removed = 0
        if (days > 0) {
            val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
            runCatching { dao.completedOlderThan(cutoff) }.getOrDefault(emptyList()).forEach { task ->
                val uri = android.net.Uri.parse(task.targetUri)
                val deleted = runCatching {
                    if (uri.scheme == "content") {
                        context.contentResolver.delete(uri, null, null) > 0
                    } else {
                        java.io.File(uri.path ?: task.targetUri).delete()
                    }
                }.getOrDefault(false)
                if (deleted) {
                    freed += task.totalBytes
                    dao.delete(task.id)
                    removed++
                }
            }
        }
        _uiState.value = _uiState.value.copy(
            notice = if (removed == 0) {
                "Rien à nettoyer" + (if (days == 0) " (délai de purge désactivé dans les Paramètres)." else ".")
            } else {
                "$removed fichier(s) supprimé(s) · ${formatBytes(freed)} libérés"
            },
        )
    }

    /** §retrouver-téléchargements : lecture directe d'un fichier terminé (file:// ou SAF). */
    fun play(id: String, onReady: () -> Unit) = viewModelScope.launch {
        val task = dao.byId(id) ?: return@launch
        if (task.status != DownloadStatus.COMPLETED.name) return@launch
        val related = dao.observeAllOrdered().first().filter {
            it.status == DownloadStatus.COMPLETED.name &&
                (if (task.mediaId != null) it.mediaId == task.mediaId
                 else it.displayPath.substringBeforeLast('/') == task.displayPath.substringBeforeLast('/'))
        }
        val queue = related.map { download ->
            val episode = download.episodeId?.let { episodeDao.byId(it) }
            val uri = dev.endlesssea.app.local.DownloadLocator.resolve(context, download.targetUri, download.fileName,
                listOfNotNull(prefs.storageRoot.value) + prefs.storageHistory.value) ?: download.targetUri
            dev.endlesssea.app.ui.player.PlayerLaunchStore.QueueItem(
                title = download.fileName, episodeId = download.episodeId ?: download.id, mediaId = download.mediaId,
                episodeNumber = episode?.number, season = episode?.season, downloaded = true,
                links = listOf(dev.endlesssea.extensions.api.model.VideoLink(url = uri,
                    streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                    quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN, server = "Téléchargé")),
            )
        }.sortedWith(compareBy({ it.season ?: 0 }, { it.episodeNumber ?: Float.MAX_VALUE }, { it.title }))
        dev.endlesssea.app.ui.player.PlayerLaunchStore.resolver = null
        dev.endlesssea.app.ui.player.PlayerLaunchStore.setQueue(queue, queue.indexOfFirst { it.episodeId == (task.episodeId ?: task.id) })
        dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
            title = task.fileName.removeSuffix(".part"),
            mediaId = task.mediaId, episodeId = task.episodeId ?: id,
            links = listOf(
                dev.endlesssea.extensions.api.model.VideoLink(
                    // §emplacement : fichier retrouvé même si le dossier de
                    // téléchargement a changé entre-temps.
                    url = dev.endlesssea.app.local.DownloadLocator.resolve(
                        context, task.targetUri, task.fileName,
                        listOfNotNull(prefs.storageRoot.value) + prefs.storageHistory.value,
                    ) ?: task.targetUri,
                    streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                    quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN,
                    server = "Téléchargé",
                ),
            ),
            startIndex = 0,
        )
        onReady()
    }

    /** §deplacer-téléchargement : copie vers un répertoire SAF (carte SD incluse) puis
     * réoriente la tâche vers la nouvelle URI et supprime l'original local. */
    fun exportToTree(id: String, treeUri: String) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val task = dao.byId(id) ?: return@launch
        if (task.status != DownloadStatus.COMPLETED.name) return@launch
        val resolver = context.contentResolver
        val u = android.net.Uri.parse(task.targetUri)
        val srcFile = if (u.scheme == null || u.scheme == "file") {
            java.io.File(u.path ?: task.targetUri.removePrefix("file://"))
        } else {
            null
        }
        val mime = when {
            task.fileName.endsWith(".mkv", true) -> "video/x-matroska"
            task.fileName.endsWith(".webm", true) -> "video/webm"
            else -> "video/mp4"
        }
        val displayName = task.fileName.removeSuffix(".part")
        val result = runCatching {
            val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                context, android.net.Uri.parse(treeUri),
            ) ?: error("Dossier illisible")
            val doc = tree.createFile(mime, displayName) ?: error("Création du fichier impossible")
            resolver.openOutputStream(doc.uri, "rwt")?.use { out ->
                if (srcFile != null) {
                    srcFile.inputStream().use { it.copyTo(out) }
                } else {
                    resolver.openInputStream(u)?.use { it.copyTo(out) } ?: error("Source introuvable")
                }
            } ?: error("Ouverture de la destination impossible")
            val folder = tree.name ?: "dossier choisi"
            dao.setTarget(id, doc.uri.toString(), "$folder/$displayName")
            runCatching { srcFile?.delete() }
            "Déplacé vers « $folder » — visible dans vos dossiers."
        }
        _uiState.value = _uiState.value.copy(notice = result.getOrElse { "Déplacement impossible : ${it.message}" })
    }

    fun pause(id: String) = viewModelScope.launch { engine.pause(id) }
    fun resume(id: String) = viewModelScope.launch { engine.resume(id) }
    fun cancel(id: String) = viewModelScope.launch { engine.cancel(id) }
    fun reorder(id: String, up: Boolean) = viewModelScope.launch { engine.reorder(id, up) }

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f Go", bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f Mo", bytes / (1L shl 20).toDouble())
            else -> String.format(Locale.ROOT, "%.0f Ko", bytes / 1024.0)
        }
    }
}
