package dev.endlesssea.app.local

import dev.endlesssea.app.di.AppPrefs.LocalFileMeta
import org.junit.Assert.*
import org.junit.Test

class LocalMetadataCodecTest {
    @Test fun uriIsNotMistakenForTitle() {
        val value = LocalMetadataCodec.decodeAll("""{"content://episode":["Titre","content://cover","0","90","1200"]}""")["content://episode"]!!
        assertEquals("Titre", value.title)
        assertEquals("content://cover", value.coverUri)
        assertEquals(0, value.introStartSec)
        assertEquals(90, value.introEndSec)
        assertEquals(1200, value.outroStartSec)
    }
    @Test fun oldTwoFieldEntriesRemainReadable() {
        assertEquals(LocalFileMeta("Titre", "cover"), LocalMetadataCodec.decodeAll("""{"uri":["Titre","cover"]}""")["uri"])
    }
    @Test fun mediaTypeIsStoredAsSixthFieldAndLegacyFiveFieldEntriesRemainReadable() {
        val encoded = LocalMetadataCodec.encode(mapOf("folder:film" to LocalFileMeta(mediaType = "MOVIE")))
        assertEquals("MOVIE", LocalMetadataCodec.decodeAll(encoded)["folder:film"]?.mediaType)
        assertNull(LocalMetadataCodec.decodeAll("""{"uri":["Titre","cover","0","90","200"]}""")["uri"]?.mediaType)
    }
    @Test fun specialCharactersRoundTripWithoutDestructiveReplacement() {
        val values = mapOf("content://été/[1]" to LocalFileMeta("Un \\\"titre\\\", [spécial]", "content://image?a=1,b=2", 0, 90, 200, "SERIES"))
        assertEquals(values, LocalMetadataCodec.decodeAll(LocalMetadataCodec.encode(values)))
    }
    @Test fun missingMetadataDoesNotCreateFakeValues() {
        assertEquals(LocalFileMeta(), LocalMetadataCodec.decodeAll("""{"uri":["",null]}""")["uri"])
        assertTrue(LocalMetadataCodec.decodeAll("invalid").isEmpty())
    }
}
