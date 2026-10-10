package dev.endlesssea.app.ui.details

import dev.endlesssea.extensions.api.model.AudioLang
import dev.endlesssea.extensions.api.model.StreamType
import dev.endlesssea.extensions.api.model.VideoLink
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackServerGroupsTest {
    private fun link(server: String, lang: AudioLang) = VideoLink(
        url = "https://example.test/$server",
        streamType = StreamType.DIRECT_FILE,
        server = server,
        audioLang = lang,
    )

    @Test fun vostfrAndFrenchAudioAreFirstRegardlessOfArrivalOrder() {
        val vf = link("VF", AudioLang.VF)
        val other = link("Other", AudioLang.OTHER)
        val vostfr = link("VOSTFR", AudioLang.VOSTFR)
        val multi = link("Multi", AudioLang.MULTI)

        val groups = playbackLanguageGroups(listOf(vf, other, vostfr, multi))

        assertEquals(
            listOf(AudioLang.VOSTFR, AudioLang.VF, AudioLang.MULTI, AudioLang.OTHER),
            groups.map { it.first },
        )
        assertEquals(listOf(vostfr), groups[0].second)
        assertEquals(listOf(vf), groups[1].second)
    }

    @Test fun languagesWithoutLinksAreOmitted() {
        assertEquals(emptyList<Pair<AudioLang, List<VideoLink>>>(), playbackLanguageGroups(emptyList()))
    }
}
