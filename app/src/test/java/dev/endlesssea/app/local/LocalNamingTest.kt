package dev.endlesssea.app.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * §tests (app / bibliothèque locale) — reconnaissance des épisodes dans les
 * noms de fichiers : c'est ce qui transforme un dossier en série ordonnée
 * (conv. 5) et ce qui rattache un téléchargement à sa fiche (conv. 7).
 * Un « S01E03 » mal interprété = épisodes dans le désordre ou doublons.
 */
class LocalNamingTest {

    @Test
    fun `le numero d'episode est reconnu dans les ecritures courantes`() {
        assertThat(LocalVideos.episodeNumber("Show.S02E10.1080p.mkv")).isEqualTo(10)
        assertThat(LocalVideos.episodeNumber("Titre - E03 - fin.mp4")).isEqualTo(3)
        assertThat(LocalVideos.episodeNumber("03 - Le début.mkv")).isEqualTo(3)
        assertThat(LocalVideos.episodeNumber("Le début 12.mp4")).isEqualTo(12)
        assertThat(LocalVideos.episodeNumber("Show S01E03 1080p VOSTFR.mkv")).isEqualTo(3)
    }

    @Test
    fun `un fichier sans numero ne renvoie rien`() {
        assertThat(LocalVideos.episodeNumber("film.mkv")).isNull()
        assertThat(LocalVideos.episodeNumber("bonus_interview.mp4")).isNull()
    }

    @Test
    fun `le titre de l'episode est debarrasse du numero`() {
        assertThat(LocalVideos.episodeTitleFromFileName("S01E03 Le début.mkv"))
            .isEqualTo("Le début")
        assertThat(LocalVideos.episodeTitleFromFileName("Show_S01E03_La_cle.mp4"))
            .isEqualTo("Show La cle")
        assertThat(LocalVideos.episodeTitleFromFileName("Show - 03 - Fin.mkv"))
            .isEqualTo("Show - 03 - Fin")
    }

    @Test
    fun `les durees sont affichees en heures et minutes`() {
        assertThat(LocalVideos.humanDuration(3_900_000)).isEqualTo("1h05")
        assertThat(LocalVideos.humanDuration(600_000)).isEqualTo("10 min")
        assertThat(LocalVideos.humanDuration(0)).isEqualTo("0 min")
        assertThat(LocalVideos.humanDuration(null)).isEmpty()
    }

    @Test
    fun `les tailles sont affichees en megaoctets puis en gigaoctets`() {
        assertThat(LocalVideos.humanSize(5L * 1024 * 1024)).isEqualTo("5 Mo")
        assertThat(LocalVideos.humanSize(500L * 1024 * 1024)).isEqualTo("500 Mo")
        assertThat(LocalVideos.humanSize(0)).isEqualTo("0 Mo")
    }
}
