package dev.endlesssea.app.local

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.CharacterCodingException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LocalMetadataTextTest {
    private class Tracked(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }
    @Test fun readsUtf8AndClosesTheStream() {
        val text = """{"title":"Été à la mer"}"""
        val stream = Tracked(text.toByteArray())
        assertEquals(text, readLocalMetadataText(stream))
        assertTrue(stream.closed)
    }
    @Test fun stripsUtf8Bom() {
        assertEquals("{}", readLocalMetadataText(Tracked("\uFEFF{}".toByteArray())))
    }
    @Test fun byteLimitAcceptsExactSizeButRejectsOneMoreByte() {
        assertEquals("{}", readLocalMetadataText(Tracked("{}".toByteArray()), maxBytes = 2))
        val stream = Tracked("{} ".toByteArray())
        try { readLocalMetadataText(stream, maxBytes = 2); fail("Accepted oversized file") }
        catch (_: IllegalArgumentException) { }
        assertTrue(stream.closed)
    }
    @Test fun limitCountsBytesNotCharacters() {
        val stream = Tracked("\"é\"".toByteArray())
        try { readLocalMetadataText(stream, maxBytes = 3); fail("Accepted four bytes") }
        catch (_: IllegalArgumentException) { }
        assertTrue(stream.closed)
    }
    @Test fun malformedUtf8IsNotSilentlyReplaced() {
        val stream = Tracked(byteArrayOf(0xc3.toByte(), 0x28))
        try { readLocalMetadataText(stream); fail("Accepted invalid UTF-8") }
        catch (_: CharacterCodingException) { }
        assertTrue(stream.closed)
    }
    @Test fun excessiveNestingIsRejectedBeforeJsonParsing() {
        val stream = Tracked(("[".repeat(33) + "0" + "]".repeat(33)).toByteArray())
        try { readLocalMetadataText(stream); fail("Accepted deep nesting") }
        catch (_: IllegalArgumentException) { }
        assertTrue(stream.closed)
    }
    @Test fun bracesQuotesAndUrlsInsideStringsDoNotCountAsNesting() {
        val text = """{"description":"[[[ \"https://example.org/#a's\" ]]]"}"""
        assertEquals(text, readLocalMetadataText(Tracked(text.toByteArray())))
    }
    @Test fun cancellationPropagatesAndClosesTheStream() {
        val stream = Tracked(ByteArray(16384))
        var checks = 0
        try {
            readLocalMetadataText(stream) { if (++checks == 2) throw CancellationException("cancelled") }
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
        assertTrue(stream.closed)
    }
    @Test fun readFailureClosesTheStream() {
        var closed = false
        val stream = object : java.io.InputStream() {
            override fun read(): Int = throw IOException("removed SD card")
            override fun close() { closed = true }
        }
        try { readLocalMetadataText(stream); fail("Read error swallowed") }
        catch (_: IOException) { }
        assertTrue(closed)
    }
}
