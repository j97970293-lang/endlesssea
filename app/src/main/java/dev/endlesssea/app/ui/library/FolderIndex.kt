package dev.endlesssea.app.ui.library

/** Stable file identity across different SAF tree permissions. Never match videos by filename. */
internal fun localFileIdentity(value: String): String = runCatching {
    val uri = java.net.URI(value)
    if (uri.scheme == "content" && uri.path.contains("/document/")) {
        "content://${uri.authority}/document/" + uri.path.substringAfter("/document/")
    } else uri.normalize().toString()
}.getOrDefault(value)

internal data class IndexedFolder<T>(val uri: String, val episodes: List<T>)

internal fun <T> indexFolders(
    files: List<T>, managedUris: Set<String> = emptySet(),
    uri: (T) -> String, parent: (T) -> String,
): List<IndexedFolder<T>> {
    val managed = managedUris.map(::localFileIdentity).toSet()
    return files.distinctBy { localFileIdentity(uri(it)) }
        .filter { localFileIdentity(uri(it)) !in managed }
        .groupBy { parent(it).ifBlank { uri(it) } }
        .map { (folder, episodes) -> IndexedFolder(folder, episodes) }
}
