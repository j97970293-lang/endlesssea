package dev.endlesssea.app.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Kept in the ViewModel, not a remembered screen value. Large snapshots never enter a Bundle. */
data class BackupSessionState<T>(
    val busy: Boolean = false,
    val pending: T? = null,
    val restorePreferences: Boolean = true,
    val message: String? = null,
)

/** Call actions on the UI thread; the injected scope owns operations across screen recreation. */
internal class BackupSession<T>(
    private val scope: CoroutineScope,
    private val read: suspend (String) -> T,
    private val write: suspend (String) -> Unit,
    private val restore: suspend (T, Boolean) -> String,
) {
    private val mutable = MutableStateFlow(BackupSessionState<T>())
    val state: StateFlow<BackupSessionState<T>> = mutable.asStateFlow()

    fun preview(uri: String) {
        if (mutable.value.pending != null) return
        execute("Import refusé. Aucune donnée modifiée") {
            val snapshot = read(uri)
            mutable.value = mutable.value.copy(pending = snapshot, restorePreferences = true)
        }
    }

    fun export(uri: String) {
        if (mutable.value.pending != null) return
        execute("Échec de l'export. Le fichier de destination peut être incomplet") {
            write(uri)
            mutable.value = mutable.value.copy(message = "Fichier de sauvegarde écrit")
        }
    }

    fun dismiss() {
        if (!mutable.value.busy) mutable.value = mutable.value.copy(pending = null)
    }

    fun setRestorePreferences(value: Boolean) {
        if (!mutable.value.busy) mutable.value = mutable.value.copy(restorePreferences = value)
    }

    fun confirm() {
        val snapshot = mutable.value.pending ?: return
        val preferences = mutable.value.restorePreferences
        execute("Échec de la restauration. Certaines étapes peuvent avoir été appliquées ; une nouvelle fusion est possible") {
            val message = restore(snapshot, preferences)
            mutable.value = mutable.value.copy(pending = null, message = message)
        }
    }

    fun clearMessage(message: String) {
        if (mutable.value.message == message) mutable.value = mutable.value.copy(message = null)
    }

    private fun execute(errorPrefix: String, action: suspend () -> Unit) {
        if (mutable.value.busy) return
        // Set synchronously: rapid double taps cannot schedule duplicate writes.
        mutable.value = mutable.value.copy(busy = true, message = null)
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.value = mutable.value.copy(message = "$errorPrefix : ${e.message ?: "erreur de stockage"}")
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
