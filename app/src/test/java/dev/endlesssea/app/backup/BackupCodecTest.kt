package dev.endlesssea.app.backup

import dev.endlesssea.app.di.AppPrefs.LocalFileMeta
import dev.endlesssea.data.db.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupCodecTest {
    private fun snapshot() = BackupSnapshot(
        exportedAt = 1234,
        library = listOf(LibraryEntity("extension:1", "CUSTOM_FAVORITES", true, 1, "[\"Fantasy\"]", "title", "COMPLETED")),
        history = listOf(WatchHistoryEntity("episode:1", "extension:1", 1000, 1000, true, 50)),
        media = listOf(MediaEntity("extension:1", "extension", "ANIME", "Title", "title", customTitle = "Mon titre", cachedAt = 42)),
        genres = listOf(GenreEntity(7, "Mon genre", true, 0, false)),
        customCategories = mapOf("À revoir" to listOf("extension:1", "content://episode")),
        localMetadata = mapOf("content://episode" to LocalFileMeta("Titre, spécial", "content://image", 0, 90, 1200)),
        preferences = BackupPreferences(3, 1.25f, true, 10, "floating", "bottom", "top", "left", false),
    )

    @Test fun versionTwoRoundTripsAllPortableFields() {
        assertEquals(snapshot(), BackupCodec.decode(BackupCodec.encode(snapshot())))
    }
    @Test fun completedAndOlderHistoryIsNotFilteredOrLimitedTo500() {
        val source = snapshot().copy(history = (1..701).map { WatchHistoryEntity("ep:$it", "media", 100, 100, true, it.toLong()) })
        assertEquals(source.history, BackupCodec.decode(BackupCodec.encode(source)).history)
    }
    @Test fun oldVersionOneRemainsReadable() {
        val json = """{"app":"endless_sea","version":1,"exportedAt":5,"library":[{"mediaId":"id","category":"FILM","favorite":true,"addedAt":1}],"history":[{"episodeId":"ep","mediaId":"","positionMs":5,"durationMs":0,"watched":false,"updatedAt":2}]}"""
        val imported = BackupCodec.decode(json)
        assertEquals("NONE", imported.library.single().status)
        assertEquals("", imported.history.single().mediaId)
        assertNull(imported.preferences)
        assertTrue(imported.media.isEmpty())
    }
    @Test fun unrelatedAndUnsupportedFilesAreRejected() {
        rejects("{}")
        rejects(JSONObject(BackupCodec.encode(snapshot())).put("app", "other").toString())
        rejects(JSONObject(BackupCodec.encode(snapshot())).put("version", 999).toString())
    }
    @Test fun trailingGarbageAndExcessiveNestingAreRejected() {
        rejects(BackupCodec.encode(snapshot()) + " trailing junk")
        rejects("[".repeat(100) + "0" + "]".repeat(100))
    }
    @Test fun malformedLaterRowsCannotProduceAPartialSnapshot() {
        val root = JSONObject(BackupCodec.encode(snapshot()))
        root.getJSONArray("history").put(JSONObject().put("episodeId", "broken"))
        rejects(root.toString())
    }
    @Test fun duplicateIdentifiersAndNegativePositionsAreRejected() {
        val original = snapshot()
        rejects(BackupCodec.encode(original.copy(history = original.history + original.history)))
        rejects(BackupCodec.encode(original.copy(history = listOf(original.history.single().copy(positionMs = -1)))))
    }
    @Test fun invalidPortablePreferencesAreRejected() {
        rejects(BackupCodec.encode(snapshot().copy(preferences = snapshot().preferences!!.copy(defaultSpeed = 99f))))
    }
    @Test fun credentialsAreNotPartOfTheFormat() {
        val keys = JSONObject(BackupCodec.encode(snapshot())).keys().asSequence().toSet()
        assertEquals(setOf("app", "version", "exportedAt", "library", "history", "media", "genres", "customCategories", "localMetadata", "preferences"), keys)
    }
    @Test fun streamIsClosedAndUtf8BomIsAccepted() {
        var closed = false
        val input = object : ByteArrayInputStream(("\uFEFF" + BackupCodec.encode(snapshot())).toByteArray(Charsets.UTF_8)) {
            override fun close() { closed = true; super.close() }
        }
        assertEquals(snapshot(), BackupCodec.decode(BackupCodec.read(input)))
        assertTrue(closed)
    }
    @Test fun oversizedStreamIsRejectedAndClosed() {
        var closed = false
        val input = object : ByteArrayInputStream(ByteArray(BackupCodec.MAX_BYTES + 1)) {
            override fun close() { closed = true; super.close() }
        }
        try { BackupCodec.read(input); fail("Oversized backup accepted") } catch (_: IllegalArgumentException) { }
        assertTrue(closed)
    }
    @Test fun newestProgressWinsButEqualDatesPreserveCurrentDevice() {
        val current = WatchHistoryEntity("ep", "media", 500, 1000, false, 100)
        assertTrue(mergeBackupHistory(listOf(current), listOf(current.copy(positionMs = 10, updatedAt = 99))).isEmpty())
        assertTrue(mergeBackupHistory(listOf(current), listOf(current.copy(positionMs = 10))).isEmpty())
        val newer = current.copy(positionMs = 1000, watched = true, updatedAt = 101)
        assertEquals(listOf(newer), mergeBackupHistory(listOf(current), listOf(newer)))
        assertEquals(listOf(current), mergeBackupHistory(emptyList(), listOf(current)))
    }
    private fun rejects(text: String) {
        try { BackupCodec.decode(text); fail("Invalid backup accepted") }
        catch (_: IllegalArgumentException) { }
        catch (_: org.json.JSONException) { }
    }
}
