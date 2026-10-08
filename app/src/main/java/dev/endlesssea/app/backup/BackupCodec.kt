package dev.endlesssea.app.backup

import dev.endlesssea.app.local.LocalMetadataCodec
import dev.endlesssea.data.db.*
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.CodingErrorAction
import java.nio.ByteBuffer
import org.json.JSONArray
import org.json.JSONObject

/** Only explicitly listed fields are portable. Never dumps arbitrary preferences or tracker credentials. */
object BackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    private const val MAX_ROWS = 100_000

    fun read(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        input.use { stream ->
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= MAX_BYTES) { "Sauvegarde trop volumineuse (maximum 20 Mio)." }
                output.write(buffer, 0, count)
            }
        }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray())).toString().removePrefix("\uFEFF")
    }

    fun encode(snapshot: BackupSnapshot): String {
        val root = JSONObject().put("app", "endless_sea").put("version", 2).put("exportedAt", snapshot.exportedAt)
        root.put("library", JSONArray().apply { snapshot.library.forEach { row -> put(encodeLibraryEntity(row)) } })
        root.put("history", JSONArray().apply { snapshot.history.forEach { row -> put(encodeWatchHistoryEntity(row)) } })
        root.put("media", JSONArray().apply { snapshot.media.forEach { row -> put(encodeMediaEntity(row)) } })
        root.put("genres", JSONArray().apply { snapshot.genres.forEach { row -> put(encodeGenreEntity(row)) } })
        root.put("customCategories", JSONObject().apply {
            snapshot.customCategories.forEach { (name, items) -> put(name, JSONArray(items)) }
        })
        root.put("localMetadata", JSONObject(LocalMetadataCodec.encode(snapshot.localMetadata)))
        snapshot.preferences?.let { root.put("preferences", encodeBackupPreferences(it)) }
        val text = root.toString(2)
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Sauvegarde trop volumineuse (maximum 20 Mio)." }
        return text
    }

    fun decode(text: String): BackupSnapshot {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Sauvegarde trop volumineuse (maximum 20 Mio)." }
        checkNesting(text)
        val parser = org.json.JSONTokener(text)
        val root = parser.nextValue()
        require(root is JSONObject && parser.nextClean() == '\u0000') { "Le fichier doit contenir un seul objet JSON." }
        require(root.getString("app") == "endless_sea") { "Ce fichier n'est pas une sauvegarde Endless Sea." }
        val version = root.int("version")
        require(version in 1..2) { "Version de sauvegarde non prise en charge : $version." }
        val library = rows(root.getJSONArray("library")) { row ->
            decodeLibraryEntity(row, version).also {
                require(it.mediaId.isNotBlank() && it.category.isNotBlank()) { "Entrée de bibliothèque sans identifiant ou catégorie." }
                it.customGenresJson?.let(::jsonArray)
            }
        }
        val history = rows(root.getJSONArray("history")) { row ->
            decodeWatchHistoryEntity(row).also { require(it.episodeId.isNotBlank()) { "Épisode sans identifiant." } }
        }
        val media = if (version == 1) emptyList() else rows(root.getJSONArray("media")) { row ->
            decodeMediaEntity(row).also {
                require(it.id.isNotBlank() && it.extensionId.isNotBlank()) { "Fiche sans identifiant." }
                jsonArray(it.altTitlesJson); jsonArray(it.genresJson); jsonArray(it.languagesJson); jsonArray(it.studiosJson)
                jsonObject(it.externalIdsJson)
            }
        }
        val genres = if (version == 1) emptyList() else rows(root.getJSONArray("genres")) { row ->
            decodeGenreEntity(row).also { require(it.name.isNotBlank()) { "Genre sans nom." } }
        }
        requireUnique(library.map { it.mediaId }); requireUnique(history.map { it.episodeId }); requireUnique(media.map { it.id })
        val categories = if (version == 1) emptyMap() else root.getJSONObject("customCategories").let { values ->
            require(values.length() <= 1000) { "Trop de catégories." }
            values.keys().asSequence().associateWith { name ->
                require(name.isNotBlank() && name.length <= 24 && name == name.trim() && '"' !in name) { "Nom de catégorie invalide." }
                val items = values.getJSONArray(name)
                require(items.length() <= MAX_ROWS) { "Catégorie trop volumineuse." }
                (0 until items.length()).map { items.getString(it).also { id -> require(id.isNotBlank()) } }.distinct()
            }
        }
        val localMetadata = if (version == 1) emptyMap() else root.getJSONObject("localMetadata").let { values ->
            require(values.length() <= MAX_ROWS) { "Trop de métadonnées locales." }
            values.keys().forEach { uri ->
                require(uri.isNotBlank()) { "Métadonnées sans URI." }
                val fields = values.getJSONArray(uri)
                require(fields.length() in 2..5) { "Métadonnées locales invalides." }
                for (i in 0 until fields.length()) {
                    if (!fields.isNull(i)) require(fields.get(i) is String) { "Métadonnées locales invalides." }
                    if (i >= 2 && !fields.isNull(i) && fields.getString(i).isNotBlank()) {
                        require((fields.getString(i).toIntOrNull() ?: -1) >= 0) { "Repère de lecture invalide." }
                    }
                }
            }
            LocalMetadataCodec.decodeAll(values.toString())
        }
        val preferences = if (root.has("preferences") && version >= 2) decodeBackupPreferences(root.getJSONObject("preferences")) else null
        preferences?.let {
            require(it.themeMode in 0..3 && it.defaultSpeed in 0.25f..3f && it.skipSeconds in setOf(5, 10, 15, 30, 85)) { "Réglages de lecture invalides." }
            require(it.playerTheme in dev.endlesssea.app.di.AppPrefs.PLAYER_THEMES.keys) { "Thème du lecteur inconnu." }
            require(it.playerToolsPosition in setOf("top", "bottom") && it.playerProgressPosition in setOf("top", "bottom") && it.megaSkipSide in setOf("left", "right")) { "Position des commandes invalide." }
        }
        return BackupSnapshot(version, root.long("exportedAt"), library, history, media, genres, categories, localMetadata, preferences)
    }

    private fun checkNesting(text: String) {
        var quoted = false
        var escaped = false
        var depth = 0
        for (c in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '\'', '/', '#' -> throw IllegalArgumentException("Commentaires et chaînes non JSON refusés.")
                '{', '[' -> { depth++; require(depth <= 32) { "JSON trop profondément imbriqué." } }
                '}', ']' -> depth--
            }
        }
    }

    private fun jsonArray(text: String): JSONArray { checkNesting(text); return JSONArray(text) }
    private fun jsonObject(text: String): JSONObject { checkNesting(text); return JSONObject(text) }

    private fun <T> rows(array: JSONArray, decode: (JSONObject) -> T): List<T> {
        require(array.length() <= MAX_ROWS) { "Sauvegarde trop volumineuse." }
        return (0 until array.length()).map { decode(array.getJSONObject(it)) }
    }
    private fun requireUnique(ids: List<String>) { require(ids.size == ids.toSet().size) { "Identifiants en double dans la sauvegarde." } }
    private fun JSONObject.text(key: String): String = get(key).let { require(it is String) { "Champ texte invalide : $key." }; it }
    private fun JSONObject.bool(key: String): Boolean = get(key).let { require(it is Boolean) { "Champ booléen invalide : $key." }; it }
    private fun JSONObject.long(key: String): Long = get(key).let {
        require(it is Long || it is Int) { "Champ entier invalide : $key." }
        (it as Number).toLong().also { n -> require(n >= 0) { "Valeur négative : $key." } }
    }
    private fun JSONObject.int(key: String): Int = long(key).also { require(it <= Int.MAX_VALUE) { "Entier trop grand : $key." } }.toInt()
    private fun JSONObject.number(key: String): Double = get(key).let { require(it is Number); it.toDouble().also { n -> require(n.isFinite()) } }

    private fun encodeLibraryEntity(row: LibraryEntity): JSONObject = JSONObject().apply {
        put("mediaId", row.mediaId)
        put("category", row.category)
        put("favorite", row.favorite)
        put("addedAt", row.addedAt)
        put("customGenresJson", row.customGenresJson ?: JSONObject.NULL)
        put("sortOverride", row.sortOverride ?: JSONObject.NULL)
        put("status", row.status)
    }
    private fun decodeLibraryEntity(row: JSONObject, version: Int): LibraryEntity = LibraryEntity(
        mediaId = row.text("mediaId"),
        category = row.text("category"),
        favorite = row.bool("favorite"),
        addedAt = row.long("addedAt"),
        customGenresJson = if (version == 1) null else if (row.isNull("customGenresJson")) null else row.text("customGenresJson"),
        sortOverride = if (version == 1) null else if (row.isNull("sortOverride")) null else row.text("sortOverride"),
        status = if (version == 1) "NONE" else row.text("status"),
    )

    private fun encodeWatchHistoryEntity(row: WatchHistoryEntity): JSONObject = JSONObject().apply {
        put("episodeId", row.episodeId)
        put("mediaId", row.mediaId)
        put("positionMs", row.positionMs)
        put("durationMs", row.durationMs)
        put("watched", row.watched)
        put("updatedAt", row.updatedAt)
    }
    private fun decodeWatchHistoryEntity(row: JSONObject): WatchHistoryEntity = WatchHistoryEntity(
        episodeId = row.text("episodeId"),
        mediaId = row.text("mediaId"),
        positionMs = row.long("positionMs"),
        durationMs = row.long("durationMs"),
        watched = row.bool("watched"),
        updatedAt = row.long("updatedAt"),
    )

    private fun encodeMediaEntity(row: MediaEntity): JSONObject = JSONObject().apply {
        put("id", row.id)
        put("extensionId", row.extensionId)
        put("type", row.type)
        put("title", row.title)
        put("titleKey", row.titleKey)
        put("altTitlesJson", row.altTitlesJson)
        put("synopsis", row.synopsis ?: JSONObject.NULL)
        put("posterUrl", row.posterUrl ?: JSONObject.NULL)
        put("bannerUrl", row.bannerUrl ?: JSONObject.NULL)
        put("year", row.year ?: JSONObject.NULL)
        put("status", row.status ?: JSONObject.NULL)
        put("rating", row.rating ?: JSONObject.NULL)
        put("genresJson", row.genresJson)
        put("languagesJson", row.languagesJson)
        put("studiosJson", row.studiosJson)
        put("episodeCount", row.episodeCount ?: JSONObject.NULL)
        put("durationMin", row.durationMin ?: JSONObject.NULL)
        put("externalIdsJson", row.externalIdsJson)
        put("customTitle", row.customTitle ?: JSONObject.NULL)
        put("customCoverUri", row.customCoverUri ?: JSONObject.NULL)
        put("cachedAt", row.cachedAt)
    }
    private fun decodeMediaEntity(row: JSONObject): MediaEntity = MediaEntity(
        id = row.text("id"),
        extensionId = row.text("extensionId"),
        type = row.text("type"),
        title = row.text("title"),
        titleKey = row.text("titleKey"),
        altTitlesJson = row.text("altTitlesJson"),
        synopsis = if (row.isNull("synopsis")) null else row.text("synopsis"),
        posterUrl = if (row.isNull("posterUrl")) null else row.text("posterUrl"),
        bannerUrl = if (row.isNull("bannerUrl")) null else row.text("bannerUrl"),
        year = if (row.isNull("year")) null else row.int("year"),
        status = if (row.isNull("status")) null else row.text("status"),
        rating = if (row.isNull("rating")) null else row.number("rating"),
        genresJson = row.text("genresJson"),
        languagesJson = row.text("languagesJson"),
        studiosJson = row.text("studiosJson"),
        episodeCount = if (row.isNull("episodeCount")) null else row.int("episodeCount"),
        durationMin = if (row.isNull("durationMin")) null else row.int("durationMin"),
        externalIdsJson = row.text("externalIdsJson"),
        customTitle = if (row.isNull("customTitle")) null else row.text("customTitle"),
        customCoverUri = if (row.isNull("customCoverUri")) null else row.text("customCoverUri"),
        cachedAt = row.long("cachedAt"),
    )

    private fun encodeGenreEntity(row: GenreEntity): JSONObject = JSONObject().apply {
        put("id", row.id)
        put("name", row.name)
        put("visible", row.visible)
        put("position", row.position)
        put("builtin", row.builtin)
    }
    private fun decodeGenreEntity(row: JSONObject): GenreEntity = GenreEntity(
        id = row.long("id"),
        name = row.text("name"),
        visible = row.bool("visible"),
        position = row.int("position"),
        builtin = row.bool("builtin"),
    )

    private fun encodeBackupPreferences(row: BackupPreferences): JSONObject = JSONObject().apply {
        put("themeMode", row.themeMode)
        put("defaultSpeed", row.defaultSpeed)
        put("autoResume", row.autoResume)
        put("skipSeconds", row.skipSeconds)
        put("playerTheme", row.playerTheme)
        put("playerToolsPosition", row.playerToolsPosition)
        put("playerProgressPosition", row.playerProgressPosition)
        put("megaSkipSide", row.megaSkipSide)
        put("recordHistory", row.recordHistory)
    }
    private fun decodeBackupPreferences(row: JSONObject): BackupPreferences = BackupPreferences(
        themeMode = row.int("themeMode"),
        defaultSpeed = row.number("defaultSpeed").toFloat(),
        autoResume = row.bool("autoResume"),
        skipSeconds = row.int("skipSeconds"),
        playerTheme = row.text("playerTheme"),
        playerToolsPosition = row.text("playerToolsPosition"),
        playerProgressPosition = row.text("playerProgressPosition"),
        megaSkipSide = row.text("megaSkipSide"),
        recordHistory = row.bool("recordHistory"),
    )
}
