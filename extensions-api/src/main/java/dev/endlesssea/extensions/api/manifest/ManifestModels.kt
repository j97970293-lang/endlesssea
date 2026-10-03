package dev.endlesssea.extensions.api.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** assets/extension.json inside an .esx package (docs/en/04 §3). Unknown fields are ignored. */
@Serializable
data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: Int,
    val versionName: String,
    val apiVersion: Int,
    val description: Map<String, String> = emptyMap(), // {"fr": "…", "en": "…"}
    val author: Author = Author(),
    val languages: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val iconUrl: String? = null,
    val entryClass: String? = null,        // required for compiled plugins
    val permissions: List<String> = emptyList(),
    val capabilities: Capabilities = Capabilities(),
    val sourceUrl: String? = null,
    val nsfw: Boolean = false,
) {
    @Serializable
    data class Author(val name: String = "", val url: String? = null)

    @Serializable
    data class Capabilities(
        val search: Boolean = true,
        val servers: Boolean = true,
        val subtitles: Boolean = true,
        val downloads: Boolean = true,
        val auth: Boolean = false,
    )
}

/** One entry of a repository index.json (docs/en/04 §4). */
@Serializable
data class RepoExtensionEntry(
    val id: String,
    val name: String,
    val version: Int,
    val versionName: String,
    val apiVersion: Int,
    val author: ExtensionManifest.Author = ExtensionManifest.Author(),
    val description: Map<String, String> = emptyMap(),
    val languages: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val minAppVersion: Int = 1,
    val size: Long = 0,
    val iconUrl: String? = null,
    val apkUrl: String,                    // *.esx OR declarative *.json bundle
    val sha256: String,
    @SerialName("kind") val kind: String = "COMPILED", // COMPILED | DECLARATIVE
    val nsfw: Boolean = false,
)

@Serializable
data class RepositoryIndex(
    val name: String,
    val description: String = "",
    val url: String,
    val extensions: List<RepoExtensionEntry> = emptyList(),
)

/** Shared, lenient parser used by the app and by tests. */
object ManifestParser {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun parseManifest(raw: String): ExtensionManifest = json.decodeFromString(raw)
    fun parseRepoIndex(raw: String): RepositoryIndex = json.decodeFromString(raw)
}
