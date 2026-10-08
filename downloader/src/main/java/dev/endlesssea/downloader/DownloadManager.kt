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

    /** §progression : progression de CHAQUE tâche active (clé = id de tâche). */
    val progressByTask: Flow<Map<String, DownloadProgress>>
    val notifications: Flow<DownloadNotice>

    /**
     * §service-telechargement (conversation 10) : nombre de tâches prises en
     * charge par le moteur (0 = file au repos). Le service au premier plan s'en
     * sert pour s'arrêter de lui-même au lieu de laisser une notification.
     */
    val runningCount: Flow<Int>
}

data class DownloadNotice(val taskId: String, val title: String, val message: String, val ok: Boolean)

class DownloadManager(
    private val dao: DownloadsDao,
    private val http: OkHttpClient,
    private val tempDirProvider: () -> File,
    /**
     * §stockage-public : déplace le fichier terminé vers son emplacement
     * définitif (dossier choisi par l'utilisateur / stockage public) et renvoie
     * l'URI finale. Null = on laisse le fichier là où il est.
     */
    private val publisher: ((DownloadTaskEntity, File) -> String?)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val maxParallel: Int = 2,
    private val partsPerTask: Int = 4,
    /** §debit (conversation 10) : plafond de bande passante en octets/s (0 = illimité). */
    private val throttleBytesPerSec: () -> Long = { 0L },
    /** §espace-disque : octets libres sur le volume de travail (contrôle avant départ). */
    private val freeSpaceProvider: (() -> Long)? = null,
) : DownloadEngine {

    private val engine = SegmentEngine(http)
    private val hlsEngine = dev.endlesssea.downloader.hls.HlsEngine(http)
    private val jobs = mutableMapOf<String, Job>()
    private val semaphore = Semaphore(maxParallel)
    private val _runningCount = MutableStateFlow(0)
    override val runningCount: Flow<Int> = _runningCount
    private val progressFlow = MutableStateFlow(DownloadProgress("", DownloadStatus.QUEUED, 0, 0, 0, 0))
    private val notices = Channel<DownloadNotice>(Channel.BUFFERED)
    override val progress = progressFlow
    private val progressMap = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    override val progressByTask = progressMap

    /** Publie une progression : flux global + table par tâche (plusieurs téléchargements). */
    private fun publishProgress(p: DownloadProgress) {
        progressFlow.value = p
        progressMap.value = progressMap.value + (p.taskId to p)
    }
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
        // §nettoyage (conversation 10) : les .part sans tâche associée (annulation
        // interrompue, changement de nom…) restaient à vie dans .tmp.
        runCatching { purgeOrphanParts() }
    }

    /** Supprime les fichiers temporaires qu'aucune tâche n'utilise plus. */
    suspend fun purgeOrphanParts(deleteFiles: Boolean = true): Int =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val tmp = tempDirProvider().resolve(".tmp")
            val files = tmp.listFiles()?.filter { it.isFile && it.name.endsWith(".part") } ?: return@withContext 0
            // On ne garde que les .part réellement utiles : tâches en file ou en pause.
            val known = runCatching { dao.activeFileNames() }.getOrDefault(emptyList())
                .map { "${it.removeSuffix(".part")}.part" }.toSet()
            var removed = 0
            files.forEach { file ->
                if (file.name !in known) {
                    if (deleteFiles && file.delete()) removed++
                }
            }
            removed
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
            try {
                semaphore.withPermit { runTask(taskId) }
            } finally {
                // §service : la tâche quitte la file — le service au premier plan
                // s'arrêtera une fois la dernière terminée.
                jobs.remove(taskId)
                _runningCount.value = jobs.size
            }
        }
        _runningCount.value = jobs.size
    }

    private suspend fun runTask(taskId: String) {
        val task = dao.byId(taskId) ?: return
        // Aiguillage par type de source (un m3u8/dash mal renseigné est détecté aussi)
        val kind = dev.endlesssea.core.download.downloadSourceKind(task.streamType, task.url)
        val isHls = kind == dev.endlesssea.core.download.DownloadSourceKind.HLS
        val isDash = kind == dev.endlesssea.core.download.DownloadSourceKind.DASH
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

            // §espace-disque (conversation 10) : un téléchargement qui ne tient pas
            // échoue AVANT de saturer la mémoire de stockage (et proprement).
            val free = runCatching { freeSpaceProvider?.invoke() ?: -1L }.getOrDefault(-1L)
            if (expected > 0 && free > 0 && expected + DISK_MARGIN_BYTES > free) {
                val manque = (expected + DISK_MARGIN_BYTES - free) / (1024 * 1024)
                throw SegmentEngine.SourceError(
                    "Espace insuffisant : il manque environ $manque Mo sur l'appareil " +
                        "(fichier de ${expected / (1024 * 1024)} Mo).",
                )
            }

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
                throttleBytesPerSec = runCatching { throttleBytesPerSec() }.getOrDefault(0L),
            ) { seg ->
                scope.launch(Dispatchers.IO) { dao.checkpoint(taskId, seg.idx, seg.downloaded, seg.done) }
                speedWindowBytes += CHECKPOINT_EST
                val now = System.currentTimeMillis()
                val elapsed = now - speedWindowStart
                if (elapsed > 0) {
                    val speed = speedWindowBytes * 1000 / elapsed
                    val remaining = (expected - table.sumOf { it.downloadedBytes }).coerceAtLeast(0)
                    publishProgress(
                        DownloadProgress(
                            taskId, DownloadStatus.DOWNLOADING, expected,
                            expected - remaining, speed,
                            etaSeconds = if (speed > 0) remaining / speed else -1,
                        ),
                    )
                }
            }

            dao.updateStatus(taskId, DownloadStatus.VERIFYING.name)
            val finalName = task.fileName.removeSuffix(".part")
            val finalFile = part.parentFile?.resolve(finalName) ?: File(finalName)
            part.renameTo(finalFile)
            // Garde « 36 Ko » (spec §6, renforcée) : taille ET contenu. Une page
            // d'erreur déguisée en vidéo se repère aussi à ses PREMIERS octets.
            val isVideoExt = finalName.substringAfterLast('.', "").lowercase() in
                setOf("mp4", "mkv", "ts", "avi", "webm", "mov", "m4v")
            val firstByte = runCatching {
                finalFile.inputStream().use { if (it.read() >= 0) it else -1 }
            }.getOrDefault(-1)
            val looksText = firstByte == 0x3C /* '<' */ || firstByte == 0x7B /* '{' */
            if (finalFile.length() > 0 && (looksText ||
                    (isVideoExt && finalFile.length() < 256 * 1024))) {
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
            // §integrite (conversation 10) : empreinte SHA-256 du fichier final,
            // conservée pour permettre une vérification ultérieure.
            val hash = runCatching { sha256(finalFile) }.getOrNull()
            if (hash != null) dao.setHash(taskId, hash)
            publishFinal(taskId, task, finalFile)
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
            publishProgress(
                DownloadProgress(
                    taskId, DownloadStatus.DOWNLOADING,
                    0, part.length(), 0, -1,
                    completedSegments = doneCount, totalSegments = totalCount,
                ),
            )
            val bytes = hlsEngine.download(plan, task.headersMap(), part, fromIdx) { idx ->
                dao.checkpoint(taskId, idx, 0, true)
                doneCount++
                publishProgress(
                    DownloadProgress(
                        taskId, DownloadStatus.DOWNLOADING,
                        0, part.length(), 0, etaSeconds = -1,
                        completedSegments = doneCount, totalSegments = totalCount,
                    ),
                )
            }
            dao.updateStatus(taskId, DownloadStatus.VERIFYING.name)
            val finalName = task.fileName.removeSuffix(".part")
            val finalFile = part.parentFile?.resolve(finalName) ?: File(finalName)
            part.renameTo(finalFile)
            val hash = runCatching { sha256(finalFile) }.getOrNull()
            dao.upsert(
                task.copy(
                    totalBytes = bytes,
                    sha256 = hash,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            publishFinal(taskId, dao.byId(taskId) ?: task, finalFile)
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

    /**
     * Déplace le fichier vers le stockage public et met l'URI à jour en base —
     * sans ça, la vidéo restait dans le dossier privé de l'app et ne se lisait
     * pas (bug « les vidéos téléchargées ne marchent pas »).
     */
    private suspend fun publishFinal(taskId: String, task: DownloadTaskEntity, file: File) {
        val pub = publisher ?: return
        val size = file.length()
        val uri = runCatching { pub(task, file) }.getOrNull() ?: return
        dao.upsert(
            (dao.byId(taskId) ?: task).copy(
                targetUri = uri,
                totalBytes = if (size > 0) size else task.totalBytes,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    // ------------------------------------------------------------ helpers    // ------------------------------------------------------------ helpers

    private fun partFile(fileName: String): File =
        tempDirProvider().resolve(".tmp/${fileName.removeSuffix(".part")}.part")

    private fun DownloadTaskEntity.headersMap(): Map<String, String> =
        headersJson.trim().removeSurrounding("{", "}").split(",")
            .mapNotNull { kv -> kv.split(":", limit = 2).takeIf { it.size == 2 } }
            .associate { it[0].trim().removeSurrounding("\"") to it[1].trim().removeSurrounding("\"") }

    companion object {
        private const val CHECKPOINT_EST = 256L * 1024       // speed-window granularity
        /** §espace-disque : marge gardée libre en plus de la taille du fichier. */
        private const val DISK_MARGIN_BYTES = 64L * 1024 * 1024
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
