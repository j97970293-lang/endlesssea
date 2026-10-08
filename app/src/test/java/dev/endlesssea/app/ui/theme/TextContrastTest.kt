package dev.endlesssea.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class TextContrastTest {
    @Test fun blackAndWhiteHaveMaximumContrast() {
        assertEquals(21.0, contrastRatio(-1, 0xFF000000.toInt()), .001)
    }
    @Test fun darkBlueIsReadableOnBlackAndBlueSurfaces() {
        val backgrounds = listOf(0xFF000000.toInt(), 0xFF141830.toInt(), 0xFF252840.toInt())
        val color = readableColor(0xFF002090.toInt(), backgrounds)
        backgrounds.forEach { assertTrue(contrastRatio(color, it) >= 4.5) }
    }
    @Test fun lightAccentIsDarkenedOnLightTheme() {
        val background = 0xFFFBFDFE.toInt()
        assertTrue(contrastRatio(readableColor(0xFFAFB8FF.toInt(), listOf(background)), background) >= 4.5)
    }
    @Test fun readableColorIsUnchanged() {
        assertEquals(-1, readableColor(-1, listOf(0xFF000000.toInt())))
    }
}
