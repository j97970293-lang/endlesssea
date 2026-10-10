package dev.endlesssea.downloader

import com.google.common.truth.Truth.assertThat
import dev.endlesssea.downloader.dash.DashError
import dev.endlesssea.downloader.dash.dashPlan
import dev.endlesssea.downloader.hls.hlsSubtitleTracks
import org.junit.Assert.assertThrows
import org.junit.Test

class DashAndSubtitleTest {
    @Test
    fun `progressive dash file is selected at the requested height`() {
        val plan = dashPlan(
            """
            <MPD type="static" mediaPresentationDuration="PT10M">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="360" bandwidth="400000" height="360" codecs="avc1.42E01E,mp4a.40.2">
                    <BaseURL>low.mp4</BaseURL>
                  </Representation>
                  <Representation id="720" bandwidth="2000000" height="720" codecs="avc1.64001F,mp4a.40.2">
                    <BaseURL>high.mp4</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
            """.trimIndent(),
            "https://cdn.example/video/master.mpd",
            requestedHeight = 720,
        )
        assertThat(plan.video.segments.single().url).isEqualTo("https://cdn.example/video/high.mp4")
        assertThat(plan.audio).isNull()
    }

    @Test
    fun `segment template keeps separate audio and rejects drm`() {
        val plan = dashPlan(
            """
            <MPD type="static" mediaPresentationDuration="PT8S">
              <Period>
                <AdaptationSet mimeType="video/mp4" contentType="video">
                  <ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"/>
                  <Representation id="drm" bandwidth="9" height="1080"><BaseURL>drm.mp4</BaseURL></Representation>
                </AdaptationSet>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1" duration="4" startNumber="1" media="v-${'$'}Number$.m4s" initialization="v-init.mp4"/>
                  <Representation id="v" bandwidth="1000" height="720" codecs="avc1.64001F"/>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4" lang="fr">
                  <SegmentTemplate timescale="1" duration="4" startNumber="1" media="a-${'$'}Number$.m4s" initialization="a-init.mp4"/>
                  <Representation id="a" bandwidth="128000" codecs="mp4a.40.2"/>
                </AdaptationSet>
              </Period>
            </MPD>
            """.trimIndent(),
            "https://cdn.example/stream/manifest.mpd",
        )
        assertThat(plan.video.segments.map { it.url }).containsExactly(
            "https://cdn.example/stream/v-1.m4s",
            "https://cdn.example/stream/v-2.m4s",
        ).inOrder()
        assertThat(plan.video.initUrl).isEqualTo("https://cdn.example/stream/v-init.mp4")
        assertThat(plan.audio!!.segments).hasSize(2)
    }

    @Test
    fun `live dash is refused`() {
        val error = assertThrows(DashError::class.java) {
            dashPlan("""<MPD type="dynamic"><Period></Period></MPD>""", "https://cdn.example/live.mpd")
        }
        assertThat(error).hasMessageThat().contains("direct")
    }

    @Test
    fun `hls master exposes subtitle playlists only`() {
        val tracks = hlsSubtitleTracks(
            """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",LANGUAGE="fr",NAME="Français",URI="subs/fr.m3u8"
            #EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID="cc",LANGUAGE="en",NAME="English"
            #EXT-X-STREAM-INF:BANDWIDTH=1000
            video.m3u8
            """.trimIndent(),
            "https://cdn.example/master.m3u8",
        )
        assertThat(tracks).hasSize(1)
        assertThat(tracks.single().url).isEqualTo("https://cdn.example/subs/fr.m3u8")
        assertThat(tracks.single().language).isEqualTo("fr")
    }
}
