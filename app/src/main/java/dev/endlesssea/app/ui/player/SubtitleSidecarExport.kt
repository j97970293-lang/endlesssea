package dev.endlesssea.app.ui.player

import android.content.Context
import android.net.Uri
import dev.endlesssea.extensions.api.model.SubtitleFormat
import dev.endlesssea.extensions.api.model.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.Locale

private const val MAX_SUBTITLE_BYTES = 20L * 1024L * 1024L

internal fun subtitleExportFileName(mediaTitle: String, track: SubtitleTrack): String {
    val rawBase = "$mediaTitle - ${track.label.ifBlank { track.lang }}"
    val safeBase = rawBase.replace(Regex("""[\\/:*?"<>|\u0000]"""), "_")
        .trim().trim('.').take(120).ifBlank { "sous-titres" }
    val extension = when (track.format) {
        SubtitleFormat.SRT -> "srt"
        SubtitleFormat.ASS -> "ass"
        SubtitleFormat.SSA -> "ssa"
        SubtitleFormat.VTT -> "vtt"
        SubtitleFormat.UNKNOWN -> runCatching { java.net.URI(track.url).path }
            .getOrNull()?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
            ?.takeIf { it in setOf("srt", "ass", "ssa", "vtt") } ?: "srt"
    }
    return "$safeBase.$extension"
}

/** Exporte uniquement le fichier de sous-titres côté extension vers l'URI choisi par l'utilisateur. */
internal suspend fun exportSidecarSubtitle(
    context: Context,
    track: SubtitleTrack,
    headers: Map<String, String>,
    destination: Uri,
): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val source = Uri.parse(track.url)
        require(source.scheme == "http" || source.scheme == "https") {
            "La source de ce sous-titre n'est pas une URL HTTP(S)."
        }
        val request = Request.Builder().url(track.url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        val client = dev.endlesssea.core.net.HttpClients.baseBuilder().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Téléchargement refusé (${response.code})." }
            val body = response.body ?: error("Réponse vide.")
            check(body.contentLength() < 0L || body.contentLength() <= MAX_SUBTITLE_BYTES) {
                "Fichier de sous-titres trop volumineux."
            }
            val bytes = body.bytes()
            check(bytes.isNotEmpty()) { "Fichier de sous-titres vide." }
            check(bytes.size <= MAX_SUBTITLE_BYTES) { "Fichier de sous-titres trop volumineux." }
            val preview = bytes.take(512).toByteArray().toString(Charsets.UTF_8)
                .trimStart('\uFEFF', ' ', '\t', '\r', '\n').lowercase(Locale.ROOT)
            check(!preview.startsWith("<!doctype html") && !preview.startsWith("<html")) {
                "La source a renvoyé une page web au lieu du sous-titre."
            }
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: error("Impossible d'écrire à l'emplacement choisi.")
            output.use { it.write(bytes) }
        }
    }
}
