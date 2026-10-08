package dev.endlesssea.app.branding

import org.junit.Assert.*
import org.junit.Test

class AppLogosTest {
    @Test fun defaultAndInvalidPreferencesResolveToBlueAndWhite() {
        assertEquals("blue_white", AppLogos.resolve(null).id)
        assertEquals("blue_white", AppLogos.resolve("removed-choice").id)
    }
    @Test fun allChoicesHaveUniqueStableIdsAndAliases() {
        assertEquals(8, AppLogos.all.size)
        assertEquals(8, AppLogos.all.map { it.id }.distinct().size)
        assertEquals(8, AppLogos.all.map { it.aliasClass }.distinct().size)
        AppLogos.all.forEach { assertEquals(it, AppLogos.resolve(it.id)) }
    }
}
