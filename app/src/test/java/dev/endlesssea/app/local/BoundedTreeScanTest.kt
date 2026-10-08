package dev.endlesssea.app.local

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class BoundedTreeScanTest {
    @Test fun eightParentsWithChildrenDoNotDeadlock() = runBlocking {
        val visited = mutableSetOf<String>()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        withTimeout(3000) {
            scanTreeBounded("root", 3) { id ->
                peak.updateAndGet { maxOf(it, active.incrementAndGet()) }
                delay(5)
                visited += id
                active.decrementAndGet()
                when {
                    id == "root" -> (1..8).map { "parent$it" }
                    id.startsWith("parent") -> listOf("child$id", "leaf$id")
                    else -> emptyList()
                }
            }
        }
        assertEquals(25, visited.size)
        assertTrue(peak.get() <= 8)
    }
    @Test fun folderResumeNeverWalksChildFolders() = runBlocking {
        val visited = mutableListOf<String>()
        scanTreeBounded("folder", 0) { id -> visited += id; listOf("child") }
        assertEquals(listOf("folder"), visited)
    }
    @Test fun stopPredicatePreventsMoreIo() = runBlocking {
        var reads = 0
        scanTreeBounded("root", 3, shouldStop = { reads >= 1 }) { reads++; listOf("child") }
        assertEquals(1, reads)
    }
}
