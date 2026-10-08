package dev.endlesssea.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Unfiltered snapshots: completed history and user-defined categories must not disappear. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM library ORDER BY mediaId")
    suspend fun library(): List<LibraryEntity>
    @Query("SELECT * FROM watch_history ORDER BY episodeId")
    suspend fun history(): List<WatchHistoryEntity>
    @Query("SELECT * FROM media ORDER BY id")
    suspend fun media(): List<MediaEntity>
    @Query("SELECT * FROM genres ORDER BY position, id")
    suspend fun genres(): List<GenreEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLibrary(rows: List<LibraryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMedia(rows: List<MediaEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(rows: List<WatchHistoryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenres(rows: List<GenreEntity>)
}
