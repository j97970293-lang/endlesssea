package dev.endlesssea.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.data.db.GenreDao
import dev.endlesssea.data.db.GenreEntity
import dev.endlesssea.data.db.LibraryDao
import dev.endlesssea.data.db.LibraryEntity
import dev.endlesssea.data.db.WatchHistoryDao
import dev.endlesssea.data.db.WatchHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class GenreUi(val id: Long, val name: String, val visible: Boolean, val position: Int)

data class SettingsUiState(
    val storageUri: String? = null,
    val wifiOnly: Boolean = true,
    val partsPerTask: Int = 4,
    val parallelTasks: Int = 2,
    val defaultSpeed: Float = 1f,
    val autoResume: Boolean = true,
    val themeMode: Int = AppPrefs.THEME_SYSTEM,
    val barTabs: Set<String> = AppPrefs.DEFAULT_TABS,
    val genres: List<GenreUi> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPrefs,
    private val genreDao: GenreDao,
    private val libraryDao: LibraryDao,
    private val historyDao: WatchHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch { prefs.storageUri.collect { v -> set { copy(storageUri = v) } } }
        viewModelScope.launch { prefs.wifiOnly.collect { v -> set { copy(wifiOnly = v) } } }
        viewModelScope.launch { prefs.partsPerTask.collect { v -> set { copy(partsPerTask = v) } } }
        viewModelScope.launch { prefs.parallelTasks.collect { v -> set { copy(parallelTasks = v) } } }
        viewModelScope.launch { prefs.defaultSpeed.collect { v -> set { copy(defaultSpeed = v) } } }
        viewModelScope.launch { prefs.autoResume.collect { v -> set { copy(autoResume = v) } } }
        viewModelScope.launch { prefs.themeMode.collect { v -> set { copy(themeMode = v) } } }
        viewModelScope.launch { prefs.barTabs.collect { v -> set { copy(barTabs = v) } } }
        viewModelScope.launch {
            genreDao.observeAll()
                .catch { /* la table est créée par seed — jamais vide de fait */ }
                .collect { g -> set { copy(genres = g.map { GenreUi(it.id, it.name, it.visible, it.position) }) } }
        }
    }

    private inline fun set(block: SettingsUiState.() -> SettingsUiState) {
        _uiState.value = _uiState.value.block()
    }

    // ----------------------------------------------------------- préférences

    fun onStorageChosen(uri: String?) {
        prefs.setStorageUri(uri)
        toastState(if (uri != null) "Emplacement enregistré" else "Emplacement réinitialisé")
    }

    fun setWifiOnly(v: Boolean) { prefs.setWifiOnly(v); toastState(if (v) "Wi-Fi uniquement activé" else "Téléchargement sur données mobiles autorisé") }
    fun setPartsPerTask(v: Int) { prefs.setPartsPerTask(v); toastState("$v segments par fichier") }
    fun setParallelTasks(v: Int) { prefs.setParallelTasks(v); toastState("$v tâche(s) simultanée(s)") }
    fun setDefaultSpeed(v: Float) { prefs.setDefaultSpeed(v); toastState("Vitesse par défaut : ${v}×") }
    fun setAutoResume(v: Boolean) { prefs.setAutoResume(v); toastState(if (v) "Reprise automatique activée" else "Reprise automatique désactivée") }
    fun setThemeMode(mode: Int) { prefs.setThemeMode(mode) }
    fun setBarTab(route: String, enabled: Boolean) { prefs.setBarTab(route, enabled) }

    // ---------------------------------------------------------------- genres

    fun addGenre(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isBlank()) { toastState("Nom de genre vide"); return@launch }
        val nextPosition = (genreDao.observeAll().first().maxOfOrNull { it.position } ?: -1) + 1
        genreDao.upsert(GenreEntity(name = trimmed, position = nextPosition, visible = true))
        toastState("Genre « $trimmed » ajouté")
    }

    fun renameGenre(id: Long, newName: String) = viewModelScope.launch {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) { toastState("Nom vide — rien renommé"); return@launch }
        genreDao.rename(id, trimmed)
        toastState("Genre renommé en « $trimmed »")
    }

    fun deleteGenre(id: Long) = viewModelScope.launch {
        genreDao.delete(id)
        toastState("Genre supprimé")
    }

    fun toggleGenre(id: Long, visible: Boolean) = viewModelScope.launch {
        genreDao.setVisible(id, visible)
    }

    fun moveGenre(id: Long, delta: Int) = viewModelScope.launch {
        val all = genreDao.observeAll().first()
        val idx = all.indexOfFirst { it.id == id }
        val target = idx + delta
        if (idx < 0 || target !in all.indices) return@launch
        genreDao.reorder(all[idx].id, all[target].position)
        genreDao.reorder(all[target].id, all[idx].position)
    }

    // ------------------------------------------------------------ sauvegarde

    /** Produit le JSON de sauvegarde (bibliothèque, favoris, historique). */
    suspend fun buildBackupJson(): String {
        val library = libraryDao.observeByCategory("ANIME").first() +
            libraryDao.observeByCategory("FILM").first() +
            libraryDao.observeByCategory("SERIE").first() +
            libraryDao.observeByCategory("OVA").first() +
            libraryDao.observeByCategory("ONA").first() +
            libraryDao.observeFavorites().first()
        val uniqueLib = library.distinctBy { it.mediaId }
        val history = historyDao.observeContinueWatching(500).first()

        val root = JSONObject().apply {
            put("app", "endless_sea")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("library", JSONArray().apply {
                uniqueLib.forEach {
                    put(JSONObject().apply {
                        put("mediaId", it.mediaId)
                        put("category", it.category)
                        put("favorite", it.favorite)
                        put("addedAt", it.addedAt)
                    })
                }
            })
            put("history", JSONArray().apply {
                history.forEach {
                    put(JSONObject().apply {
                        put("episodeId", it.episodeId)
                        put("mediaId", it.mediaId)
                        put("positionMs", it.positionMs)
                        put("durationMs", it.durationMs)
                        put("watched", it.watched)
                        put("updatedAt", it.updatedAt)
                    })
                }
            })
        }
        return root.toString(2)
    }

    /** Restaure le JSON produit par [buildBackupJson]. Retourne un message de bilan. */
    suspend fun restoreBackup(json: String): String = runCatching {
        val root = JSONObject(json)
        var libCount = 0
        root.optJSONArray("library")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                libraryDao.upsert(
                    LibraryEntity(
                        mediaId = e.getString("mediaId"),
                        category = e.optString("category", "ANIME"),
                        favorite = e.optBoolean("favorite", false),
                        addedAt = e.optLong("addedAt", System.currentTimeMillis()),
                    ),
                )
                libCount++
            }
        }
        var histCount = 0
        root.optJSONArray("history")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                historyDao.upsert(
                    WatchHistoryEntity(
                        episodeId = e.getString("episodeId"),
                        mediaId = e.optString("mediaId", ""),
                        positionMs = e.optLong("positionMs", 0),
                        durationMs = e.optLong("durationMs", 0),
                        watched = e.optBoolean("watched", false),
                        updatedAt = e.optLong("updatedAt", System.currentTimeMillis()),
                    ),
                )
                histCount++
            }
        }
        "Sauvegarde restaurée : $libCount entrée(s) de bibliothèque, $histCount historique(s)"
    }.getOrElse { "Sauvegarde illisible : ${it.message}" }

    fun onBackupExported(ok: Boolean) = toastState(if (ok) "Fichier de sauvegarde écrit" else "Export annulé")
    fun onBackupImported(message: String) = toastState(message)

    fun toastState(message: String) { set { copy(message = message) } }
    fun clearMessage() { set { copy(message = null) } }
}
