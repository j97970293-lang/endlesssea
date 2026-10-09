package dev.endlesssea.app.ui.tracking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.endlesssea.app.tracking.*
import dev.endlesssea.app.ui.search.SearchItemUi
import dev.endlesssea.data.db.LibraryDao
import dev.endlesssea.data.db.MediaDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

internal data class AniListLibraryState(val account: String? = null, val entries: List<RemoteLibraryEntry> = emptyList(),
    val busy: Boolean = false, val error: String? = null, val updatedAt: Long = 0,
    val linked: Map<String, List<String>> = emptyMap(), val targets: List<SearchItemUi> = emptyList())

@HiltViewModel
internal class AniListLibraryViewModel @Inject constructor(private val repository: TrackerRepository,
    private val libraryDao: LibraryDao, private val mediaDao: MediaDao,
    @ApplicationContext private val context: Context) : ViewModel() {
    private val _state = MutableStateFlow(AniListLibraryState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    init {
        viewModelScope.launch {
            repository.accounts.map { list -> list.firstOrNull { it.service == "ANILIST" && it.enabled }?.userName?.takeIf { it.isNotBlank() } }
                .distinctUntilChanged().collectLatest { name ->
                    refreshJob?.cancel()
                    _state.value = _state.value.copy(account = name, entries = emptyList(), busy = false, error = null, updatedAt = 0)
                    if (name != null) {
                        val cached = withContext(Dispatchers.IO) { readCache(name) }
                        _state.value = _state.value.copy(entries = cached.first, updatedAt = cached.second)
                        refresh()
                    }
                }
        }
        viewModelScope.launch { repository.links.collect { links ->
            _state.value = _state.value.copy(linked = links.filter { it.service == "ANILIST" }.groupBy({ it.remoteId }, { it.mediaId }))
        } }
        viewModelScope.launch { libraryDao.observeAll().collect { list ->
            _state.value = _state.value.copy(targets = list.mapNotNull { item -> mediaDao.byId(item.mediaId)?.let {
                SearchItemUi(it.id, it.customTitle ?: it.title, it.customCoverUri ?: it.posterUrl)
            } })
        } }
    }
    fun refresh() {
        val name = _state.value.account ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val entries = repository.pullLibrary("ANILIST")
                val now = System.currentTimeMillis()
                withContext(Dispatchers.IO) { writeCache(name, entries, now) }
                if (_state.value.account == name) _state.value = _state.value.copy(entries = entries, busy = false, updatedAt = now)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (_state.value.account == name) _state.value = _state.value.copy(busy = false,
                error = "${e.message ?: "AniList indisponible"}. La dernière liste importée est conservée.") }
        }
    }
    fun match(mediaId: String, entry: RemoteLibraryEntry, done: () -> Unit) = viewModelScope.launch {
        try {
            if (repository.link(mediaId,"ANILIST",entry.hit())) done()
            else _state.value = _state.value.copy(error = "Association impossible : vérifiez votre compte.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.value = _state.value.copy(error = e.message) }
    }
    private fun cache(name: String): android.util.AtomicFile {
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
        return android.util.AtomicFile(java.io.File(context.filesDir,"tracker-lists/anilist-$hash.json").also { it.parentFile?.mkdirs() })
    }
    private fun readCache(name: String): Pair<List<RemoteLibraryEntry>, Long> = runCatching {
        val data = org.json.JSONObject(cache(name).openRead().bufferedReader().use { it.readText() })
        val list = data.getJSONArray("entries")
        (0 until list.length()).map { i -> val v = list.getJSONObject(i)
            RemoteLibraryEntry(v.getString("id"),v.getString("title"),v.optString("poster").takeIf { it.isNotBlank() && it != "null" },
                v.getInt("progress"),v.optInt("total").takeIf { it > 0 },v.getString("status"),v.optInt("year").takeIf { it > 0 })
        } to data.getLong("updatedAt")
    }.getOrDefault(emptyList<RemoteLibraryEntry>() to 0L)
    private fun writeCache(name: String, entries: List<RemoteLibraryEntry>, at: Long) {
        val json = org.json.JSONObject().put("updatedAt",at).put("entries",org.json.JSONArray(entries.map {
            org.json.JSONObject().put("id",it.id).put("title",it.title).put("poster",it.poster).put("progress",it.progress)
                .put("total",it.total).put("status",it.status).put("year",it.year)
        }))
        val file = cache(name); val output = file.startWrite()
        try { output.write(json.toString().toByteArray()); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
}
