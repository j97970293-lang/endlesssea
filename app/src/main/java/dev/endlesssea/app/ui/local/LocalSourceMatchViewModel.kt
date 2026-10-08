package dev.endlesssea.app.ui.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.core.util.FileNames
import dev.endlesssea.data.db.EsDatabase
import dev.endlesssea.data.db.MediaEntity
import dev.endlesssea.data.db.EpisodeEntity
import dev.endlesssea.extensions.api.model.*
import dev.endlesssea.extensions.loader.ExtensionRegistry
import dev.endlesssea.app.local.*
import dev.endlesssea.app.ui.library.LocalVideoUi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

internal data class LocalSourceHit(val source: String, val sourceName: String, val item: SearchItem)
internal data class LocalMatchPreview(val hit: LocalSourceHit, val details: MediaDetails)
internal data class LocalSourceMatchState(
    val busy: Boolean = false, val hits: List<LocalSourceHit> = emptyList(),
    val preview: LocalMatchPreview? = null, val message: String? = null,
)

/** Metadata association is explicit and local only: no tracker update, no file rename, no video URL import. */
@HiltViewModel
internal class LocalSourceMatchViewModel @Inject constructor(
    private val registry: ExtensionRegistry,
    private val database: EsDatabase,
) : ViewModel() {
    private val _state = MutableStateFlow(LocalSourceMatchState())
    val state = _state.asStateFlow()
    private var request: Job? = null

    fun search(query: String) {
        if (query.isBlank()) return
        request?.cancel()
        request = viewModelScope.launch {
            _state.value = LocalSourceMatchState(busy = true)
            try {
                val hits = withTimeout(45_000) { withContext(Dispatchers.IO) {
                    val gate = Semaphore(3)
                    coroutineScope {
                        registry.enabledExtensions().map { (_, ext) -> async {
                            gate.withPermit {
                                try {
                                    withTimeout(15_000) {
                                        ext.search(query.trim(), page = 1, filters = FilterSet()).items.take(30)
                                            .map { LocalSourceHit(ext.info.id, ext.info.name, it) }
                                    }
                                } catch (_: TimeoutCancellationException) { emptyList() }
                                catch (e: CancellationException) { throw e }
                                catch (_: Exception) { emptyList() }
                            }
                        } }.awaitAll().flatten().distinctBy { it.source to it.item.url }
                    }
                } }
                _state.value = LocalSourceMatchState(hits = hits,
                    message = if (hits.isEmpty()) "Aucun résultat : vérifiez le titre et vos extensions activées." else null)
            } catch (_: TimeoutCancellationException) { _state.value = LocalSourceMatchState(message = "Recherche trop longue. Réessayez.") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = LocalSourceMatchState(message = "Recherche indisponible. Vérifiez les sources et la connexion.") }
        }
    }

    fun preview(hit: LocalSourceHit) {
        request?.cancel()
        request = viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, preview = null, message = null)
            try {
                val details = withTimeout(30_000) { withContext(Dispatchers.IO) { registry.instance(hit.source).load(hit.item.url) } }
                _state.value = _state.value.copy(busy = false, preview = LocalMatchPreview(hit, details))
            } catch (_: TimeoutCancellationException) { _state.value = _state.value.copy(busy = false, message = "La fiche ne répond pas.") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = _state.value.copy(busy = false, message = "Impossible de charger cette fiche.") }
        }
    }

    fun pairs(preview: LocalMatchPreview, files: List<LocalVideoUi>, seasonForUnnumberedFiles: Int?): List<Pair<LocalVideoUi, Episode>> =
        matchLocalEpisodes(files, preview.details.seasons.flatMap { season -> season.episodes.map { it.copy(season = it.season ?: season.number) } },
            localKey = { file ->
                val season = LocalVideos.episodeSeason(file.name) ?: seasonForUnnumberedFiles
                val number = playbackEpisodeOrder(file.name)
                if (season != null && number != null) EpisodeMatchKey(season, number) else null
            },
            remoteKey = { episode -> onlineEpisodeKey(episode.season, episode.number) })

    fun apply(folder: String, files: List<LocalVideoUi>, fallbackSeason: Int?, importEpisodes: Boolean, done: () -> Unit) {
        if (_state.value.busy) return
        val preview = _state.value.preview ?: return
        val pairs = if (importEpisodes) pairs(preview, files, fallbackSeason) else emptyList()
        request = viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            try {
                val details = preview.details
                val id = LocalMediaIds.series(folder)
                database.withTransaction {
                    val previous = database.mediaDao().byId(id)
                    val oldIds = org.json.JSONObject(previous?.externalIdsJson ?: "{}")
                    val preserveManual = previous != null && (!oldIds.has("local_source") || oldIds.optBoolean("local_manual_meta"))
                    val fileMeta = LocalVideos.seriesMeta[folder]
                    database.mediaDao().upsertAll(listOf(MediaEntity(
                        id = id, extensionId = "local", type = details.type.name,
                        title = details.title, titleKey = FileNames.normalizedKey(details.title),
                        synopsis = if (preserveManual) previous?.synopsis ?: details.synopsis else details.synopsis, posterUrl = details.posterUrl, bannerUrl = details.bannerUrl,
                        year = details.year, genresJson = if (preserveManual && previous?.genresJson != "[]") previous!!.genresJson else org.json.JSONArray(details.genres).toString(),
                        studiosJson = if (preserveManual && previous?.studiosJson != "[]") previous!!.studiosJson else org.json.JSONArray(details.studios).toString(), episodeCount = details.episodeCount,
                        externalIdsJson = org.json.JSONObject(details.externalIds).put("local_source", preview.hit.source)
                            .put("local_source_key", preview.hit.item.url).put("local_manual_meta", preserveManual).toString(),
                        customTitle = previous?.customTitle ?: previous?.title?.takeIf { preserveManual } ?: fileMeta?.title,
                        customCoverUri = previous?.customCoverUri ?: fileMeta?.coverUri,
                    )))
                    database.episodeDao().upsertAll(pairs.map { (file, ep) ->
                        EpisodeEntity(id = file.uri, mediaId = id, season = ep.season, number = ep.number,
                            title = ep.title, thumbnailUrl = ep.thumbnailUrl,
                            durationMs = file.durationMs ?: ep.durationMs,
                            data = org.json.JSONObject().put("source_episode_id", ep.id).toString())
                    })
                }
                _state.value = _state.value.copy(busy = false, message = "Fiche associée · ${pairs.size} épisode(s) reconnus")
                done()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.value = _state.value.copy(busy = false, message = "Association non enregistrée. Réessayez.") }
        }
    }

    fun dismiss() { request?.cancel(); _state.value = LocalSourceMatchState() }
}
