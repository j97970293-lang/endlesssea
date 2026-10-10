package dev.endlesssea.app.tracking

import com.google.common.truth.Truth.assertThat
import dev.endlesssea.extensions.api.model.Episode
import org.junit.Test

class EpisodeCatalogTest {
    @Test
    fun `missing title and still are filled without replacing existing values`() {
        val episodes = listOf(
            Episode("1", 1f, title = "Déjà là", thumbnailUrl = null, data = "x"),
            Episode("2", 2f, title = null, thumbnailUrl = null, data = "y"),
        )
        val merged = mergeCatalog(
            episodes,
            listOf(CatalogEpisode(1, "Autre", "https://img/1.jpg"), CatalogEpisode(2, "Suite", "https://img/2.jpg")),
        )
        assertThat(merged[0].title).isEqualTo("Déjà là")
        assertThat(merged[0].thumbnailUrl).isEqualTo("https://img/1.jpg")
        assertThat(merged[1].title).isEqualTo("Suite")
    }

    @Test
    fun `anizip episode object is parsed`() {
        val parsed = parseAniZipEpisodes(
            """{"episodes":{"1":{"episode":"1","title":{"fr":"Début"},"image":"https://img/1.jpg"}}}""",
        )
        assertThat(parsed).containsExactly(CatalogEpisode(1, "Début", "https://img/1.jpg"))
    }
}
