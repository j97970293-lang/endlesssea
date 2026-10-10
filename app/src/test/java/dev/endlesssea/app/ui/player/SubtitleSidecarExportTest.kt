package dev.endlesssea.app.ui.player

import dev.endlesssea.extensions.api.model.SubtitleFormat
import dev.endlesssea.extensions.api.model.SubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSidecarExportTest {
    @Test
    fun exportNameIsSanitizedAndKeepsTheSubtitleFormat() {
        val track = SubtitleTrack(
            url = "https://example.test/subtitle",
            lang = "fr",
            label = "Français / VF",
            format = SubtitleFormat.SRT,
        )
        assertEquals("Film _ Episode - Français _ VF.srt", subtitleExportFileName("Film : Episode", track))
    }

    @Test
    fun unknownFormatCanBeInferredFromTheUrl() {
        val track = SubtitleTrack(
            url = "https://example.test/captions.vtt",
            lang = "en",
            label = "English",
            format = SubtitleFormat.UNKNOWN,
        )
        assertEquals("Film - English.vtt", subtitleExportFileName("Film", track))
    }
}
