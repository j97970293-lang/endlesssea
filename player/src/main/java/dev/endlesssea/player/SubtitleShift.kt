package dev.endlesssea.player

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * §sous-titres (conversation 1) — **décalage temporel** des pistes externes.
 *
 * Media3 1.5 ne sait pas décaler les cues d'une piste (aucun champ de temps
 * modifiable dans [androidx.media3.common.text.Cue]) : on réécrit donc le
 * fichier SRT/VTT avec des horodatages décalés, dans le cache, puis on
 * rattache ce fichier au lecteur. Fonctionne dans les deux sens (avancer ou
 * retarder les sous-titres) et se limite aux fichiers locaux (content://,
 * file://) — les pistes embarquées dans la vidéo restent intactes.
 */
internal object SubtitleShift {

    /** Lit un fichier local (content:// ou file://). */
    fun readText(context: Context, uri: String): String? = runCatching {
        val u = Uri.parse(uri)
        when (u.scheme) {
            "content" -> context.contentResolver.openInputStream(u)
                ?.bufferedReader()?.use { it.readText() }
            "file", null -> File(u.path ?: uri).takeIf { it.isFile }?.readText()
            else -> null
        }
    }.getOrNull()

    /**
     * Écrit une copie décalée de [offsetMs] millisecondes dans le cache et
     * renvoie son URI (`file://…`) ; `null` si le fichier est illisible
     * (URL distante, format inconnu…).
     */
    fun shiftToCache(context: Context, uri: String, offsetMs: Long): String? {
        val text = readText(context, uri) ?: return null
        val vtt = uri.endsWith(".vtt", true)
        val shifted = shift(text, offsetMs, vtt)
        val dir = File(context.cacheDir, "subs").apply { mkdirs() }
        val out = File(dir, "shift_${offsetMs}_${uri.hashCode()}.${if (vtt) "vtt" else "srt"}")
        return runCatching {
            out.writeText(shifted)
            Uri.fromFile(out).toString()
        }.getOrNull()
    }

    /** Décale tous les horodatages d'un fichier SRT ou VTT. */
    fun shift(text: String, offsetMs: Long, vtt: Boolean): String =
        text.lineSequence().joinToString("\n") { line ->
            if (TIME_LINE.containsMatchIn(line)) shiftLine(line, offsetMs, vtt) else line
        }

    private val TIME_LINE = Regex(
        """(\d{1,2}:)?\d{1,2}:\d{2}[.,]\d{3}\s*-->\s*(\d{1,2}:)?\d{1,2}:\d{2}[.,]\d{3}""",
    )
    private val STAMP = Regex("""(\d{1,2}:)?\d{1,2}:\d{2}[.,]\d{3}""")

    private fun shiftLine(line: String, offsetMs: Long, vtt: Boolean): String =
        STAMP.replace(line) { match ->
            val raw = match.value
            val ms = parse(raw) ?: return@replace raw
            format(ms + offsetMs, raw.contains(','), raw.split(":").size >= 3)
        }

    private fun parse(stamp: String): Long? = runCatching {
        val normalized = stamp.replace(',', '.')
        val parts = normalized.split(":").map { it.trim() }
        val secPart = parts.last().toDouble()
        val minutes = parts.getOrNull(parts.size - 2)?.toInt() ?: 0
        val hours = parts.getOrNull(parts.size - 3)?.toInt() ?: 0
        ((hours * 3600 + minutes * 60) * 1000L) + (secPart * 1000).toLong()
    }.getOrNull()

    private fun format(ms: Long, comma: Boolean, withHours: Boolean): String {
        val clamped = ms.coerceAtLeast(0)
        val h = clamped / 3_600_000
        val m = (clamped % 3_600_000) / 60_000
        val s = (clamped % 60_000) / 1000
        val milli = clamped % 1000
        val sep = if (comma) "," else "."
        return if (withHours) {
            "%02d:%02d:%02d%s%03d".format(h, m, s, sep, milli)
        } else {
            "%02d:%02d%s%03d".format(h * 60 + m, s, sep, milli)
        }
    }
}
