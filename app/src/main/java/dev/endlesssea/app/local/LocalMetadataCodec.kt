package dev.endlesssea.app.local

import dev.endlesssea.app.di.AppPrefs.LocalFileMeta
import org.json.JSONArray
import org.json.JSONObject

/** The URI is an object key, not the first metadata field. Keeps legacy 2-field arrays. */
internal object LocalMetadataCodec {
    fun decodeAll(json: String): Map<String, LocalFileMeta> = runCatching {
        val root = JSONObject(json)
        root.keys().asSequence().mapNotNull { uri ->
            val fields = root.optJSONArray(uri) ?: return@mapNotNull null
            fun text(i: Int): String? = if (fields.isNull(i)) null else fields.optString(i).takeIf { it.isNotBlank() }
            fun seconds(i: Int): Int? = text(i)?.toIntOrNull()?.takeIf { it >= 0 }
            uri to LocalFileMeta(text(0), text(1), seconds(2), seconds(3), seconds(4), text(5))
        }.toMap()
    }.getOrDefault(emptyMap())

    fun encode(values: Map<String, LocalFileMeta>): String = JSONObject().apply {
        values.forEach { (uri, meta) -> put(uri, JSONArray().apply {
            put(meta.title ?: ""); put(meta.coverUri ?: "")
            put(meta.introStartSec?.toString() ?: ""); put(meta.introEndSec?.toString() ?: "")
            put(meta.outroStartSec?.toString() ?: ""); put(meta.mediaType ?: "")
        }) }
    }.toString()
}
