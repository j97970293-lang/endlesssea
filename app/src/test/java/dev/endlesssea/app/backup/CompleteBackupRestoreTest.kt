package dev.endlesssea.app.backup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CompleteBackupRestoreTest {
    @Test fun databaseThenPreferencesAndReturnCounts() = runBlocking {
        val steps = mutableListOf<String>()
        val result = completeBackupRestore(
            databaseWrite = { steps += "database"; 7 },
            preferencesWrite = { steps += "preferences" },
        )
        assertEquals(listOf("database", "preferences"), steps)
        assertEquals(7, result)
    }
    @Test fun databaseFailureDoesNotApplyPreferences() = runBlocking {
        var preferencesApplied = false
        try {
            completeBackupRestore<Int>({ error("database unavailable") }, { preferencesApplied = true })
            fail("Expected database error")
        } catch (_: IllegalStateException) { }
        assertFalse(preferencesApplied)
    }
    @Test fun cancellationAfterWriteStartsStillCompletesBothPhases() = runBlocking {
        withTimeout(3000) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val steps = mutableListOf<String>()
            val job = launch {
                completeBackupRestore(
                    databaseWrite = { started.complete(Unit); release.await(); steps += "database" },
                    preferencesWrite = { yield(); steps += "preferences" },
                )
            }
            started.await(); job.cancel(); release.complete(Unit); job.join()
            assertEquals(listOf("database", "preferences"), steps)
        }
    }
    @Test fun alreadyCancelledCallerMustNotBeginWrites() = runBlocking {
        var writes = 0
        val job = launch {
            currentCoroutineContext().cancel()
            try { completeBackupRestore({ writes++ }, { writes++ }) }
            catch (_: CancellationException) { }
        }
        job.join()
        assertEquals(0, writes)
    }
    @Test fun preferenceErrorsRemainVisibleToCaller() = runBlocking {
        var committed = false
        try {
            completeBackupRestore({ committed = true }, { error("preference failure") })
            fail("Expected preference error")
        } catch (e: IllegalStateException) { assertEquals("preference failure", e.message) }
        assertTrue(committed)
    }
}
