package dev.endlesssea.core.subtitle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubtitleToolsTest {
    @Test
    fun `ass dialogue becomes readable srt without override tags`() {
        val ass = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.50,0:00:04.00,Default,,0,0,0,,Bonjour {\i1}monde{\i0}\NDeuxième
        """.trimIndent()
        val srt = AssDialogue.toSrt(ass)
        assertThat(srt).contains("00:00:01,500 --> 00:00:04,000")
        assertThat(srt).contains("Bonjour monde\nDeuxième")
        assertThat(srt).doesNotContain("\\i")
    }

    @Test
    fun `sidecar json round trip keeps local uri`() {
        val encoded = encodeSidecars(listOf(SidecarTrack("content://video/1", "fr", "Français", "VTT")))
        val decoded = decodeSidecars(encoded)
        assertThat(decoded).containsExactly(SidecarTrack("content://video/1", "fr", "Français", "VTT"))
    }

    @Test
    fun `sidecar name stays next to the video stem`() {
        assertThat(sidecarFileName("Film (1982).mp4", "fr", "vtt")).isEqualTo("Film (1982).fr.vtt")
        assertThat(sidecarFileName("Film.mp4", "audio", "m4a")).isEqualTo("Film.audio.m4a")
    }
}
