package dev.endlesssea.player

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

data class LocalSidecar(val uri: String, val name: String, val role: String)

/** Names written beside a downloaded video: `film.audio.m4a`, `film.fr.vtt`. */
fun sidecarRole(videoName: String, candidate: String): String? {
    val stem = videoName.substringBeforeLast('.').lowercase()
    val other = candidate.lowercase()
    if (!other.startsWith("$stem.")) return null
    val ext = other.substringAfterLast('.')
    val middle = other.removePrefix("$stem.").substringBeforeLast('.')
    return when {
        middle == "audio" && ext in setOf("m4a", "mp4", "webm", "aac") -> "audio"
        ext in setOf("srt", "vtt", "ass", "ssa") -> "subtitle"
        else -> null
    }
}

/** Best-effort lookup of audio and subtitle files saved next to a local video. Never throws. */
fun discoverLocalSidecars(context: Context, videoUri: String): List<LocalSidecar> = runCatching {
    val uri = Uri.parse(videoUri)
    val names = when (uri.scheme) {
        "file", null -> fileSiblings(uri)
        "content" -> contentSiblings(context, uri)
        else -> emptyList()
    }
    val videoName = names.firstOrNull { it.second == videoUri }?.first
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: return emptyList()
    names.mapNotNull { (name, found) ->
        if (found == videoUri) return@mapNotNull null
        sidecarRole(videoName, name)?.let { LocalSidecar(found, name, it) }
    }
}.getOrDefault(emptyList())

private fun fileSiblings(uri: Uri): List<Pair<String, String>> {
    val file = File(uri.path ?: return emptyList())
    val parent = file.parentFile ?: return emptyList()
    return parent.listFiles()?.map { it.name to Uri.fromFile(it).toString() }.orEmpty() +
        (file.name to uri.toString())
}

private fun contentSiblings(context: Context, uri: Uri): List<Pair<String, String>> {
    val found = linkedMapOf<String, String>()
    val name = queryName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/')
    if (name != null) found[name] = uri.toString()
    documentSiblings(context, uri).forEach { found[it.first] = it.second }
    mediaStoreSiblings(context, name).forEach { found[it.first] = it.second }
    return found.map { it.key to it.value }
}

private fun queryName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

private fun documentSiblings(context: Context, uri: Uri): List<Pair<String, String>> = runCatching {
    val docId = DocumentsContract.getDocumentId(uri)
    val parent = docId.substringBeforeLast('/')
    if (parent == docId || uri.authority == null) return emptyList()
    val children = DocumentsContract.buildChildDocumentsUri(uri.authority, parent)
    context.contentResolver.query(
        children,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
        null, null, null,
    )?.use { cursor ->
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        buildList {
            while (cursor.moveToNext()) {
                val childName = cursor.getString(nameCol) ?: continue
                val childId = cursor.getString(idCol) ?: continue
                val child = DocumentsContract.buildDocumentUri(uri.authority, childId)
                add(childName to child.toString())
            }
        }
    }.orEmpty()
}.getOrDefault(emptyList())

private fun mediaStoreSiblings(context: Context, videoName: String?): List<Pair<String, String>> {
    if (videoName.isNullOrBlank()) return emptyList()
    val stem = videoName.substringBeforeLast('.')
    val volume = if (android.os.Build.VERSION.SDK_INT >= 29) MediaStore.VOLUME_EXTERNAL else "external"
    val collection = MediaStore.Files.getContentUri(volume)
    return runCatching {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("$stem.%"),
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            buildList {
                while (cursor.moveToNext()) {
                    val childName = cursor.getString(nameCol) ?: continue
                    val id = cursor.getLong(idCol)
                    add(childName to android.content.ContentUris.withAppendedId(collection, id).toString())
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}
