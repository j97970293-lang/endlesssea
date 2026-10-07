package dev.endlesssea.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * §tests (core) — noms de fichiers et gabarits de renommage : c'est la partie
 * qui touche le disque de l'utilisateur, donc celle qu'on ne veut jamais voir
 * produire un nom invalide (FAT/SD) ou un doublon de recherche mal normalisé.
 */
class FileNamesTest {

    // ------------------------------------------------------------- sanitize

    @Test
    fun `sanitize supprime tous les caracteres interdits`() {
        val out = FileNames.sanitize("a\\b/c:d*e?f\"g<h>i|j")
        assertThat(out).isEqualTo("abcdefghij")
    }

    @Test
    fun `sanitize reduit les espaces et rogne les points finaux`() {
        assertThat(FileNames.sanitize("  Le   Début  ")).isEqualTo("Le Début")
        assertThat(FileNames.sanitize("fin..")).isEqualTo("fin")
    }

    @Test
    fun `sanitize remplace un titre vide par untitled`() {
        assertThat(FileNames.sanitize("")).isEqualTo("untitled")
        assertThat(FileNames.sanitize("   ")).isEqualTo("untitled")
        assertThat(FileNames.sanitize("///")).isEqualTo("untitled")
    }

    @Test
    fun `sanitize tronque proprement en respectant la longueur maximale`() {
        val out = FileNames.sanitize("x".repeat(120), maxLength = 10)
        assertThat(out).hasLength(10)
        assertThat(out).isEqualTo("xxxxxxxxx…")
    }

    @Test
    fun `sanitize conserve les accents et la longueur sous la limite`() {
        val out = FileNames.sanitize("Épisode 12 — La clé de l'été")
        assertThat(out).isEqualTo("Épisode 12 — La clé de l'été")
    }

    // --------------------------------------------------------- normalizedKey

    @Test
    fun `normalizedKey replie les accents et la casse`() {
        assertThat(FileNames.normalizedKey("  ÉPÉE  De  FER "))
            .isEqualTo("epee de fer")
    }

    @Test
    fun `normalizedKey rend comparables deux ecritures du meme titre`() {
        assertThat(FileNames.normalizedKey("Kimi no Na wa"))
            .isEqualTo(FileNames.normalizedKey("KIMI   NO   NA WA"))
    }

    // ---------------------------------------------------------------- render

    @Test
    fun `render applique le gabarit par defaut avec les numeros sur deux chiffres`() {
        val out = FileNames.render(
            FileNames.DEFAULT_TEMPLATE,
            mapOf(
                "title" to "Ma Série", "season" to "1", "episode" to "7",
                "quality" to "1080p", "lang" to "VOSTFR", "ext" to "mkv",
            ),
        )
        assertThat(out).isEqualTo("Ma Série - S01E07 [1080p][VOSTFR].mkv")
    }

    @Test
    fun `render respecte une largeur de remplissage de trois chiffres`() {
        assertThat(FileNames.render("E{episode:000}", mapOf("episode" to "7")))
            .isEqualTo("E007")
    }

    @Test
    fun `render sans remplissage n'ajoute aucun zero`() {
        assertThat(FileNames.render("E{episode}", mapOf("episode" to "7")))
            .isEqualTo("E7")
    }

    @Test
    fun `render ne remplit pas une valeur non numerique`() {
        assertThat(FileNames.render("S{season:00}", mapOf("season" to "special")))
            .isEqualTo("Sspecial")
    }

    @Test
    fun `render laisse intacts les jetons inconnus`() {
        assertThat(FileNames.render("{title} {bogus}", mapOf("title" to "A")))
            .isEqualTo("A {bogus}")
    }

    @Test
    fun `render applique le gabarit film`() {
        val out = FileNames.render(
            FileNames.MOVIE_TEMPLATE,
            mapOf("title" to "Le Film", "year" to "2024", "ext" to "mp4"),
        )
        assertThat(out).isEqualTo("Le Film (2024).mp4")
    }
}
