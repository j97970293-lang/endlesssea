package dev.endlesssea.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** docs/en/09-database-model.md — every table of endless-sea.db v1. */

@Entity(tableName = "media", indices = [Index("extensionId"), Index("titleKey")])
data class MediaEntity(
    @PrimaryKey val id: String,            // "<extensionId>:<mediaKey>"
    val extensionId: String,
    val type: String,                      // MediaType
    val title: String,
    val titleKey: String,                  // accent-folded lowercase (FileNames.normalizedKey)
    val altTitlesJson: String = "[]",
    val synopsis: String? = null,
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val year: Int? = null,
    val status: String? = null,
    val rating: Double? = null,
    val genresJson: String = "[]",
    val languagesJson: String = "[]",
    val studiosJson: String = "[]",
    val episodeCount: Int? = null,
    val durationMin: Int? = null,
    val externalIdsJson: String = "{}",
    /** §métadonnées-éditées : titre perso (prioritaire sur title si non nul). */
    val customTitle: String? = null,
    /** §métadonnées-éditées : affiche perso (URL ou content://). */
    val customCoverUri: String? = null,
    val cachedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "episodes",
    foreignKeys = [ForeignKey(MediaEntity::class, ["id"], ["mediaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mediaId")],
)
data class EpisodeEntity(
    @PrimaryKey val id: String,            // "<mediaId>:S1:E3"
    val mediaId: String,
    val season: Int?,
    val number: Float,
    val title: String?,
    val thumbnailUrl: String? = null,
    val durationMs: Long? = null,
    val airDate: String? = null,
    val data: String,                      // opaque provider payload
)

@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val mediaId: String,       // FK media.id (kept soft to survive extension removal)
    val category: String,                  // LibraryCategory
    val favorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val customGenresJson: String? = null,
    val sortOverride: String? = null,
    /** Watchlist §29 : NONE / WISHLIST / WATCHING / COMPLETED / DROPPED. */
    val status: String = "NONE",
)

@Entity(tableName = "watch_history", indices = [Index("updatedAt")])
data class WatchHistoryEntity(
    @PrimaryKey val episodeId: String,
    val mediaId: String,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean = false,          // ≥ 90 % auto-marked
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "download_tasks", indices = [Index("status"), Index("priority")])
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val mediaId: String? = null,
    val episodeId: String? = null,
    val url: String,
    val headersJson: String = "{}",
    val server: String = "",
    val quality: String = "UNKNOWN",
    val streamType: String,                // DIRECT_FILE / HLS / DASH
    val targetUri: String,                 // file:// or SAF content://
    val fileName: String,
    val displayPath: String = "",
    val subtitlesJson: String = "[]",      // chosen tracks + local uris
    val totalBytes: Long = 0,
    val status: String = "QUEUED",         // DownloadStatus
    val error: String? = null,
    val priority: Int = 0,
    val etag: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "download_segments",
    primaryKeys = ["taskId", "idx"],
    foreignKeys = [ForeignKey(DownloadTaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
)
data class DownloadSegmentEntity(
    val taskId: String,
    val idx: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0,
    val done: Boolean = false,
)

/** User-managed genres: add / rename / reorder / hide (spec §9). */
@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val visible: Boolean = true,
    val position: Int = 0,
    val builtin: Boolean = false,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,            // ANIME/FILMS/SERIES/OVA/ONA/CUSTOM_*
    val label: String,
    val position: Int,
)

@Entity(tableName = "repos")
data class RepoEntity(
    @PrimaryKey val url: String,
    val name: String,
    val enabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
    val lastSyncAt: Long = 0,
    val etag: String? = null,
)

@Entity(tableName = "extensions")
data class ExtensionEntity(
    @PrimaryKey val pkg: String,           // manifest id
    val name: String,
    val version: Int,
    val versionName: String,
    val apiVersion: Int,
    val author: String = "",
    val descriptionJson: String = "{}",
    val languagesJson: String = "[]",
    val typesJson: String = "[]",
    val capabilitiesJson: String = "{}",
    val repoUrl: String? = null,
    /** §4 : icône persistée à l'installation (robuste aussi pour les .esx hors dépôt). */
    val iconUrl: String? = null,
    val status: String = "ENABLED",        // ExtensionStatus
    val trust: String = "UNKNOWN",         // TrustLevel
    val permissionsJson: String = "[]",
    val certSha256: String? = null,        // signing-cert digest (update-safety, doc 04 §7)
    val packageSha256: String = "",
    val installedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0,
    val lastError: String? = null,         // journal des erreurs (spec §17)
)
