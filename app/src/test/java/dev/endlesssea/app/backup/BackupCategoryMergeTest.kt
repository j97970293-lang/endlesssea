package dev.endlesssea.app.backup

import org.junit.Assert.*
import org.junit.Test

class BackupCategoryMergeTest {
    @Test fun caseVariantsAccumulateInsteadOfOverwritingEarlierImportedItems() {
        val result = mergeBackupCategories(mapOf("Anime" to listOf("local")),
            linkedMapOf("ANIME" to listOf("one"), "anime" to listOf("two")))
        assertEquals(mapOf("Anime" to listOf("local", "one", "two")), result)
    }
    @Test fun newCategoryVariantsAlsoAccumulate() {
        assertEquals(mapOf("Films" to listOf("one", "two")), mergeBackupCategories(emptyMap(),
            linkedMapOf("Films" to listOf("one"), "films" to listOf("two"))))
    }
    @Test fun repeatedImportIsIdempotent() {
        val original = mapOf("Anime" to listOf("one"))
        val incoming = mapOf("ANIME" to listOf("one", "two", "two"))
        val first = mergeBackupCategories(original, incoming)
        assertEquals(first, mergeBackupCategories(first, incoming))
        assertEquals(listOf("one", "two"), first["Anime"])
    }
    @Test fun existingOrderAndSpellingArePreserved() {
        val original = linkedMapOf("À revoir" to listOf("a"), "Films" to listOf("b"))
        val result = mergeBackupCategories(original, linkedMapOf("FILMS" to listOf("c"), "Nouveau" to listOf("d")))
        assertEquals(listOf("À revoir", "Films", "Nouveau"), result.keys.toList())
        assertEquals(listOf("b", "c"), result["Films"])
    }
    @Test fun emptyImportedCategoryDoesNotClearExistingItems() {
        assertEquals(mapOf("Anime" to listOf("keep")),
            mergeBackupCategories(mapOf("Anime" to listOf("keep")), mapOf("ANIME" to emptyList())))
    }
    @Test fun inputsRemainUnchanged() {
        val original = linkedMapOf("Anime" to listOf("a"))
        val incoming = linkedMapOf("ANIME" to listOf("b"))
        mergeBackupCategories(original, incoming)
        assertEquals(mapOf("Anime" to listOf("a")), original)
        assertEquals(mapOf("ANIME" to listOf("b")), incoming)
    }
}
