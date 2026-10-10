package dev.endlesssea.app.ui.player.themes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeProviderTest {
    @Test fun savedLegacyIdsMigrateToTheEssentialLayout() {
        listOf(null, "default", "zen", "orbit", "compactbar", "neonframe", "split", "floating", "unknown")
            .forEach { oldId ->
                assertEquals("cinema", ThemeProvider.migrate(oldId))
                assertSame(CinemaTheme, ThemeProvider.of(oldId))
            }
    }

    @Test fun onlyTheEssentialPlayerThemeIsRegistered() {
        assertEquals(listOf("cinema"), ThemeProvider.all.keys.toList())
        assertSame(CinemaTheme, ThemeProvider.of("cinema"))
        assertEquals("cinema", ThemeProvider.migrate("cinema"))
    }

    @Test fun legacyBackupIdsRemainRecognizedAndUnknownIdsDoNot() {
        listOf("default", "zen", "orbit", "compactbar", "neonframe", "split", "floating").forEach {
            assertTrue(dev.endlesssea.app.di.AppPrefs.isKnownPlayerTheme(it))
            assertEquals("cinema", dev.endlesssea.app.di.AppPrefs.migratePlayerTheme(it))
        }
        assertFalse(dev.endlesssea.app.di.AppPrefs.isKnownPlayerTheme("made-up"))
        assertEquals("cinema", dev.endlesssea.app.di.AppPrefs.migratePlayerTheme(null))
    }
}
