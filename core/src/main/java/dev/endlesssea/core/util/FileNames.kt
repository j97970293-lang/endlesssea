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

    const val DEFAULT_TEMPLATE = "{title} - S{season:00}E{episode:00} [{quality}][{lang}].{ext}"
    const val MOVIE_TEMPLATE = "{title} ({year}).{ext}"
}
