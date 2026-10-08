package dev.endlesssea.app.ui.explore

import dev.endlesssea.app.ui.search.SearchItemUi
import dev.endlesssea.extensions.api.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class ExploreFiltersTest {
    private val movie = SearchItemUi("movie", "Film", genres = listOf("Action"), year = 2025, type = MediaType.MOVIE)
    private val series = SearchItemUi("series", "Série", genres = listOf("Drame"), year = 2024, type = MediaType.SERIES)
    private val unknown = SearchItemUi("unknown", "Unknown")
    private val rows = listOf(ExploreRowUi("Catalogue", listOf(movie, series, unknown), "source"))

    @Test fun noFiltersPreserveItems() {
        assertEquals(rows, filterExploreRows(rows, null, null, null))
    }
    @Test fun combinesFiltersAndIgnoresGenreCase() {
        assertEquals(listOf(movie), filterExploreRows(rows, "action", 2025, "Film").single().items)
    }
    @Test fun translatesFrenchSeriesLabel() {
        assertEquals(listOf(series), filterExploreRows(rows, null, null, "Série").single().items)
    }
    @Test fun removesEmptyRowsAndDoesNotMatchUnknownMetadata() {
        assertTrue(filterExploreRows(rows, "Action", 2024, null).isEmpty())
        assertTrue(filterExploreRows(rows, null, null, "OVA").isEmpty())
        assertTrue(filterExploreRows(rows, null, 2026, null).isEmpty())
    }
}
