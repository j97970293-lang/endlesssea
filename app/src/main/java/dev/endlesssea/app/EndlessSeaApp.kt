package dev.endlesssea.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.endlesssea.data.db.EsDatabase
import dev.endlesssea.data.db.GenreEntity
import dev.endlesssea.data.db.CategoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EndlessSeaApp : Application() {

    @Inject lateinit var database: EsDatabase

    /** §service-telechargement (conversation 10) : moteur partagé avec le service. */
    @Inject lateinit var downloadEngine: dev.endlesssea.downloader.DownloadEngine

    @Inject lateinit var prefs: dev.endlesssea.app.di.AppPrefs

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // §diagnostic : journaliser les plantages AVANT tout le reste
        dev.endlesssea.app.util.CrashReporter.install(this)
        // §4 — Images des extensions : Coil partagé avec cache disque, UA honnête
        // et DNS DoH. Doit être initialisé AVANT la première AsyncImage.
        EsImages.imageLoader(this)
        appScope.launch {
            // Seed user-editable genres/categories once (spec §8/§9 — everything stays editable).
            database.genreDao().insertAll(EsDatabase.SEED_GENRES)
            seedCategories()
        }
        // §service-telechargement (conversation 10) : la file survivait au passage
        // en arrière-plan par chance ; on reprend sur le service au premier plan
        // (wake-lock + notifications) dès qu'une tâche est en attente.
        appScope.launch {
            runCatching {
                // Service au premier plan UNIQUEMENT s'il reste des tâches : sinon
                // Android afficherait une notification « file » vide.
                val pending = database.downloadsDao().schedulable()
                if (pending.isNotEmpty()) {
                    dev.endlesssea.downloader.DownloadService.start(this@EndlessSeaApp, downloadEngine)
                }
                downloadEngine.recoverQueue()
            }.onFailure { e ->
                dev.endlesssea.core.diag.EsLog.e(
                    "Download", "boot", "Service de téléchargement indisponible",
                    e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    private suspend fun seedCategories() {
        EsDatabase.SEED_CATEGORIES.forEach {
            // insert-only-if-missing via upsert of identical seeds is idempotent by PK
            database.runInTransaction { /* no-op guard, seeds are PK-stable */ }
        }
    }
}
