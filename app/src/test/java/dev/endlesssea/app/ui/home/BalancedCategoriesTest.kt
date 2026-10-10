package dev.endlesssea.app.ui.home

import dev.endlesssea.extensions.api.model.HomeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalancedCategoriesTest {

    private fun category(key: String, title: String = key) = HomeCategory(key, title)

    @Test
    fun filmFirstCatalogStillShowsSeriesWithinFirstTwoRows() {
        val sourceOrder = listOf(
            category("movie-popular", "Films populaires"),
            category("movie-top", "Films les mieux notés"),
            category("movie-new", "Nouveaux films"),
            category("tv-popular", "Séries populaires"),
            category("tv-top", "Séries les mieux notées"),
        )

        val selected = balancedCategories(sourceOrder, 2)

        assertEquals(2, selected.size)
        assertEquals("movie-popular", selected[0].key)
        assertEquals("tv-popular", selected[1].key)
    }

    @Test
    fun roundRobinIncludesAnimeAndLiveBeforeRepeatingFamilies() {
        val selected = balancedCategories(
            listOf(
                category("films-1"), category("films-2"),
                category("series_1"), category("series_2"),
                category("anime/latest"), category("live-tv"),
            ),
            4,
        )

        assertEquals(listOf("films-1", "series_1", "anime/latest", "live-tv"), selected.map { it.key })
    }

    @Test
    fun duplicateAndBlankKeysAreDiscardedWithoutExceedingLimit() {
        val selected = balancedCategories(
            listOf(
                category(""), category("films"), category("films", "Duplicate"), category("series"),
            ),
            10,
        )
        assertEquals(listOf("films", "series"), selected.map { it.key })
        assertTrue(balancedCategories(selected, 0).isEmpty())
    }
}
