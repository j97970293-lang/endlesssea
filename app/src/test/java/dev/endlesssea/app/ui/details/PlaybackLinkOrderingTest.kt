package dev.endlesssea.app.ui.details

import dev.endlesssea.app.di.MediaPlaybackPreference
import dev.endlesssea.extensions.api.model.AudioLang
import dev.endlesssea.extensions.api.model.Quality
import dev.endlesssea.extensions.api.model.StreamType
import dev.endlesssea.extensions.api.model.VideoLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackLinkOrderingTest {
    private fun link(server: String, language: AudioLang, quality: Quality) = VideoLink(
        url = "https://example.test/$server/${language.iso}/${quality.pixels}",
        server = server,
        audioLang = language,
        quality = quality,
        streamType = StreamType.DIRECT_FILE,
    )

    private fun order(links: List<VideoLink>, saved: MediaPlaybackPreference) = orderPlaybackLinks(
        links = links,
        preferredAudioLanguage = "vf",
        serverPriority = listOf("Global", "Saved"),
        preferredQuality = "1080",
        rememberedPreference = saved,
    )

    @Test fun availableSavedServerLanguageAndQualityOverrideGlobalPreferences() {
        val global = link("Global", AudioLang.VF, Quality.Q1080)
        val savedExact = link("Saved", AudioLang.VOSTFR, Quality.Q720)
        val savedOtherQuality = link("Saved", AudioLang.VOSTFR, Quality.Q1080)
        val saved = MediaPlaybackPreference("Saved", 720, "vostfr")

        assertEquals(
            savedExact,
            order(listOf(global, savedOtherQuality, savedExact), saved).first(),
        )
    }

    @Test fun unavailableSavedCombinationFallsBackToAllGlobalPreferences() {
        val savedServerButWrongQuality = link("Saved", AudioLang.VF, Quality.Q1080)
        val globalLanguageAndServer = link("Global", AudioLang.VF, Quality.Q1080)
        val saved = MediaPlaybackPreference("Saved", 720, "vf")

        assertEquals(
            globalLanguageAndServer,
            order(listOf(savedServerButWrongQuality, globalLanguageAndServer), saved).first(),
        )
        assertNull(availableMediaPlaybackPreference(listOf(savedServerButWrongQuality), saved))
    }

    @Test fun qualityCardUsesRememberedResolutionOnlyWhenTheFullChoiceIsAvailable() {
        val q720 = link("Saved", AudioLang.VF, Quality.Q720)
        val q1080 = link("Saved", AudioLang.VF, Quality.Q1080)
        val saved = MediaPlaybackPreference("Saved", 720, "vf")

        assertEquals(q720, preferredPlaybackLink(listOf(q1080, q720), "1080", saved))
        assertEquals(q1080, preferredPlaybackLink(listOf(q1080), "1080", saved))
    }
}
