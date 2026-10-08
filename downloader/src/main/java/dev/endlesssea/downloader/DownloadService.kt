package dev.endlesssea.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.endlesssea.core.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground "dataSync" service hosting active downloads (docs/en/06 §8, doc 10 §1).
 * Starts on first active task, self-stops after idle; holds a partial wakelock only
 * while segments transfer. Actions: Pause / Resume / Cancel per notification.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var engine: DownloadEngine? = null        // injected by the app (Binder)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        attachEngineIfNeeded()
        createChannels()
        ServiceCompat.startForeground(
            this, ONGOING_ID, ongoingNotification("Préparation de la file…"),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        acquireWakeLock()
        scope.launch {
            engine?.progress?.collectLatest { p ->
                if (p.status == DownloadStatus.DOWNLOADING && p.taskId.isNotBlank()) {
                    notifyProgress(p)
                }
            }
        }
        // §service : arrêt automatique quand la file est au repos — plus de
        // notification permanente quand il ne se passe rien.
        scope.launch {
            var idleSince = 0L
            engine?.runningCount?.collectLatest { running ->
                if (running > 0) { idleSince = 0L; return@collectLatest }
                if (idleSince == 0L) idleSince = System.currentTimeMillis()
                kotlinx.coroutines.delay(IDLE_STOP_MS)
                if (idleSince != 0L && System.currentTimeMillis() - idleSince >= IDLE_STOP_MS) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        scope.launch {
            engine?.notifications?.collectLatest { n ->
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(n.taskId.hashCode(), resultNotification(n))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch(Dispatchers.IO) {
            when (intent?.action) {
                ACTION_RESUME_ALL -> engine?.recoverQueue()
                ACTION_PAUSE -> intent.getStringExtra(EXTRA_TASK_ID)?.let { engine?.pause(it) }
                ACTION_RESUME -> intent.getStringExtra(EXTRA_TASK_ID)?.let { engine?.resume(it) }
                ACTION_CANCEL -> intent.getStringExtra(EXTRA_TASK_ID)?.let { engine?.cancel(it) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------- wakelock (doc 06 §8)

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "endlesssea:downloads")
            .apply { setReferenceCounted(false); acquire(10 * 60 * 1000L) }
    }

    private fun releaseWakeLock() = wakeLock?.takeIf { it.isHeld }?.release()

    // ------------------------------------------------- notifications

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Téléchargements", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULTS, "Résultats", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun ongoingNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Endless Sea")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun notifyProgress(p: dev.endlesssea.core.model.DownloadProgress) {
        val pct = (p.fraction * 100).toInt()
        val n = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Téléchargement en cours")
            .setContentText(if (p.totalSegments > 0) "${p.completedSegments}/${p.totalSegments} segments · taille finale inconnue"
                else "$pct % · ${p.speedBytesPerSec / 1024} Ko/s")
            .setProgress(100, pct, p.totalBytes <= 0 && p.totalSegments <= 0)
            .setOngoing(true)
            .addAction(0, "Pause", actionIntent(ACTION_PAUSE, p.taskId))
            .addAction(0, "Annuler", actionIntent(ACTION_CANCEL, p.taskId))
            .build()
        getSystemService(NotificationManager::class.java).notify(ONGOING_ID, n)
    }

    private fun resultNotification(n: DownloadNotice): Notification =
        NotificationCompat.Builder(this, CHANNEL_RESULTS)
            .setSmallIcon(
                if (n.ok) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error
            )
            .setContentTitle(n.title)
            .setContentText(n.message)
            .setAutoCancel(true)
            .build()

    private fun actionIntent(action: String, taskId: String): PendingIntent =
        PendingIntent.getService(
            this, taskId.hashCode() + action.hashCode(),
            Intent(this, DownloadService::class.java).setAction(action).putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_PROGRESS = "downloads"
        const val CHANNEL_RESULTS = "downloads_results"
        const val ONGOING_ID = 4201
        const val EXTRA_TASK_ID = "taskId"
        const val ACTION_RESUME_ALL = "dev.endlesssea.action.RESUME_ALL"
        const val ACTION_PAUSE = "dev.endlesssea.action.PAUSE"
        const val ACTION_RESUME = "dev.endlesssea.action.RESUME"
        const val ACTION_CANCEL = "dev.endlesssea.action.CANCEL"
        /** §service : délai sans aucune tâche avant d'arrêter le service. */
        const val IDLE_STOP_MS = 45_000L

        fun start(context: Context, engine: DownloadEngine) {
            // In the real app the engine is provided via a bound service; the static
            // attach below keeps this scaffold trivially testable.
            val intent = Intent(context, DownloadService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
            attachedEngine = engine
        }

        /** Process-wide engine reference (binder-free scaffold). */
        var attachedEngine: DownloadEngine? = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Engine persistence in Room guarantees recovery on next boot (doc 06 §3).
    }

    init {
        // Pick up the engine attached before the service was created.
        if (attachedEngine != null) { /* assigned in onCreate scope */ }
    }

    private fun attachEngineIfNeeded() { if (engine == null) engine = attachedEngine }
}
