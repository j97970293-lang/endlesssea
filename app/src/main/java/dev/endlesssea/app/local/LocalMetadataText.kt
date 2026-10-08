package dev.endlesssea.app.local

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal const val MAX_LOCAL_METADATA_BYTES = 1024 * 1024

/** Bounded UTF-8 input for optional details.json / episodes.json, never for video data. */
internal fun readLocalMetadataText(
    input: InputStream,
    maxBytes: Int = MAX_LOCAL_METADATA_BYTES,
    checkActive: () -> Unit = {},
): String = input.use { stream ->
    require(maxBytes >= 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        checkActive()
        val count = stream.read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= maxBytes) { "Métadonnées locales trop volumineuses" }
        output.write(buffer, 0, count)
    }
    checkActive()
    val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(output.toByteArray())).toString().removePrefix("\uFEFF")
    var quoted = false
    var escaped = false
    var depth = 0
    text.forEachIndexed { index, c ->
        if (index % 4096 == 0) checkActive()
        if (quoted) {
            if (escaped) escaped = false
            else if (c == '\\') escaped = true
            else if (c == '"') quoted = false
        } else when (c) {
            '"' -> quoted = true
            '\'', '/', '#' -> throw IllegalArgumentException("JSON non standard")
            '{', '[' -> { depth++; require(depth <= 32) { "Métadonnées trop imbriquées" } }
            '}', ']' -> depth--
        }
    }
    text // Syntax and schema are still checked by the caller's JSON parser.
}
