package dev.endlesssea.app.ui.player.themes

import org.junit.Assert.*
import org.junit.Test

class ThemeProviderTest {
    @Test fun retiredAndUnknownThemesReturnToDefault() {
        listOf(null, "netflix", "youtube", "vlc", "plex", "unknown").forEach {
            assertEquals("default", ThemeProvider.migrate(it))
            assertSame(DefaultTheme, ThemeProvider.of(it))
        }
    }
    @Test fun sevenOriginalInterfacesHaveStableIds() {
        assertEquals(7, ThemeProvider.all.size)
        ThemeProvider.all.forEach { (id, theme) ->
            assertEquals(id, theme.id)
            assertEquals(id, ThemeProvider.migrate(id))
        }
    }
}
