package dev.endlesssea.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Upsert suspend fun upsertAll(items: List<MediaEntity>)
    @Query("SELECT * FROM media WHERE id = :id") suspend fun byId(id: String): MediaEntity?
    @Query("SELECT * FROM media WHERE titleKey LIKE '%' || :key || '%' LIMIT :limit")
    fun searchLocal(key: String, limit: Int = 50): Flow<List<MediaEntity>>
    @Query("SELECT * FROM media ORDER BY cachedAt DESC LIMIT :limit")
    fun recent(limit: Int = 20): Flow<List<MediaEntity>>

    /** §métadonnées-éditées : écrase le titre/affiche perso d'une source téléchargée. */
    @Query("UPDATE media SET customTitle = :title, customCoverUri = :coverUri WHERE id = :id")
    suspend fun setCustomMeta(id: String, title: String?, coverUri: String?)
}

@Dao
interface EpisodeDao {
    @Upsert suspend fun upsertAll(items: List<EpisodeEntity>)
    @Query("SELECT * FROM episodes WHERE mediaId = :mediaId ORDER BY season, number")
    fun ofMedia(mediaId: String): Flow<List<EpisodeEntity>>
}

@Dao
interface LibraryDao {
    @Upsert suspend fun upsert(entry: LibraryEntity)
    @Query("DELETE FROM library WHERE mediaId = :mediaId") suspend fun remove(mediaId: String)
    @Query("SELECT * FROM library WHERE category = :category ORDER BY addedAt DESC")
    fun observeByCategory(category: String): Flow<List<LibraryEntity>>
    @Query("SELECT * FROM library WHERE favorite = 1 ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<LibraryEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM library WHERE mediaId = :mediaId)")
    suspend fun contains(mediaId: String): Boolean
    @Query("SELECT * FROM library WHERE mediaId = :mediaId")
    suspend fun byMediaId(mediaId: String): LibraryEntity?
    /** Watchlist §29 : met à jour le statut de suivi d'un titre en bibliothèque. */
    @Query("UPDATE library SET status = :status WHERE mediaId = :mediaId")
    suspend fun setStatus(mediaId: String, status: String)
}

@Dao
interface WatchHistoryDao {
    @Upsert suspend fun upsert(entry: WatchHistoryEntity)
    @Query("SELECT * FROM watch_history WHERE episodeId = :id") suspend fun byEpisode(id: String): WatchHistoryEntity?
    /** "Continue watching": started, not finished (spec §10). */
    @Query("SELECT * FROM watch_history WHERE watched = 0 AND positionMs > 0 ORDER BY updatedAt DESC LIMIT :limit")
    fun observeContinueWatching(limit: Int = 12): Flow<List<WatchHistoryEntity>>
    /** Progression de fiche : épisodes terminés d'un média (§fiche-immersive). */
    @Query("SELECT COUNT(*) FROM watch_history WHERE mediaId = :mediaId AND watched = 1")
    suspend fun watchedCount(mediaId: String): Int

    /** Reprendre sur une fiche : dernier épisode commencé non terminé de ce média. */
    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId AND watched = 0 AND positionMs > 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun resumeForMedia(mediaId: String): WatchHistoryEntity?

    /**
     * §bibliotheque-statuts (conversation 6) : un SEUL passage pour classer toute
     * la bibliothèque en « en cours » / « terminé », au lieu d'une requête par
     * titre (la grille en affiche des centaines).
     */
    @Query(
        "SELECT mediaId, " +
            "SUM(CASE WHEN watched = 1 THEN 1 ELSE 0 END) AS watchedCount, " +
            "COUNT(*) AS total FROM watch_history GROUP BY mediaId",
    )
    suspend fun statsAll(): List<MediaWatchStat>
}

/** Résumé de visionnage d'un média (base des onglets En cours / Terminé). */
data class MediaWatchStat(val mediaId: String, val watchedCount: Int, val total: Int)

@Dao
interface DownloadsDao {
    @Upsert suspend fun upsert(task: DownloadTaskEntity)
    @Query("UPDATE download_tasks SET status = :status, error = :error, updatedAt = :at WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null, at: Long = System.currentTimeMillis())
    @Query("SELECT * FROM download_tasks ORDER BY priority DESC, createdAt ASC")
    fun observeAllOrdered(): Flow<List<DownloadTaskEntity>>
    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED','PROBING','DOWNLOADING','PAUSED')")
    fun observeActive(): Flow<List<DownloadTaskEntity>>
    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED','PROBING','DOWNLOADING') ORDER BY priority DESC, createdAt ASC")
    suspend fun schedulable(): List<DownloadTaskEntity>
    @Query("SELECT * FROM download_tasks WHERE id = :id") suspend fun byId(id: String): DownloadTaskEntity?
    /** Médias ayant au moins un fichier téléchargé (filtre « Sur l'appareil » de la bibliothèque). */
    @Query("SELECT DISTINCT mediaId FROM download_tasks WHERE status = 'COMPLETED' AND mediaId IS NOT NULL")
    suspend fun completedMediaIds(): List<String>
    @Query("SELECT EXISTS(SELECT 1 FROM download_tasks WHERE episodeId = :episodeId AND quality = :quality AND status = 'COMPLETED')")
    suspend fun alreadyDownloaded(episodeId: String, quality: String): Boolean
    @Query("UPDATE download_tasks SET priority = :p WHERE id = :id") suspend fun setPriority(id: String, p: Int)
    /** §hors-ligne : fichiers téléchargés d'un média (fiche « Sur l'appareil »). */
    @Query("SELECT * FROM download_tasks WHERE mediaId = :mediaId AND status = 'COMPLETED'")
    suspend fun completedForMedia(mediaId: String): List<DownloadTaskEntity>

    /**
     * §telecharges-bibliotheque (conversation 7) : TOUS les fichiers terminés,
     * y compris un épisode isolé sans fiche — la bibliothèque les regroupe.
     */
    @Query("SELECT * FROM download_tasks WHERE status = 'COMPLETED' ORDER BY updatedAt DESC")
    fun observeCompleted(): Flow<List<DownloadTaskEntity>>
    /** §deplacer-téléchargement : réoriente une tâche vers un nouveau targetUri (carte SD…). */
    @Query("UPDATE download_tasks SET targetUri = :targetUri, displayPath = :displayPath, updatedAt = :at WHERE id = :id")
    suspend fun setTarget(id: String, targetUri: String, displayPath: String, at: Long = System.currentTimeMillis())
    @Query("DELETE FROM download_tasks WHERE id = :id") suspend fun delete(id: String)

    @Upsert suspend fun upsertSegments(segments: List<DownloadSegmentEntity>)
    @Query("SELECT * FROM download_segments WHERE taskId = :taskId ORDER BY idx")
    suspend fun segments(taskId: String): List<DownloadSegmentEntity>
    @Query("UPDATE download_segments SET downloadedBytes = :bytes, done = :done WHERE taskId = :taskId AND idx = :idx")
    suspend fun checkpoint(taskId: String, idx: Int, bytes: Long, done: Boolean)
}

/** §megaskip — cache des segments + boutons de saut personnalisés. */
@Dao
interface SkipDao {
    @Query("SELECT * FROM skip_cache WHERE cacheKey = :key")
    suspend fun cached(key: String): SkipCacheEntity?

    @Query("SELECT * FROM skip_cache WHERE mediaKey = :mediaKey AND season = :season AND episode = :episode")
    suspend fun cachedFor(mediaKey: String, season: Int, episode: Int): List<SkipCacheEntity>

    @Upsert suspend fun put(entity: SkipCacheEntity)

    @Query("DELETE FROM skip_cache WHERE fetchedAt < :before")
    suspend fun purgeOlderThan(before: Long)

    @Query("DELETE FROM skip_cache")
    suspend fun clear()

    @Query("SELECT * FROM skip_buttons ORDER BY position, id")
    fun observeButtons(): Flow<List<SkipButtonEntity>>

    @Query("SELECT * FROM skip_buttons WHERE enabled = 1 ORDER BY position, id")
    suspend fun enabledButtons(): List<SkipButtonEntity>

    @Upsert suspend fun upsertButton(button: SkipButtonEntity): Long

    @Query("DELETE FROM skip_buttons WHERE id = :id")
    suspend fun deleteButton(id: Long)
}

@Dao
interface GenreDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(items: List<GenreEntity>)
    @Upsert suspend fun upsert(genre: GenreEntity)
    @Query("SELECT * FROM genres WHERE visible = 1 ORDER BY position") fun observeVisible(): Flow<List<GenreEntity>>
    @Query("SELECT * FROM genres ORDER BY position") fun observeAll(): Flow<List<GenreEntity>>
    @Query("UPDATE genres SET position = :position WHERE id = :id") suspend fun reorder(id: Long, position: Int)
    @Query("UPDATE genres SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Query("UPDATE genres SET visible = :visible WHERE id = :id") suspend fun setVisible(id: Long, visible: Boolean)
    @Query("DELETE FROM genres WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface RepoDao {
    @Upsert suspend fun upsert(repo: RepoEntity)
    @Query("SELECT * FROM repos ORDER BY addedAt") fun observeAll(): Flow<List<RepoEntity>>
    @Query("SELECT * FROM repos WHERE enabled = 1") suspend fun enabled(): List<RepoEntity>
    @Query("UPDATE repos SET enabled = :enabled WHERE url = :url") suspend fun setEnabled(url: String, enabled: Boolean)
    @Query("DELETE FROM repos WHERE url = :url") suspend fun delete(url: String)
}

@Dao
interface ExtensionDao {
    @Upsert suspend fun upsert(extension: ExtensionEntity)
    @Query("SELECT * FROM extensions ORDER BY name") fun observeInstalled(): Flow<List<ExtensionEntity>>
    @Query("SELECT * FROM extensions WHERE status = 'ENABLED'") suspend fun enabled(): List<ExtensionEntity>
    @Query("SELECT * FROM extensions WHERE pkg = :pkg") suspend fun byId(pkg: String): ExtensionEntity?
    @Query("UPDATE extensions SET status = :status WHERE pkg = :pkg") suspend fun setStatus(pkg: String, status: String)
    @Query("UPDATE extensions SET lastError = :error WHERE pkg = :pkg") suspend fun logError(pkg: String, error: String?)
    @Query("DELETE FROM extensions WHERE pkg = :pkg") suspend fun delete(pkg: String)
}
