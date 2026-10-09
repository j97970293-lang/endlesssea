package dev.endlesssea.app.ui.home

import dev.endlesssea.app.ui.search.SearchItemUi
import org.junit.Assert.*
import org.junit.Test

class SourceFeaturedTest {
    private fun row(source: String)=HomeRowUi(source,listOf(SearchItemUi("$source:1",source)),sourcePkg=source)
    @Test fun sourceSelectionChangesCarouselAsWellAsRows() {
        assertEquals(listOf("b:1"),sourceFeatured(listOf(row("a"),row("b")),"b",emptyList()).map{it.id})
    }
    @Test fun missingSourceNeverFallsBackToOtherSources() {
        assertTrue(sourceFeatured(listOf(row("a")),"b",listOf(SearchItemUi("a:cached","Cached"))).isEmpty())
    }
    @Test fun lateResponseForOldSourceCannotPolluteCurrentCarousel() {
        assertEquals(listOf("b:1"),sourceFeatured(listOf(row("b"),row("a")),"b",emptyList()).map{it.id})
    }
    @Test fun allSourcesCanUseOfflineCacheWhenCataloguesAreEmpty() {
        assertEquals(1,sourceFeatured(emptyList(),"ALL",listOf(SearchItemUi("cached","Cached"))).size)
    }
    @Test fun duplicateCardsAreNotRepeated() {
        assertEquals(1,sourceFeatured(listOf(row("a"),row("a")),"a",emptyList()).size)
    }
}
