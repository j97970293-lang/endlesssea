package dev.endlesssea.app.di

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderTest {
    @Test fun appendsNewTabsWithoutReorderingUserChoices() {
        assertEquals(listOf("library", "home", "statistics"),
            normalizeTabOrder(listOf("library", "old", "home", "home"), listOf("home", "library", "statistics")))
    }
    @Test fun normalizationIsIdempotent() {
        val all = listOf("home", "library", "statistics")
        val once = normalizeTabOrder(listOf("library"), all)
        assertEquals(once, normalizeTabOrder(once, all))
    }
}
