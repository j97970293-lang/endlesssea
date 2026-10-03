package dev.endlesssea.extensions.loader

import dev.endlesssea.data.db.RepoDao
import dev.endlesssea.data.db.RepoEntity
import dev.endlesssea.extensions.api.manifest.ManifestParser
import dev.endlesssea.extensions.api.manifest.RepositoryIndex
import dev.endlesssea.extensions.api.manifest.RepoExtensionEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Extension repositories (spec §18, docs/en/04 §4): any HTTPS URL serving an index.json.
 * Add / remove / enable / refresh; conditional GET via stored ETag; offline cache.
 */
class RepoManager(
    private val dao: RepoDao,
    private val http: OkHttpClient,
) {

    sealed interface SyncResult {
        data class Fresh(val url: String, val index: RepositoryIndex) : SyncResult
        data class NotModified(val url: String) : SyncResult
        data class Failed(val url: String, val reason: String) : SyncResult
    }

    suspend fun addRepository(url: String): Result<RepositoryIndex> = runCatching {
        val index = fetch(url, etag = null)
        dao.upsert(
            RepoEntity(url = url, name = index.index.name, etag = index.etag,
                lastSyncAt = System.currentTimeMillis())
        )
        index.index
    }

    suspend fun removeRepository(url: String) = dao.delete(url)

    /** Refreshes one repository; 304 = cheap cache hit. */
    suspend fun sync(url: String): SyncResult = runCatching {
        val current = dao.enabled().firstOrNull { it.url == url }
        val result = fetch(url, current?.etag)
        when (result) {
            is FetchResult.Fresh -> {
                dao.upsert(
                    RepoEntity(url = url, name = result.index.name, etag = result.etag,
                        enabled = current?.enabled ?: true, lastSyncAt = System.currentTimeMillis())
                )
                SyncResult.Fresh(url, result.index)
            }
            is FetchResult.NotModified -> SyncResult.NotModified(url)
        }
    }.getOrElse { SyncResult.Failed(url, it.message ?: "error") }

    /** New versions available online vs installed rows (badge « Mise à jour »). */
    fun pendingUpdates(
        online: List<RepoExtensionEntry>,
        installedVersions: Map<String, Int>,
    ): List<RepoExtensionEntry> =
        online.filter { (installedVersions[it.id] ?: 0) in 1 until it.version }

    // ----------------------------------------------------------------

    private sealed interface FetchResult {
        data class Fresh(val index: RepositoryIndex, val etag: String?) : FetchResult
        data object NotModified : FetchResult
    }

    private suspend fun fetch(url: String, etag: String?): FetchResult = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()
        http.newCall(req).execute().use { res ->
            when {
                res.code == 304 -> FetchResult.NotModified
                !res.isSuccessful -> error("HTTP ${res.code}")
                else -> FetchResult.Fresh(
                    ManifestParser.parseRepoIndex(res.body.string()),
                    res.header("ETag"),
                )
            }
        }
    }
}
