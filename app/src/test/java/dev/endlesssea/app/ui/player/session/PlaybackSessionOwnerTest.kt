package dev.endlesssea.app.ui.player.session

import org.junit.Assert.*
import org.junit.Test

class PlaybackSessionOwnerTest {
    @Test fun firstAttachmentStartsTheSuppliedRequest() {
        val owner = PlaybackSessionOwner<String> { it.isNotBlank() }
        assertEquals(PlaybackSessionOwner.Attachment.Start("episode-1"), owner.attach { "episode-1" })
    }
    @Test fun activityRecreationDoesNotConsumeAnEmptyLaunch() {
        val owner = PlaybackSessionOwner<String> { it.isNotBlank() }
        owner.attach { "episode-1" }
        assertEquals(PlaybackSessionOwner.Attachment.Retained, owner.attach { error("Must not consume twice") })
    }
    @Test fun unrelatedPendingLaunchIsNotConsumedByReattachingActivity() {
        val owner = PlaybackSessionOwner<String> { it.isNotBlank() }
        owner.attach { "episode-1" }
        var consumed = false
        owner.attach { consumed = true; "other-episode" }
        assertFalse(consumed)
    }
    @Test fun missingRequestAfterProcessDeathIsExplicit() {
        val owner = PlaybackSessionOwner<String> { it.isNotBlank() }
        assertEquals(PlaybackSessionOwner.Attachment.Unavailable, owner.attach { null })
    }
    @Test fun savedRecoveryStartsWhenThereIsNoNewLaunch() {
        val owner = PlaybackSessionOwner<String> { it.isNotBlank() }
        assertEquals(PlaybackSessionOwner.Attachment.Start("restored"), owner.attach(
            consume = { null }, restore = { "restored" },
        ))
    }
    @Test fun aNewLaunchTakesPrecedenceOverSavedRecovery() {
        val owner = PlaybackSessionOwner<String> { it.isNotBlank() }
        var restored = false
        assertEquals(PlaybackSessionOwner.Attachment.Start("new"), owner.attach(
            consume = { "new" }, restore = { restored = true; "old" },
        ))
        assertFalse(restored)
    }
    @Test fun invalidLaunchCanFallBackToValidSavedRecovery() {
        val owner = PlaybackSessionOwner<String> { it.isNotBlank() }
        assertEquals(PlaybackSessionOwner.Attachment.Start("restored"), owner.attach(
            consume = { "" }, restore = { "restored" },
        ))
    }
    @Test fun reattachingDoesNotReadRecoveryAgain() {
        val owner = PlaybackSessionOwner<String> { it.isNotBlank() }
        owner.attach(consume = { null }, restore = { "restored" })
        var restored = false
        assertEquals(PlaybackSessionOwner.Attachment.Retained, owner.attach(
            consume = { error("Must not consume again") }, restore = { restored = true; "again" },
        ))
        assertFalse(restored)
    }
    @Test fun emptyLinkEquivalentCannotStartPlayback() {
        assertEquals(PlaybackSessionOwner.Attachment.Unavailable,
            PlaybackSessionOwner<String> { it.isNotBlank() }.attach { "" })
    }
    @Test fun invalidRequestDoesNotPreventLaterValidRequest() {
        val owner = PlaybackSessionOwner<String> { it.isNotBlank() }
        owner.attach { "" }
        assertEquals(PlaybackSessionOwner.Attachment.Start("valid"), owner.attach { "valid" })
    }
    @Test fun separateViewModelsOwnSeparateSessions() {
        val first = PlaybackSessionOwner<String> { true }
        val second = PlaybackSessionOwner<String> { true }
        first.attach { "first" }
        assertEquals(PlaybackSessionOwner.Attachment.Start("second"), second.attach { "second" })
    }
    @Test fun repeatedRecreationKeepsTheSameOwner() {
        val owner = PlaybackSessionOwner<String> { true }
        var count = 0
        repeat(20) { owner.attach { count++; "episode" } }
        assertEquals(1, count)
    }
}
