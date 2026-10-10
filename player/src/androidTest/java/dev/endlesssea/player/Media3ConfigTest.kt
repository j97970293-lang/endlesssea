package dev.endlesssea.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Media3 starts under the short-buffer and the larger-buffer profiles. No network stream is opened. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Media3ConfigTest {
    @Test
    fun shortAndNormalBuffersBothBuild() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(15_000 to 30_000, 30_000 to 60_000).forEach { (min, max) ->
            val player = ExoPlayer.Builder(context)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(min, max, 1_500, 3_000)
                        .build(),
                )
                .build()
            assertTrue(player.playbackState >= 0)
            player.release()
        }
    }
}
