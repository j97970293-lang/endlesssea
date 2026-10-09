package dev.endlesssea.app.tracking

import org.json.JSONObject

data class RemoteLibraryEntry(
    val id: String, val title: String, val poster: String?, val progress: Int,
    val total: Int?, val status: String, val year: Int?,
) {
    fun hit() = TrackerSearchHit(id, title, total, poster, year)
}
internal data class RemoteLibraryPage(val entries: List<RemoteLibraryEntry>, val hasNext: Boolean)
internal fun parseAniListPage(response: JSONObject): RemoteLibraryPage {
    if (response.optJSONArray("errors")?.length()?.let { it > 0 } == true) throw TrackerError("AniList a refusé la lecture de la liste. Vérifiez la connexion du compte.")
    val page = response.getJSONObject("data").getJSONObject("Page")
    val list = page.getJSONArray("mediaList")
    val entries = (0 until list.length()).map { i ->
        val entry = list.getJSONObject(i); val media = entry.getJSONObject("media"); val titles = media.getJSONObject("title")
        RemoteLibraryEntry(media.getInt("id").toString(),
            titles.optString("userPreferred").takeUnless { it.isBlank() || it == "null" }
                ?: titles.optString("romaji").takeUnless { it.isBlank() || it == "null" } ?: titles.getString("english"),
            media.optJSONObject("coverImage")?.optString("large")?.takeUnless { it == "null" || it.isBlank() },
            entry.getInt("progress"), media.optInt("episodes").takeIf { it > 0 },
            normalizeAniListStatus(entry.getString("status")), media.optInt("seasonYear").takeIf { it > 0 })
    }
    return RemoteLibraryPage(entries, page.getJSONObject("pageInfo").getBoolean("hasNextPage"))
}
internal fun normalizeAniListStatus(status: String) = if (status == "CURRENT") "WATCHING" else status
fun remoteStatusLabel(status: String) = when (status) {
    "WATCHING", "CURRENT" -> "En cours"
    "COMPLETED" -> "Terminés"
    "PLANNING" -> "À voir"
    "PAUSED" -> "En pause"
    "DROPPED" -> "Abandonnés"
    "REPEATING" -> "Revisionnage"
    else -> status
}
