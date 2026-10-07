package dev.endlesssea.app.skip

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * §tests (app / Megaskip) — identité d'un média et segments sautables : ces
 * fonctions décident de la requête envoyée à TheIntroDB/AniSkip (saison,
 * épisode, clé de cache) et du libellé du bouton dans le lecteur.
 */
class SkipModelsTest {

    // ------------------------------------------------------------- SkipTarget

    @Test
    fun `saison et episode sont extraits des ecritures courantes`() {
        assertThat(SkipTarget.parseSeasonEpisode("Show.S01E03.1080p.mkv")).isEqualTo(1 to 3)
        assertThat(SkipTarget.parseSeasonEpisode("Titre 2x07 VOSTFR")).isEqualTo(2 to 7)
        assertThat(SkipTarget.parseSeasonEpisode("Show - EP12.mkv")).isEqualTo(1 to 12)
        assertThat(SkipTarget.parseSeasonEpisode("E07")).isEqualTo(1 to 7)
    }

    @Test
    fun `un nom sans numero retourne la paire zero`() {
        assertThat(SkipTarget.parseSeasonEpisode("Le film.mkv")).isEqualTo(0 to 0)
        assertThat(SkipTarget.parseSeasonEpisode("")).isEqualTo(0 to 0)
        assertThat(SkipTarget.parseSeasonEpisode(null)).isEqualTo(0 to 0)
    }

    @Test
    fun `la saison explicite gagne sur le repli par episode seul`() {
        assertThat(SkipTarget.parseSeasonEpisode("S02E05 special")).isEqualTo(2 to 5)
    }

    @Test
    fun `la normalisation de titre rend deux ecritures comparables`() {
        assertThat(SkipTarget.normalize("Kimi   no  Na wa!")).isEqualTo("kimi no na wa")
        assertThat(SkipTarget.normalize("L'ÉTÉ Bleu!!")).isEqualTo("l été bleu")
        assertThat(SkipTarget.normalize("  ")).isEmpty()
    }

    @Test
    fun `la cle de media prefere l'identifiant au titre`() {
        assertThat(SkipTarget(mediaId = "tt1234567", title = "Peu importe").mediaKey)
            .isEqualTo("tt1234567")
        assertThat(SkipTarget(mediaId = "   ", title = "Kimi no Na wa").mediaKey)
            .isEqualTo("title:kimi no na wa")
        assertThat(SkipTarget().mediaKey).isEqualTo("unknown")
    }

    @Test
    fun `la cle de cache du fournisseur encode saison et episode`() {
        val target = SkipTarget(mediaId = "42", season = 1, episode = 3)
        assertThat(target.cacheKey("aniskip")).isEqualTo("aniskip|42|S1E3")
        assertThat(target.cacheKey("theintrodb")).isEqualTo("theintrodb|42|S1E3")
    }

    @Test
    fun `un media sans aucun identifiant est reconnu comme anonyme`() {
        assertThat(SkipTarget().isAnonymous).isTrue()
        assertThat(SkipTarget(title = "   ").isAnonymous).isTrue()
        assertThat(SkipTarget(title = "Un titre").isAnonymous).isFalse()
        assertThat(SkipTarget(tmdbId = "1399").isAnonymous).isFalse()
        assertThat(SkipTarget(malId = "1").isAnonymous).isFalse()
    }

    // ------------------------------------------------------------ SkipSegment

    @Test
    fun `la duree d'un segment et son libelle sont humains`() {
        val intro = SkipSegment(SkipType.INTRO, startMs = 10_000, endMs = 100_000)
        assertThat(intro.durationMs).isEqualTo(90_000)
        assertThat(intro.humanDuration).isEqualTo("1 min 30")

        val credits = SkipSegment(SkipType.CREDITS, startMs = 0, endMs = 15_000)
        assertThat(credits.humanDuration).isEqualTo("15 s")
    }

    @Test
    fun `un segment inverse ne produit jamais de duree negative`() {
        val broken = SkipSegment(SkipType.INTRO, startMs = 5_000, endMs = 1_000)
        assertThat(broken.durationMs).isEqualTo(0)
        assertThat(broken.humanDuration).isEqualTo("0 s")
    }

    @Test
    fun `la position est dans le segment jusqu'a la borne haute exclue`() {
        val segment = SkipSegment(SkipType.INTRO, startMs = 10_000, endMs = 100_000)
        assertThat(segment.contains(9_999)).isFalse()
        assertThat(segment.contains(10_000)).isTrue()
        assertThat(segment.contains(99_999)).isTrue()
        assertThat(segment.contains(100_000)).isFalse()
    }

    // ------------------------------------------------------------- réglages

    @Test
    fun `le saut automatique suit les reglages par defaut`() {
        val defaults = SkipSettings()
        assertThat(defaults.autoFor(SkipType.INTRO)).isTrue()
        assertThat(defaults.autoFor(SkipType.RECAP)).isFalse()
        assertThat(defaults.autoFor(SkipType.CREDITS)).isTrue()
        assertThat(defaults.autoFor(SkipType.PREVIEW)).isFalse()
        assertThat(defaults.autoFor(SkipType.CUSTOM)).isFalse()
        assertThat(defaults.countdownSec).isEqualTo(3)
    }

    @Test
    fun `les reglages personnalises sont respectes type par type`() {
        val custom = SkipSettings(autoIntro = false, autoRecap = true, autoCredits = false)
        assertThat(custom.autoFor(SkipType.INTRO)).isFalse()
        assertThat(custom.autoFor(SkipType.RECAP)).isTrue()
        assertThat(custom.autoFor(SkipType.CREDITS)).isFalse()
    }

    @Test
    fun `chaque type de segment a son libelle de bouton`() {
        assertThat(SkipType.INTRO.actionLabel).isEqualTo("Passer l'intro")
        assertThat(SkipType.RECAP.actionLabel).isEqualTo("Passer le récap")
        assertThat(SkipType.CREDITS.actionLabel).isEqualTo("Passer le générique")
        assertThat(SkipType.PREVIEW.actionLabel).isEqualTo("Passer l'aperçu")
        assertThat(SkipType.CUSTOM.actionLabel).isEqualTo("Passer")
    }

    @Test
    fun `les boutons de saut personnalises affichent des libelles lisibles`() {
        assertThat(CustomSkipButton(label = "Générique", seconds = 120).human).isEqualTo("+2 min")
        assertThat(CustomSkipButton(label = "Générique", seconds = 90).human).isEqualTo("+1min30")
        assertThat(CustomSkipButton(label = "Générique", seconds = 95).human).isEqualTo("+1min35")
        assertThat(CustomSkipButton(label = "Intro", seconds = 30).human).isEqualTo("+30 s")
    }
}
