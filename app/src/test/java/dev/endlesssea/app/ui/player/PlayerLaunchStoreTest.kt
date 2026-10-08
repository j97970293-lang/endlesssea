package dev.endlesssea.app.ui.player

import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class PlayerLaunchStoreTest {
    @After fun reset() {
        PlayerLaunchStore.setQueue(emptyList(), -1)
        PlayerLaunchStore.resolver = null
        PlayerLaunchStore.consume()
    }
    @Test fun alignsLaunchWithEpisodeInQueue() {
        PlayerLaunchStore.setQueue(listOf(
            PlayerLaunchStore.QueueItem("One", "e1", mediaId = "show"),
            PlayerLaunchStore.QueueItem("Two", "e2", mediaId = "show"),
        ), 0)
        PlayerLaunchStore.set("Two", "show", "e2", emptyList(), 0)
        assertEquals(1, PlayerLaunchStore.queueIndex)
        assertEquals(2, PlayerLaunchStore.queue.size)
    }
    @Test fun unrelatedLaunchClearsOldResolverAndQueue() {
        PlayerLaunchStore.setQueue(listOf(PlayerLaunchStore.QueueItem("Old", "e1", mediaId = "old")), 0)
        PlayerLaunchStore.resolver = { emptyList() }
        PlayerLaunchStore.set("New", "new", "e2", emptyList(), 0)
        assertEquals("e2", PlayerLaunchStore.queue.single().episodeId)
        assertNull(PlayerLaunchStore.resolver)
    }
    @Test fun invalidIndexCannotEnableNavigation() {
        PlayerLaunchStore.setQueue(emptyList(), 0)
        assertEquals(-1, PlayerLaunchStore.queueIndex)
    }
}
