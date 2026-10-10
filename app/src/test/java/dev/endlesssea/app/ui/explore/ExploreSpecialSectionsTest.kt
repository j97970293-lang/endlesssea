package dev.endlesssea.app.ui.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExploreSpecialSectionsTest {
    @Test
    fun classifiesFrenchAccentedAndEnglishLabels() {
        assertEquals(ExploreSpecialKind.PROGRAMS, exploreSpecialKind("planning", "Programme de la semaine"))
        assertEquals(ExploreSpecialKind.PROGRAMS, exploreSpecialKind("airing", "Weekly schedule"))
        assertEquals(ExploreSpecialKind.NEWS, exploreSpecialKind("actus", "Actualités cinéma"))
        assertEquals(ExploreSpecialKind.NEWS, exploreSpecialKind("news", "Latest articles"))
    }

    @Test
    fun leavesOrdinaryCatalogCategoriesUnclassified() {
        assertNull(exploreSpecialKind("popular", "Populaires"))
        assertNull(exploreSpecialKind("main", ""))
    }
}
