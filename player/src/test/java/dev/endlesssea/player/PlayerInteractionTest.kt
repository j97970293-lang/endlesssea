package dev.endlesssea.player

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test

class PlayerInteractionTest {
    @Test fun everyTapAfterInitialPairSeeksWithoutPairingAgain() {
        val taps = SeekTapSequence(300)
        assertFalse(taps.tap(0, 1))
        repeat(5) { assertTrue(taps.tap((it + 1) * 100L, 1)) }
    }
    @Test fun burstCanReverseDirection() {
        val taps = SeekTapSequence(300)
        taps.tap(0, 1)
        assertTrue(taps.tap(100, 1))
        assertTrue(taps.tap(200, -1))
    }
    @Test fun isolatedTapDoesNotSeek() {
        val taps = SeekTapSequence(300)
        assertFalse(taps.tap(0, 1))
        assertFalse(taps.tap(1000, 1))
    }
    @Test fun oppositeInitialTapsAreNotADoubleTap() {
        val taps = SeekTapSequence(300)
        taps.tap(0, 1)
        assertFalse(taps.tap(100, -1))
    }
    @Test fun longPressOrCancellationResetsSequence() {
        val taps = SeekTapSequence(300)
        taps.tap(0, 1); taps.reset()
        assertFalse(taps.tap(100, 1))
    }
    @Test fun burstExpires() {
        val taps = SeekTapSequence(300)
        taps.tap(0, 1); taps.tap(100, 1)
        assertFalse(taps.tap(1100, 1))
    }
    @Test fun fiveButtonClicksAccumulateDespiteStalePlaybackPosition() {
        val pending = RelativeSeekTarget()
        repeat(5) { pending.add(100_000, 10_000, 500_000) }
        assertEquals(150_000L, pending.take())
        assertNull(pending.take())
    }
    @Test fun mixedClicksUsePendingTargetAndRespectBounds() {
        val pending = RelativeSeekTarget()
        pending.add(100_000, 10_000, 120_000)
        assertEquals(120_000L, pending.add(100_000, 30_000, 120_000))
        assertEquals(110_000L, pending.add(100_000, -10_000, 120_000))
        assertEquals(0L, pending.add(100_000, -500_000, 120_000))
    }
    @Test fun unknownDurationDoesNotPreventSeek() {
        assertEquals(50_000L, RelativeSeekTarget().add(40_000, 10_000, -1))
    }
    @Test fun absoluteSeekOrEpisodeChangeClearsQueuedTarget() {
        val pending = RelativeSeekTarget()
        pending.add(100_000, 10_000, 500_000); pending.clear()
        assertEquals(15_000L, pending.add(5_000, 10_000, 500_000))
    }
    @Test fun bufferingStillDisplaysPauseAndPausesOnClick() {
        assertTrue(playbackButtonShowsPause(true, Player.STATE_BUFFERING, false))
        assertFalse(playbackButtonShowsPause(false, Player.STATE_BUFFERING, false))
    }
    @Test fun errorEndedOrIdleOfferPlayNotPause() {
        assertFalse(playbackButtonShowsPause(true, Player.STATE_READY, true))
        assertFalse(playbackButtonShowsPause(true, Player.STATE_ENDED, false))
        assertFalse(playbackButtonShowsPause(true, Player.STATE_IDLE, false))
    }
}
