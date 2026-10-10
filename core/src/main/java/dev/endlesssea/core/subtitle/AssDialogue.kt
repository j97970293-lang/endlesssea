package dev.endlesssea.core.subtitle

/**
 * Converts basic ASS/SSA dialogue to SRT.
 *
 * Full libass rendering (fonts, drawings, animations) is intentionally not bundled:
 * it would add a native library and a continuous CPU cost on low-RAM devices.
 * Override tags are stripped so the spoken lines remain readable.
 */
object AssDialogue {

    fun toSrt(source: String): String? {
        val cues = source.lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("Dialogue:", ignoreCase = true)) return@mapNotNull null
            val parts = trimmed.substringAfter(':').split(',', limit = 10)
            if (parts.size < 10) return@mapNotNull null
            val start = toSrtTime(parts[1].trim()) ?: return@mapNotNull null
            val end = toSrtTime(parts[2].trim()) ?: return@mapNotNull null
            val text = clean(parts[9])
            if (text.isBlank()) return@mapNotNull null
            start to (end to text)
        }.toList()
        if (cues.isEmpty()) return null
        return buildString {
            cues.forEachIndexed { index, (start, rest) ->
                append(index + 1).append('\n')
                append(start).append(" --> ").append(rest.first).append('\n')
                append(rest.second).append("\n\n")
            }
        }.trimEnd() + "\n"
    }

    private fun clean(raw: String): String = raw
        .replace(Regex("""\{[^}]*}"""), "")
        .replace("\\N", "\n", ignoreCase = false)
        .replace("\\n", "\n")
        .replace("\\h", " ")
        .replace(Regex("""\\[a-zA-Z]+\d*"""), "")
        .lines().joinToString("\n") { it.trim() }
        .trim()

    /** ASS `H:MM:SS.cs` or `H:MM:SS:cs` → SRT `HH:MM:SS,mmm`. */
    fun toSrtTime(raw: String): String? {
        val match = Regex("""(\d+):(\d{2}):(\d{2})[.:](\d{1,3})""").find(raw.trim()) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: return null
        val minutes = match.groupValues[2].toIntOrNull() ?: return null
        val seconds = match.groupValues[3].toIntOrNull() ?: return null
        val fraction = match.groupValues[4].padEnd(3, '0').take(3).toIntOrNull() ?: return null
        if (minutes > 59 || seconds > 59) return null
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, fraction)
    }
}
