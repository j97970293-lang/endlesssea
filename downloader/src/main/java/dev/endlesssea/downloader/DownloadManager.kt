package dev.endlesssea.downloader

import dev.endlesssea.core.model.DownloadProgress
import dev.endlesssea.core.model.DownloadStatus
import dev.endlesssea.data.db.DownloadSegmentEntity
import dev.endlesssea.data.db.DownloadTaskEntity
import dev.endlesssea.data.db.DownloadsDao
import dev.endlesssea.downloader.segment.SegmentEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Queue + lifecycle over [SegmentEngine] (docs/en/06 §3, §7).
 *
 * - DB is the source of truth; in-memory jobs mirror active tasks only.
 * - Boot recovery: tasks left PROBING/DOWNLOADING return to QUEUED and resume from the
 *   segment table + real on-disk bytes (honest resume).
 * - Swap target: implement [DownloadEngine] to replace this engine (docs/en/02 §7).
 */
interface DownloadEngine {
    suspend fun enqueue(task: DownloadTaskEntity): String
    suspend fun pause(taskId: String)
    suspend fun resume(taskId: String)
    suspend fun cancel(taskId: String, deleteFiles: Boolean = true)
    suspend fun recoverQueue()

    /** Réorganise la file : [up]=true fait remonter la tâche (prioritaire). */
    suspend fun reorder(taskId: String, up: Boolean)

    val progress: Flow<DownloadProgress>
    val notifications: Flow<DownloadNotice>
}

data class DownloadNotice(val taskId: String, val title: String, val message: String, val ok: Boolean)

class DownloadManager(
    private val dao: DownloadsDao,
    private val http: OkHttpClient,
    private val tempDirProvider: () -> File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxParallel: Int = 2,
    private val partsPerTask: Int = 4,
) : DownloadEngine {

    private val engine = SegmentEngine(http)
    private val hlsEngine = dev.endlesssea.downloader.hls.HlsEngine(http)
    private val jobs = mutableMapOf<String, Job>()
    private val semaphore = Semaphore(maxParallel)
    private val progressFlow = MutableStateFlow(DownloadProgress("", DownloadStatus.QUEUED, 0, 0, 0, 0))
    private val notices = Channel<DownloadNotice>(Channel.BUFFERED)
    override val progress = progressFlow
    override val notifications: Flow<DownloadNotice> = notices.receiveAsFlow()

    // ------------------------------------------------------------ commands

    override suspend fun enqueue(task: DownloadTaskEntity): String {
        val id = task.id.ifBlank { UUID.randomUUID().toString() }
        dao.upsert(task.copy(id = id, status = DownloadStatus.QUEUED.name))
        kick(id)
        return id
    }

    override suspend fun pause(taskId: String) {
        jobs.remove(taskId)?.cancel()                  // co-op cancellation = byte-exact stop
        dao.updateStatus(taskId, DownloadStatus.PAUSED.name)
    }

    override suspend fun resume(taskId: String) {
        dao.updateStatus(taskId, DownloadStatus.QUEUED.name)
        kick(taskId)
    }

    override suspend fun cancel(taskId: String, deleteFiles: Boolean) {
        jobs.remove(taskId)?.cancel()
        if (deleteFiles) partFile(dao.byId(taskId)?.fileName ?: return).delete()
        dao.delete(taskId)
    }

    /** Called on app boot: restore interrupted tasks (doc 06 §3). */
    override suspend fun recoverQueue() {
        dao.schedulable().forEach { dao.updateStatus(it.id, DownloadStatus.QUEUED.name) }
        dao.schedulable().forEach { kick(it.id) }
    }

    override suspend fun reorder(taskId: String, up: Boolean) {
        val queue = dao.schedulable()
        if (queue.isEmpty()) return
        if (up) {
            dao.setPriority(taskId, (queue.maxOfOrNull { it.priority } ?: 0) + 1)
        } else {
            dao.setPriority(taskId, (queue.minOfOrNull { it.priority } ?: 0) - 1)
        }
    }

    // ------------------------------------------------------------ scheduling

    private fun kick(taskId: String) {
        if (jobs.containsKey(taskId)) return
        jobs[taskId] = scope.launch {
            semaphore.withPermit {
                runTask(taskId)
            }
        }
    }

    private suspend fun runTask(taskId: String) {
        val task = dao.byId(taskId) ?: return
        // Aiguillage par type de source (un m3u8/dash mal renseigné est détecté aussi)
        val cleanUrl = task.url.substringBefore('?').lowercase()
        val isHls = task.streamType == "HLS" || cleanUrl.endsWith(".m3u8")
        val isDash = task.streamType == "DASH" || cleanUrl.endsWith(".mpd")
        if (isDash) {
            dao.updateStatus(
                taskId, DownloadStatus.FAILED.name,
                "Ce lien est un flux DASH : lecture en ligne uniquement pour l'instant.",
            )
            notices.trySend(DownloadNotice(taskId, task.fileName, "Flux DASH — non téléchargeable", ok = false))
            return
        }
        if (isHls) {
            runHlsTask(taskId, task)
            return
        }
        try {
            dao.updateStatus(taskId, DownloadStatus.PROBING.name)
            val probe = engine.probe(task.url, task.headersMap())
            val expected = if (probe.contentLength > 0) probe.contentLength else task.totalBytes
            dao.upsert(task.copy(totalBytes = expected, etag = probe.etag))

            val parts = tempDirProvider().resolve(".tmp").apply { mkdirs() }
            val part = File(parts, "${task.fileName}.part")

            val table = dao.segments(taskId).ifEmpty {
                engine.plan(expected, probe.acceptRanges, partsPerTask).map {
                    DownloadSegmentEntity(taskId, it.idx, it.start, it.end)
                }.also { dao.upsertSegments(it) }
            }
            // Plan vide = le serveur n'a pas donné de taille exploitable (le plus souvent
            // un lien « lecture seule » — flux, page de lecteur, URL éphémère). On le dit
            // clairement au lieu de planter plus bas sur une liste vide (« List is empty. »).
            if (table.isEmpty()) {
                throw SegmentEngine.SourceError(
                    "Téléchargement impossible : la source ne fournit pas un fichier direct " +
                        "(lecture en ligne uniquement, ou lien expiré).",
                )
            }
            var speedWindowBytes = 0L
            var speedWindowStart = System.currentTimeMillis()

            dao.updateStatus(taskId, DownloadStatus.DOWNLOADING.name)
            engine.download(
                url = probe.url.takeIf { it.isNotBlank() } ?: task.url,
                headers = task.headersMap(),
                segments = table.map { SegmentEngine.Segment(it.idx, it.startByte, it.endByte, it.downloadedBytes) },
                target = part,
            ) { seg ->
                scope.launch(Dispatchers.IO) { dao.checkpoint(taskId, seg.idx, seg.downloaded, seg.done) }
                speedWindowBytes += CHECKPOINT_EST
                val now = System.currentTimeMillis()
                val elapsed = now - speedWindowStart
                if (elapsed > 0) {
                    val speed = speedWindowBytes * 1000 / elapsed
                    val remaining = (expected - table.sumOf { it.downloadedBytes }).coerceAtLeast(0)
                    progressFlow.value = DownloadProgress(
                        taskId, DownloadStatus.DOWNLOADING, expected,
                        expected - remaining, speed,
                        etaSeconds = if (speed > 0) remaining / speed else -1,
                    )
                }
            }

            dao.updateStatus(taskId, DownloadStatus.VERIFYING.name)
            val finalName = task.fileName.removeSuffix(".part")
            val finalFile = part.parentFile?.resolve(finalName) ?: File(finalName)
            part.renameTo(finalFile)
            // Garde « 36 Ko » : une vidéo de quelques Ko au final = page d'erreur déguisée.
            val isVideoExt = finalName.substringAfterLast('.', "").lowercase() in
                setOf("mp4", "mkv", "ts", "avi", "webm", "mov", "m4v")
            if (isVideoExt && finalFile.length() in 1 until 256 * 1024) {
                val ko = finalFile.length() / 1024
                finalFile.delete()
                dev.endlesssea.core.diag.EsLog.e(
                    "Download", "36KB guard",
                    "Fichier suspect refusé: $finalName ($ko Ko)",
                )
                dao.updateStatus(
                    taskId, DownloadStatus.FAILED.name,
                    "Téléchargement invalide : $ko Ko seulement — le serveur a probablement " +
                        "renvoyé une page d'erreur au lieu de la vidéo. Réessaie plus tard " +
                        "ou choisis un autre serveur/qualité.",
                )
                notices.trySend(DownloadNotice(taskId, finalName, "Fichier suspect refusé", ok = false))
                return
            }
            dao.updateStatus(taskId, DownloadStatus.COMPLETED.name)
            notices.trySend(DownloadNotice(taskId, finalName, "Téléchargement terminé", ok = true))
        } catch (e: kotlinx.coroutines.CancellationException) {
            dao.updateStatus(taskId, DownloadStatus.PAUSED.name)
            throw e
        } catch (e: SegmentEngine.IntegrityError) {
            dao.updateStatus(taskId, DownloadStatus.FAILED.name, "integrity:${e.message}")
            notices.trySend(DownloadNotice(taskId, task.fileName, "Fichier corrompu — vérification échouée", ok = false))
        } catch (e: Exception) {
            dev.endlesssea.core.diag.EsLog.e("Download", "runTask", "Échec de ${task.fileName}", e.message ?: e.javaClass.simpleName)
            dao.updateStatus(taskId, DownloadStatus.FAILED.name, e.message)
            notices.trySend(DownloadNotice(taskId, task.fileName, "Téléchargement interrompu : ${e.message}", ok = false))
        } finally {
            jobs.remove(taskId)
        }
    }

    /** Chemin flux HLS : segments récupérés un par un avec reprise par index (diag « 36 Ko »). */
    private suspend fun runHlsTask(taskId: String, task: DownloadTaskEntity) {
        try {
            dao.updateStatus(taskId, DownloadStatus.PROBING.name)
            val plan = hlsEngine.resolve(task.url, task.headersMap())
            val partsDir = tempDirProvider().resolve(".tmp").apply { mkdirs() }
            val part = File(partsDir, "${task.fileName}.part")
            val table = dao.segments(taskId).ifEmpty {
                plan.segments.map { DownloadSegmentEntity(taskId, it.idx, 0, 0) }
                    .also { dao.upsertSegments(it) }
            }
            val doneIdx = table.filter { it.done }.map { it.idx }.toSet()
            val fromIdx = plan.segments.firstOrNull { it.idx !in doneIdx }?.idx ?: plan.segments.size
            dao.updateStatus(taskId, DownloadStatus.DOWNLOADING.name)
            var doneCount = doneIdx.size
            val totalCount = plan.segments.size
            progressFlow.value = DownloadProgress(
                taskId, DownloadStatus.DOWNLOADING, totalCount.toLong(), doneCount.toLong(), 0, -1,
            )
            val bytes = hlsEngine.download(plan, task.headersMap(), part, fromIdx) { idx ->
                dao.checkpoint(taskId, idx, 0, true)
                doneCount++
                progressFlow.value = DownloadProgress(
                    taskId, DownloadStatus.DOWNLOADING,
                    totalCount.toLong(), doneCount.toLong(), 0, etaSeconds = -1,
                )
            }
            dao.updateStatus(taskId, DownloadStatus.VERIFYING.name)
            val finalName = task.fileName.removeSuffix(".part")
            val finalFile = part.parentFile?.resolve(finalName) ?: File(finalName)
            part.renameTo(finalFile)
            dao.upsert(task.copy(totalBytes = bytes, updatedAt = System.currentTimeMillis()))
            dao.updateStatus(taskId, DownloadStatus.COMPLETED.name)
            notices.trySend(
                DownloadNotice(taskId, finalName, "Téléchargement terminé ($totalCount segments)", ok = true),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            dao.updateStatus(taskId, DownloadStatus.PAUSED.name)
            throw e
        } catch (e: Exception) {
            dao.updateStatus(taskId, DownloadStatus.FAILED.name, e.message)
            notices.trySend(
                DownloadNotice(taskId, task.fileName, "Téléchargement interrompu : ${e.message}", ok = false),
            )
        } finally {
            jobs.remove(taskId)
        }
    }

    // ------------------------------------------------------------ helpers

    private fun partFile(fileName: String): File =
        tempDirProvider().resolve(".tmp/${fileName.removeSuffix(".part")}.part")

    private fun DownloadTaskEntity.headersMap(): Map<String, String> =
        headersJson.trim().removeSurrounding("{", "}").split(",")
            .mapNotNull { kv -> kv.split(":", limit = 2).takeIf { it.size == 2 } }
            .associate { it[0].trim().removeSurrounding("\"") to it[1].trim().removeSurrounding("\"") }

    companion object {
        private const val CHECKPOINT_EST = 256L * 1024       // speed-window granularity
        fun sha256(file: File): String =
            file.inputStream().use { input ->
                val md = MessageDigest.getInstance("SHA-256")
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    md.update(buf, 0, n)
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
    }
}
