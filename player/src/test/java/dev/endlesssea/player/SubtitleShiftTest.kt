package dev.endlesssea.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * §sous-titres (conversation 1) — le décalage réécrit les horodatages des
 * fichiers SRT/VTT. Ces tests couvrent les deux sens, les deux formats et les
 * bornes (début de fichier).
 */
class SubtitleShiftTest {

    private val srt = """
        1
        00:00:01,000 --> 00:00:03,500
        Bonjour

        2
        00:00:10,250 --> 00:00:12,000
        Deuxième ligne
    """.trimIndent()

    private val vtt = """
        WEBVTT

        00:00:01.000 --> 00:00:03.500
        Bonjour

        00:01:02.000 --> 00:01:04.500
        Suite
    """.trimIndent()

    @Test
    fun `srt retarde de 500 ms en gardant la virgule`() {
        val out = SubtitleShift.shift(srt, 500L, vtt = false)
        assertThat(out).contains("00:00:01,500 --> 00:00:04,000")
        assertThat(out).contains("00:00:10,750 --> 00:00:12,500")
    }

    @Test
    fun `srt avance d'une seconde et demie`() {
        val out = SubtitleShift.shift(srt, -1_500L, vtt = false)
        assertThat(out).contains("00:00:00,000 --> 00:00:02,000")
        assertThat(out).contains("00:00:08,750 --> 00:00:10,500")
    }

    @Test
    fun `vtt conserve le point et les heures au-dela d'une heure`() {
        val out = SubtitleShift.shift(vtt, 30_000L, vtt = true)
        assertThat(out).contains("00:00:31.000 --> 00:00:33.500")
        assertThat(out).contains("00:01:32.000 --> 00:01:34.500")
    }

    @Test
    fun `un decalage negatif est borne a zero, jamais d'horaire negative`() {
        val out = SubtitleShift.shift(srt, -60_000L, vtt = false)
        // Les deux cues reculent au-delà du début : elles sont ramenées à zéro
        // (et non à des horodatages négatifs comme « -00:00:59,000 »).
        assertThat(out).doesNotContain(":-")
        assertThat(out).isEqualTo(
            """
            1
            00:00:00,000 --> 00:00:00,000
            Bonjour

            2
            00:00:00,000 --> 00:00:00,000
            Deuxième ligne
            """.trimIndent(),
        )
    }

    @Test
    fun `les lignes de texte hors horodatage restent intactes`() {
        val out = SubtitleShift.shift(srt, 1_000L, vtt = false)
        assertThat(out).contains("Bonjour")
        assertThat(out).contains("Deuxième ligne")
        assertThat(out).contains("1\n")
    }

    @Test
    fun `un decalage nul redonne exactement le fichier`() {
        assertThat(SubtitleShift.shift(srt, 0L, vtt = false)).isEqualTo(srt)
    }
}
