package dev.endlesssea.downloader.hls

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Preserve completed bytes; discard only an uncheckpointed tail from an interrupted append. */
internal fun prepareHlsResume(part: File, marker: File, fingerprint: String, checkpointEnd: Long?) {
    if (checkpointEnd == null) {
        marker.writeText(fingerprint)
        return
    }
    if (checkpointEnd <= 0 || !part.isFile || part.length() < checkpointEnd ||
        !marker.isFile || marker.length() > 128 || marker.readText() != fingerprint) {
        throw IOException("Reprise HLS non vérifiable (ancien partiel ou flux modifié). Annule cette tâche et relance le téléchargement ; le fichier partiel est conservé.")
    }
    RandomAccessFile(part, "rw").use { it.setLength(checkpointEnd) }
}
