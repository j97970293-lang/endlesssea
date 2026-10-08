package dev.endlesssea.app.local

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A parent must release its I/O permit BEFORE waiting for its children. */
internal suspend fun scanTreeBounded(
    root: String,
    maxDepth: Int,
    parallelism: Int = 8,
    shouldStop: () -> Boolean = { false },
    readDirectory: suspend (String) -> List<String>,
) = coroutineScope {
    require(maxDepth >= 0 && parallelism > 0)
    val gate = Semaphore(parallelism)
    suspend fun walk(id: String, depth: Int): Unit {
        if (depth > maxDepth || shouldStop()) return
        val children = gate.withPermit {
            if (shouldStop()) emptyList() else readDirectory(id)
        }
        if (depth < maxDepth) coroutineScope {
            children.map { async { walk(it, depth + 1) } }.awaitAll()
        }
    }
    walk(root, 0)
}
