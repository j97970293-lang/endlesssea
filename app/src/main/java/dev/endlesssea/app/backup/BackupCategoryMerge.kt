package dev.endlesssea.app.backup

/** Merge against the accumulated result, not repeatedly against the old preference store. */
internal fun mergeBackupCategories(
    current: Map<String, List<String>>,
    incoming: Map<String, List<String>>,
): Map<String, List<String>> {
    val result = linkedMapOf<String, List<String>>()
    current.forEach { (name, items) -> result[name] = items.distinct() }
    incoming.forEach { (name, items) ->
        val canonical = result.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
        result[canonical] = (result[canonical].orEmpty() + items).distinct()
    }
    return result
}
