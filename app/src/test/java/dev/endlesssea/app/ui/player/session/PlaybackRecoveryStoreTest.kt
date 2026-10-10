package dev.endlesssea.app.ui.player.session

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRecoveryStoreTest {
    @Test fun storesOnlyStableIdentifiersTitleAndPosition() {
        val handle = SavedStateHandle()
        val store = PlaybackRecoveryStore(handle)
        val state = PlaybackRecoveryState("source:show", "source:show:S1:E2", "Show S1:E2", 12_345L)

        store.save(state)

        assertEquals(state, store.read())
        assertEquals(
            setOf("player.recovery.media_id", "player.recovery.episode_id", "player.recovery.title", "player.recovery.position_ms"),
            handle.keys(),
        )
    }

    @Test fun updatesPositionOnlyForTheActiveEpisode() {
        val store = PlaybackRecoveryStore(SavedStateHandle())
        store.save(PlaybackRecoveryState("source:show", "episode-1", "Show E1", 100L))

        store.updatePosition("source:show", "episode-2", 500L)
        assertEquals(100L, store.read()?.positionMs)
        store.updatePosition("source:show", "episode-1", 700L)
        assertEquals(700L, store.read()?.positionMs)
    }

    @Test fun invalidOrMissingIdentityCannotBeRestored() {
        val handle = SavedStateHandle()
        val store = PlaybackRecoveryStore(handle)
        store.save(PlaybackRecoveryState("", "episode-1", "Title", 0L))
        assertNull(store.read())
        assertFalse(handle.keys().any { it.startsWith("player.recovery.") })
    }

    @Test fun clearRemovesAllRecoveryKeys() {
        val handle = SavedStateHandle()
        val store = PlaybackRecoveryStore(handle)
        store.save(PlaybackRecoveryState("source:show", "episode-1", "Title", 0L))
        store.clear()
        assertNull(store.read())
        assertFalse(handle.keys().any { it.startsWith("player.recovery.") })
    }
}
