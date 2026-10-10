package dev.endlesssea.downloader

/** How many tasks and segment connections a download may open right now. */
data class DownloadParallelism(val tasks: Int, val parts: Int)

/**
 * Caps parallelism for data-saver mode and low available memory.
 * Never raises the user's chosen limits. Direct-file segments are the expensive part;
 * HLS/DASH already fetch one segment at a time.
 */
fun effectiveDownloadParallelism(
    requestedTasks: Int,
    requestedParts: Int,
    dataSaver: Boolean,
    availableMemoryBytes: Long,
): DownloadParallelism {
    val tasks = requestedTasks.coerceIn(1, 4)
    val parts = requestedParts.coerceIn(1, 8)
    if (dataSaver || availableMemoryBytes in 1 until 192L * 1024 * 1024) {
        return DownloadParallelism(1, 1)
    }
    if (availableMemoryBytes in 1 until 384L * 1024 * 1024) {
        return DownloadParallelism(1, parts.coerceAtMost(2))
    }
    return DownloadParallelism(tasks, parts)
}
