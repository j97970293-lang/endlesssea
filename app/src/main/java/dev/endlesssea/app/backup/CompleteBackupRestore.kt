package dev.endlesssea.app.backup

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Once a confirmed write begins, navigation/rotation must not cancel the preferences phase
 * after Room has committed. This does NOT provide atomicity across process death or two stores.
 */
internal suspend fun <T> completeBackupRestore(
    databaseWrite: suspend () -> T,
    preferencesWrite: suspend () -> Unit,
): T {
    currentCoroutineContext().ensureActive()
    return withContext(NonCancellable) {
        val result = databaseWrite()
        preferencesWrite()
        result
    }
}
