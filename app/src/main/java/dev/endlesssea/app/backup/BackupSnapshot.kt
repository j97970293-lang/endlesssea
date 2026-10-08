package dev.endlesssea.app.backup

import dev.endlesssea.app.di.AppPrefs.LocalFileMeta
import dev.endlesssea.data.db.*

data class BackupPreferences(
    val themeMode: Int,
    val defaultSpeed: Float,
    val autoResume: Boolean,
    val skipSeconds: Int,
    val playerTheme: String,
    val playerToolsPosition: String,
    val playerProgressPosition: String,
    val megaSkipSide: String,
    val recordHistory: Boolean,
)

data class BackupSnapshot(
    val version: Int = 2,
    val exportedAt: Long,
    val library: List<LibraryEntity>,
    val history: List<WatchHistoryEntity>,
    val media: List<MediaEntity> = emptyList(),
    val genres: List<GenreEntity> = emptyList(),
    val customCategories: Map<String, List<String>> = emptyMap(),
    val localMetadata: Map<String, LocalFileMeta> = emptyMap(),
    val preferences: BackupPreferences? = null,
)

/** Strictly newer wins; equal timestamps retain this device's data. */
internal fun mergeBackupHistory(current: List<WatchHistoryEntity>, incoming: List<WatchHistoryEntity>): List<WatchHistoryEntity> {
    val byId = current.associateBy { it.episodeId }
    return incoming.filter { row -> byId[row.episodeId]?.let { row.updatedAt > it.updatedAt } ?: true }
}
