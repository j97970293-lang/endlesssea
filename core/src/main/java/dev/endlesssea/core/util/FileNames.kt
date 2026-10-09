package dev.endlesssea.core.util

import java.text.Normalizer

/**
 * File-name safety + naming templates (docs/en/08 §2, spec §24 "renommage automatique").
 */
object FileNames {

    private val ILLEGAL = Regex("[\\\\/:*?\"<>|]")
    private val WHITESPACE = Regex("\\s+")

    /** FAT/SD-safe, Unicode-aware component (≤ 100 chars, trimmed dots/spaces). */
    fun sanitize(raw: String, maxLength: Int = 100): String {
        val cleaned = raw
            .replace(ILLEGAL, "")
            .replace(WHITESPACE, " ")
            .trim()
            .trimEnd('.')
            .ifBlank { "untitled" }
        return if (cleaned.length <= maxLength) cleaned
        else cleaned.take(maxLength - 1).trimEnd() + "…"
    }

    /** Accent-folded lowercase key used for local search/dedup. */
    fun normalizedKey(title: String): String =
        Normalizer.normalize(title.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(WHITESPACE, " ")

    /**
     * Default: "{title} - S{season:00}E{episode:00} [{quality}][{lang}].{ext}"
     * Tokens: {title} {year} {season:00} {episode:00} {quality} {lang} {ext} {server}
     */
    fun render(template: String, tokens: Map<String, String>): String {
        var out = template
        tokens.forEach { (key, value) ->
            out = out.replace("{$key}", value)
            // zero-padded form {key:00}
            if (value.all { it.isDigit() }) {
                out = out.replace(Regex("\\{$key:0+}")) { m ->
                    val width = m.value.length - key.length - 3
                    value.padStart(width.coerceAtLeast(1), '0')
                }
            } else {
                out = out.replace(Regex("\\{$key:0+}"), value)
            }
        }
        return out
    }

    /** Stable, readable names for other players; never invent a season for a film. */
    fun videoName(title: String, movie: Boolean, year: Int?, season: Int?, episode: Float,
        episodeTitle: String?, quality: String, language: String, extension: String): String {
        val base = sanitize(title, 65)
        val date = year?.takeIf { it > 0 && !base.contains("($it)") }?.let { " ($it)" }.orEmpty()
        val number = if (episode.isFinite() && episode >= 0f) {
            val parts = episode.toString().removeSuffix(".0").split('.', limit=2)
            parts[0].padStart(2,'0') + (parts.getOrNull(1)?.let { ".$it" } ?: "")
        } else null
        val episodeLabel = if (movie) "" else {
            val code = (season?.takeIf { it >= 0 }?.let { "S"+it.toString().padStart(2,'0') }.orEmpty()) +
                (number?.let { "E$it" } ?: episodeTitle?.takeIf { it.isNotBlank() }?.let { sanitize(it,30) }.orEmpty())
            code.takeIf { it.isNotBlank() }?.let { " - $it" }.orEmpty()
        }
        val tags = listOf(quality.takeUnless { it in setOf("UNKNOWN","AUTO","") },
            language.takeUnless { it in setOf("OTHER","UNKNOWN","") }).filterNotNull()
            .joinToString("") { " [${sanitize(it,16)}]" }
        val ext = extension.removePrefix(".").lowercase().takeIf { it in setOf("mp4","mkv","webm","ts","m4v","avi","mov") } ?: "mp4"
        return sanitize(base + date + episodeLabel + tags, 110) + ".$ext"
    }

    const val DEFAULT_TEMPLATE = "{title} - S{season:00}E{episode:00} [{quality}][{lang}].{ext}"
    const val MOVIE_TEMPLATE = "{title} ({year}).{ext}"
}
