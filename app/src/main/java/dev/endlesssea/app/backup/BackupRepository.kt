package dev.endlesssea.app.backup

import androidx.room.withTransaction
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.data.db.EsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(private val database: EsDatabase, private val prefs: AppPrefs) {
    private val mutex = Mutex()

    suspend fun export(): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val dao = database.backupDao()
            val snapshot = database.withTransaction {
                BackupSnapshot(exportedAt = System.currentTimeMillis(), library = dao.library(), history = dao.history(),
                    media = dao.media(), genres = dao.genres(),
                    customCategories = prefs.customCategories.value.associateWith { prefs.categoryItems(it) },
                    localMetadata = prefs.localFileMetadataSnapshot(),
                    preferences = BackupPreferences(prefs.themeMode.value, prefs.defaultSpeed.value, prefs.autoResume.value,
                        prefs.skipSeconds.value, prefs.playerTheme.value, prefs.toolsPosition.value,
                        prefs.progressPosition.value, prefs.megaSkipSide.value, prefs.recordHistory.value))
            }
            BackupCodec.encode(snapshot)
        }
    }

    suspend fun restore(snapshot: BackupSnapshot, restorePreferences: Boolean): String = mutex.withLock {
        val counts = withContext(Dispatchers.IO) {
            val dao = database.backupDao()
            database.withTransaction {
                val existingLibrary = dao.library().map { it.mediaId }.toSet()
                val newLibrary = snapshot.library.filter { it.mediaId !in existingLibrary }
                val history = mergeBackupHistory(dao.history(), snapshot.history)
                val names = dao.genres().map { it.name.lowercase(Locale.ROOT) }.toMutableSet()
                val genres = snapshot.genres.filter { names.add(it.name.lowercase(Locale.ROOT)) }.map { it.copy(id = 0) }
                dao.insertMedia(snapshot.media)
                dao.insertLibrary(newLibrary)
                dao.upsertHistory(history)
                dao.insertGenres(genres)
                newLibrary.size to history.size
            }
        }
        // Room is atomic; portable preferences are a separate store, merged after DB success.
        withContext(Dispatchers.Main.immediate) {
            prefs.mergeBackupCollections(snapshot.customCategories, snapshot.localMetadata)
            if (restorePreferences) snapshot.preferences?.let {
                prefs.setThemeMode(it.themeMode); prefs.setDefaultSpeed(it.defaultSpeed)
                prefs.setAutoResume(it.autoResume); prefs.setSkipSeconds(it.skipSeconds)
                prefs.setPlayerTheme(it.playerTheme); prefs.setToolsPosition(it.playerToolsPosition)
                prefs.setProgressPosition(it.playerProgressPosition); prefs.setMegaSkipSide(it.megaSkipSide)
                prefs.setRecordHistory(it.recordHistory)
            }
        }
        "Fusion terminée : ${counts.first} entrée(s) ajoutée(s), ${counts.second} progression(s) ajoutée(s) ou actualisée(s). Données existantes conservées en cas de conflit."
    }
}
