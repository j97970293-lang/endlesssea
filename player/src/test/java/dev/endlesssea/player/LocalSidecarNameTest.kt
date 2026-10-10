package dev.endlesssea.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalSidecarNameTest {
    @Test
    fun `audio and subtitle files beside a video are recognized`() {
        assertThat(sidecarRole("Film.mp4", "Film.audio.m4a")).isEqualTo("audio")
        assertThat(sidecarRole("Film.mp4", "Film.fr.vtt")).isEqualTo("subtitle")
        assertThat(sidecarRole("Film.mp4", "Other.fr.vtt")).isNull()
    }
}
