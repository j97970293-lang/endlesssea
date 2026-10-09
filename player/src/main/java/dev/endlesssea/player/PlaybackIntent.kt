package dev.endlesssea.player

import androidx.media3.common.Player

/** A buffering player still has a Pause action; an ended/failed/idle player has Play/Retry. */
fun playbackButtonShowsPause(playWhenReady: Boolean, playbackState: Int, hasError: Boolean): Boolean =
    playWhenReady && !hasError && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE
