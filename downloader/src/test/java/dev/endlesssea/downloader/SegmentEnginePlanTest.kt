package dev.endlesssea.downloader

import com.google.common.truth.Truth.assertThat
import dev.endlesssea.downloader.segment.SegmentEngine
import okhttp3.OkHttpClient
import org.junit.Test

/**
 * §tests (downloader) — plan de segmentation : c'est lui qui décide combien de
 * connexions ouvrir et quelles plages demander. Une erreur ici se paie en
 * téléchargements corrompus (trou de couverture) ou en plantage de la file
 * (réglage utilisateur « 1 segment », cf. test dédié).
 */
class SegmentEnginePlanTest {

    private val engine = SegmentEngine(OkHttpClient())
    private val mib = 1024L * 1024L
    private val gio = 1024L * mib

    @Test
    fun `une taille inconnue ou nulle ne produit aucun segment`() {
        assertThat(engine.plan(0, acceptRanges = true)).isEmpty()
        assertThat(engine.plan(-1, acceptRanges = true)).isEmpty()
    }

    @Test
    fun `sans Range on telecharge le fichier d'un seul bloc`() {
        val segments = engine.plan(100 * mib, acceptRanges = false)
        assertThat(segments).hasSize(1)
        assertThat(segments[0].start).isEqualTo(0)
        assertThat(segments[0].end).isEqualTo(100 * mib - 1)
    }

    @Test
    fun `un petit fichier reste sur une seule connexion`() {
        assertThat(engine.plan(7, acceptRanges = true)).hasSize(1)
        assertThat(engine.plan(SegmentEngine.MIN_SPLIT_BYTES - 1, acceptRanges = true)).hasSize(1)
    }

    @Test
    fun `au seuil de decoupe on ouvre deux connexions`() {
        assertThat(engine.plan(SegmentEngine.MIN_SPLIT_BYTES, acceptRanges = true)).hasSize(2)
    }

    @Test
    fun `un gros fichier est plafonne au nombre de segments par defaut`() {
        val segments = engine.plan(gio, acceptRanges = true)
        assertThat(segments).hasSize(SegmentEngine.DEFAULT_MAX_PARTS)
        assertThat(segments.first().start).isEqualTo(0)
        assertThat(segments.last().end).isEqualTo(gio - 1)
        assertThat(segments.sumOf { it.total }).isEqualTo(gio)
    }

    @Test
    fun `maxParts est respecte et les segments restent jointifs`() {
        val size = 30 * mib
        val segments = engine.plan(size, acceptRanges = true, maxParts = 3)
        assertThat(segments).hasSize(3)
        assertThat(segments.map { it.idx }).containsExactly(0, 1, 2).inOrder()
        for (i in 1 until segments.size) {
            assertThat(segments[i].start).isEqualTo(segments[i - 1].end + 1)
        }
        assertThat(segments.sumOf { it.total }).isEqualTo(size)
    }

    @Test
    fun `un reglage utilisateur a un seul segment ne doit pas planter`() {
        // §debit : les Réglages proposent 1 segment par fichier ; sans cette
        // branche, `coerceIn(2, 1)` levait une IllegalArgumentException.
        val segments = engine.plan(100 * mib, acceptRanges = true, maxParts = 1)
        assertThat(segments).hasSize(1)
        assertThat(segments[0].end).isEqualTo(100 * mib - 1)
    }

    @Test
    fun `une taille non divisible reste integralement couverte`() {
        val size = 25 * mib + 12345
        val segments = engine.plan(size, acceptRanges = true, maxParts = 2)
        assertThat(segments).hasSize(2)
        assertThat(segments.last().end).isEqualTo(size - 1)
        assertThat(segments.sumOf { it.total }).isEqualTo(size)
    }

    @Test
    fun `une valeur negative de maxParts retombe sur la valeur par defaut`() {
        assertThat(engine.plan(gio, acceptRanges = true, maxParts = -4))
            .hasSize(SegmentEngine.DEFAULT_MAX_PARTS)
    }

    @Test
    fun `le suivi d'avancement d'un segment est exact`() {
        val segment = SegmentEngine.Segment(idx = 0, start = 0, end = 99)
        assertThat(segment.total).isEqualTo(100)
        assertThat(segment.done).isFalse()
        assertThat(segment.remaining).isEqualTo(100)
        segment.downloaded = 40
        assertThat(segment.done).isFalse()
        assertThat(segment.remaining).isEqualTo(60)
        segment.downloaded = 100
        assertThat(segment.done).isTrue()
        assertThat(segment.remaining).isEqualTo(0)
    }
}
