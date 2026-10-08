package dev.endlesssea.app.backup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BackupSessionTest {
    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val writes = mutableListOf<String>()
        val restorations = mutableListOf<Pair<String, Boolean>>()
        var load: suspend (String) -> String = { "snapshot:$it" }
        var save: suspend (String) -> Unit = { writes += it }
        var apply: suspend (String, Boolean) -> String = { snapshot, preferences ->
            restorations += snapshot to preferences; "done"
        }
        val session = BackupSession(scope, { load(it) }, { save(it) }, { data, preferences -> apply(data, preferences) })
    }
    private fun checkSession(block: suspend Fixture.() -> Unit) = runBlocking {
        val fixture = Fixture()
        try { fixture.block() } finally { fixture.scope.cancel() }
    }

    @Test fun readingOnlyPreviewsAndNeverRestores() = checkSession {
        session.preview("file")
        assertEquals("snapshot:file", session.state.value.pending)
        assertTrue(restorations.isEmpty())
        assertFalse(session.state.value.busy)
    }
    @Test fun cancelPreviewDoesNotWriteAnything() = checkSession {
        session.preview("file"); session.dismiss(); session.confirm()
        assertNull(session.state.value.pending)
        assertTrue(restorations.isEmpty())
        assertTrue(writes.isEmpty())
    }
    @Test fun confirmationUsesTheSelectedPreferenceChoice() = checkSession {
        session.preview("file"); session.setRestorePreferences(false); session.confirm()
        assertEquals(listOf("snapshot:file" to false), restorations)
        assertNull(session.state.value.pending)
        assertEquals("done", session.state.value.message)
    }
    @Test fun duplicateClicksCannotScheduleDuplicateRestores() = checkSession {
        val gate = CompletableDeferred<Unit>()
        apply = { value, preferences -> restorations += value to preferences; gate.await(); "done" }
        session.preview("file"); session.confirm(); session.confirm()
        session.dismiss(); session.setRestorePreferences(false)
        assertTrue(session.state.value.busy)
        assertNotNull(session.state.value.pending)
        assertTrue(session.state.value.restorePreferences)
        assertEquals(1, restorations.size)
        gate.complete(Unit)
        assertFalse(session.state.value.busy)
    }
    @Test fun overlappingFileActionsAreIgnoredDuringReading() = checkSession {
        val gate = CompletableDeferred<String>()
        var reads = 0
        load = { reads++; gate.await() }
        session.preview("first"); session.preview("second"); session.export("target")
        assertEquals(1, reads)
        assertTrue(writes.isEmpty())
        gate.complete("data")
        assertEquals("data", session.state.value.pending)
    }
    @Test fun aPendingPreviewCannotBeSilentlyReplaced() = checkSession {
        session.preview("first"); session.preview("second"); session.export("target")
        assertEquals("snapshot:first", session.state.value.pending)
        assertTrue(writes.isEmpty())
    }
    @Test fun malformedReadLeavesNoPreviewAndReportsNoChanges() = checkSession {
        load = { error("invalid JSON") }
        session.preview("bad")
        assertNull(session.state.value.pending)
        assertTrue(session.state.value.message!!.contains("Aucune donnée modifiée"))
        assertFalse(session.state.value.busy)
        assertTrue(restorations.isEmpty())
    }
    @Test fun restoreFailureKeepsPreviewForRetry() = checkSession {
        apply = { _, _ -> error("disk full") }
        session.preview("file"); session.confirm()
        assertNotNull(session.state.value.pending)
        assertTrue(session.state.value.message!!.contains("disk full"))
        assertFalse(session.state.value.busy)
        apply = { _, _ -> "retried" }
        session.confirm()
        assertNull(session.state.value.pending)
        assertEquals("retried", session.state.value.message)
    }
    @Test fun failedExportDoesNotDisplaySuccess() = checkSession {
        save = { error("permission lost") }
        session.export("target")
        assertTrue(session.state.value.message!!.contains("Échec de l'export"))
        assertFalse(session.state.value.busy)
    }
    @Test fun lateSnackbarAcknowledgementCannotClearANewerError() = checkSession {
        session.export("good")
        val oldMessage = session.state.value.message!!
        save = { error("new error") }
        session.export("bad"); session.clearMessage(oldMessage)
        assertTrue(session.state.value.message!!.contains("new error"))
    }
    @Test fun cancelledReadIsNotReportedAsInvalidBackup() = checkSession {
        load = { awaitCancellation() }
        session.preview("file"); scope.cancel()
        assertNull(session.state.value.message)
        assertNull(session.state.value.pending)
        assertFalse(session.state.value.busy)
    }
}
