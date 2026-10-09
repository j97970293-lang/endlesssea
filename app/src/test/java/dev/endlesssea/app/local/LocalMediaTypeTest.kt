package dev.endlesssea.app.local

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaTypeTest {
    @Test fun inferenceRecognizesFilmsAndNumberedSeries() {
        assertEquals(LocalMediaType.MOVIE, LocalMediaType.infer(listOf("My Film (2025).mp4")))
        assertEquals(LocalMediaType.SERIES, LocalMediaType.infer(listOf("S02E03 - Episode.mkv")))
        assertEquals(LocalMediaType.ANIME, LocalMediaType.infer(listOf("One Piece 03.mkv")))
    }

    @Test fun unknownProviderTypesNormalizeToEditableOtherChoice() {
        assertEquals(LocalMediaType.OTHER, LocalMediaType.normalize("OVA"))
        assertEquals(LocalMediaType.MOVIE, LocalMediaType.normalize("movie"))
    }
}
