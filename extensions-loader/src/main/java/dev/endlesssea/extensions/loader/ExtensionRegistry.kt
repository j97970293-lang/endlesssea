package dev.endlesssea.extensions.loader

import android.content.Context
import dev.endlesssea.extensions.api.EsExtension
import dev.endlesssea.data.db.ExtensionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runtime registry: instantiates activated extensions on demand and caches them.
 * Read path for every UI feature (search fan-out, home rows, details, links).
 */
class ExtensionRegistry(
    private val context: Context,
    private val loader: ExtensionLoader,
    private val extensionDao: ExtensionDao,
) {
    private val cache = mutableMapOf<String, EsExtension>()
    private val mutex = Mutex()

    /** (displayName → extension) for every ENABLED installed extension. */
    suspend fun enabledExtensions(): List<Pair<String, EsExtension>> = withContext(Dispatchers.IO) {
        extensionDao.enabled().mapNotNull { row ->
            runCatching { row.name to instance(row.pkg) }
                .onFailure { extensionDao.logError(row.pkg, it.message) }
                .getOrNull()
        }
    }

    suspend fun instance(pkg: String): EsExtension = mutex.withLock {
        cache.getOrPut(pkg) {
            val row = extensionDao.byId(pkg) ?: error("extension $pkg not installed")
            val pkgFile = File(File(context.filesDir, "extensions"), "${row.pkg}/${row.version}/${row.pkg}.esx")
            val manifest = loader.inspectPackage(pkgFile)?.first ?: error("manifest unreadable for $pkg")
            loader.instantiate(pkgFile, manifest, locale = context.resources.configuration.locales[0].language)
        }
    }

    suspend fun invalidate(pkg: String) = mutex.withLock { cache.remove(pkg) }

    suspend fun clear() = mutex.withLock { cache.clear() }
}
