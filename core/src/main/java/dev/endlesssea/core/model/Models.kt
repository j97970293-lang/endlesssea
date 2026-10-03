package dev.endlesssea.core.model

/** App-side enums & shared state models (DB mirrors in :data). */

enum class LibraryCategory { ANIME, FILMS, SERIES, OVA, ONA, CUSTOM }

enum class DownloadStatus {
    QUEUED, PROBING, DOWNLOADING, PAUSED, VERIFYING, MERGING, COMPLETED, FAILED, CANCELLED;

    val isActive: Boolean get() = this == QUEUED || this == PROBING || this == DOWNLOADING
}

enum class TrustLevel { OFFICIAL, REPO_VERIFIED, UNKNOWN }

enum class ExtensionStatus { ENABLED, DISABLED, INCOMPATIBLE }

/** Live progress emitted by the download engine to UI/notification layers. */
data class DownloadProgress(
    val taskId: String,
    val status: DownloadStatus,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long,
) {
    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

/** Normalized location shown in Settings: "/Téléchargements/EndlessSea/" etc. */
sealed interface StorageTarget {
    data object Internal : StorageTarget
    data class MediaStoreDownloads(val displayPath: String) : StorageTarget
    data class SafTree(val treeUri: String, val displayPath: String) : StorageTarget
}
