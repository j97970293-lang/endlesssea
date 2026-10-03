package dev.endlesssea.app.ui.details

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.endlesssea.core.util.FileNames
import dev.endlesssea.data.db.DownloadTaskEntity
import dev.endlesssea.data.db.EpisodeDao
import dev.endlesssea.data.db.EpisodeEntity
import dev.endlesssea.data.db.LibraryDao
import dev.endlesssea.data.db.LibraryEntity
import dev.endlesssea.data.db.MediaDao
import dev.endlesssea.data.db.MediaEntity
import dev.endlesssea.downloader.DownloadEngine
import dev.endlesssea.extensions.api.model.Episode
import dev.endlesssea.extensions.api.model.LinkRequest
import dev.endlesssea.extensions.api.model.MediaDetails
import dev.endlesssea.extensions.api.model.VideoLink
import dev.endlesssea.extensions.loader.ExtensionRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DetailsUiState(
    val loading: Boolean = true,
    val details: MediaDetails? = null,
    val episodes: List<Episode> = emptyList(),
    val inLibrary: Boolean = false,
    val favorite: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    // Liens déjà résolus pour l'épisode demandé (clé = id d'épisode)
    val linksByEpisode: Map<String, List<VideoLink>> = emptyMap(),
    val linksLoadingEpisode: String? = null,
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val registry: ExtensionRegistry,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao,
    private val libraryDao: LibraryDao,
    private val downloads: DownloadEngine,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val mediaId: String = checkNotNull(savedStateHandle["id"])

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState

    /** Paquet d'extension associé (préfixe avant le premier « : »). */
    private val extensionId = mediaId.substringBefore(":")
    private val mediaKey = mediaId.substringAfter(":", missingDelimiterValue = mediaId)

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        val remote = runCatching {
            val ext = registry.instance(extensionId)
            ext.load(mediaKey)
        }
        remote.onSuccess { details ->
            cacheLocally(details)
            val episodes = details.seasons.flatMap { it.episodes }
            _uiState.value = _uiState.value.copy(
                loading = false, details = details, episodes = episodes,
            )
            refreshLibraryFlags()
        }
        remote.onFailure { e ->
            // Repli sur le cache local (hors-ligne)
            val cached = mediaDao.byId(mediaId)
            if (cached != null) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    details = cached.toDetails(),
                    error = "Hors-ligne : cache local affiché (${e.message ?: ""})".trim(),
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = e.message ?: "Impossible de charger cette fiche",
                )
            }
            refreshLibraryFlags()
        }
    }

    private suspend fun cacheLocally(details: MediaDetails) = withContext(Dispatchers.IO) {
        mediaDao.upsertAll(listOf(details.toEntity()))
        val episodes = details.seasons.flatMap { season ->
            season.episodes.map { it.toEntity(mediaId) }
        }
        if (episodes.isNotEmpty()) episodeDao.upsertAll(episodes)
    }

    private fun refreshLibraryFlags() = viewModelScope.launch {
        val inLib = libraryDao.contains(mediaId)
        val fav = libraryDao.observeFavorites().first().any { it.mediaId == mediaId }
        _uiState.value = _uiState.value.copy(inLibrary = inLib, favorite = fav)
    }

    fun toggleLibrary(category: String) = viewModelScope.launch {
        if (_uiState.value.inLibrary) {
            libraryDao.remove(mediaId)
        } else {
            libraryDao.upsert(LibraryEntity(mediaId = mediaId, category = category))
        }
        _uiState.value = _uiState.value.copy(
            inLibrary = !_uiState.value.inLibrary, favorite = if (_uiState.value.inLibrary) false else _uiState.value.favorite,
            message = if (_uiState.value.inLibrary) "Retiré de la bibliothèque" else "Ajouté à la bibliothèque",
        )
    }

    fun toggleFavorite() = viewModelScope.launch {
        val fav = !_uiState.value.favorite
        libraryDao.upsert(
            LibraryEntity(
                mediaId = mediaId,
                category = _uiState.value.details?.type?.name ?: "ANIME",
                favorite = fav,
            ),
        )
        _uiState.value = _uiState.value.copy(
            favorite = fav, inLibrary = true,
            message = if (fav) "Ajouté aux favoris" else "Retiré des favoris",
        )
    }

    /** Résout les liens d'un épisode (serveurs × qualités, spec §14) avec mise en cache mémoire. */
    fun loadLinks(episode: Episode, onDone: (List<VideoLink>) -> Unit = {}) = viewModelScope.launch {
        _uiState.value.linksByEpisode[episode.id]?.let { onDone(it); return@launch }
        _uiState.value = _uiState.value.copy(linksLoadingEpisode = episode.id)
        val links = runCatching {
            registry.instance(extensionId).loadLinks(LinkRequest(episode = episode, mediaId = mediaId))
        }.getOrElse { emptyList() }
        if (links.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                linksLoadingEpisode = null,
                message = "Aucun lien trouvé pour cet épisode",
            )
        } else {
            _uiState.value = _uiState.value.copy(
                linksLoadingEpisode = null,
                linksByEpisode = _uiState.value.linksByEpisode + (episode.id to links),
            )
            onDone(links)
        }
    }

    /** Lecture : met les liens dans le canal mémoire, puis [onReady] lance PlayerActivity. */
    fun playEpisode(episode: Episode, startIndex: Int = 0, onReady: () -> Unit = {}) = viewModelScope.launch {
        val existing = _uiState.value.linksByEpisode[episode.id]
        if (existing != null) {
            dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
                title = buildEpisodeTitle(episode), mediaId = mediaId, episodeId = episode.id,
                links = existing, startIndex = startIndex,
            )
            onReady()
            return@launch
        }
        loadLinks(episode) { links ->
            dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
                title = buildEpisodeTitle(episode), mediaId = mediaId, episodeId = episode.id,
                links = links, startIndex = startIndex,
            )
            onReady()
        }
    }

    /** Téléchargement en un clic : met en file le lien choisi (spec §14). */
    fun enqueue(episode: Episode, link: VideoLink) = viewModelScope.launch {
        val title = buildEpisodeTitle(episode)
        val quality = link.quality.name
        val fileName = FileNames.sanitize(
            "$title [$quality]${if (link.subtitles.isNotEmpty()) " [subs]" else ""}",
        ) + extensionFor(link)
        val dir = File(context.getExternalFilesDir(null), "EndlessSea").apply { mkdirs() }
        val task = DownloadTaskEntity(
            id = "dl-${System.currentTimeMillis()}-${(0..999).random()}",
            mediaId = mediaId, episodeId = episode.id,
            url = link.url,
            headersJson = if (link.headers.isEmpty()) "{}" else
                link.headers.entries.joinToString(",", "{", "}") { (k, v) ->
                    "\"${k.replace("\"", "")}\":\"${v.replace("\"", "'")}\""
                },
            server = link.server, quality = quality,
            streamType = link.streamType.name,
            targetUri = File(dir, fileName).toURI().toString(),
            fileName = fileName,
            displayPath = "EndlessSea/$fileName",
            status = "QUEUED",
        )
        downloads.enqueue(task)
        _uiState.value = _uiState.value.copy(message = "Téléchargement ajouté : $fileName")
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }

    private fun buildEpisodeTitle(episode: Episode): String {
        val base = _uiState.value.details?.title ?: mediaKey
        val e = "E${episode.number.toInt()}"
        val s = episode.season?.let { "S$it:" } ?: ""
        return "$base $s$e"
    }

    private fun extensionFor(link: VideoLink): String = when (link.streamType) {
        dev.endlesssea.extensions.api.model.StreamType.HLS -> ".mp4"   // muxé après assemblage
        dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE ->
            link.url.substringAfterLast('.', "").let { if (it.length in 2..4) ".$it" else ".mp4" }
        else -> ".mp4"
    }

    private fun MediaDetails.toEntity() = MediaEntity(
        id = mediaId, extensionId = extensionId, type = type.name,
        title = title, titleKey = FileNames.normalizedKey(title),
        synopsis = synopsis, posterUrl = posterUrl, bannerUrl = bannerUrl,
        year = year, status = status.name, episodeCount = episodeCount, durationMin = durationMin,
        genresJson = genres.joinToString(",", "[", "]") { "\"${it.replace("\"", "'")}\"" },
        altTitlesJson = altTitles.joinToString(",", "[", "]") { "\"${it.replace("\"", "'")}\"" },
        studiosJson = studios.joinToString(",", "[", "]") { "\"${it.replace("\"", "'")}\"" },
    )

    private fun Episode.toEntity(parentMediaId: String) = EpisodeEntity(
        id = id, mediaId = parentMediaId, season = season, number = number,
        title = title, thumbnailUrl = thumbnailUrl, durationMs = durationMs, data = data,
    )

    private fun MediaEntity.toDetails() = MediaDetails(
        id = mediaId, url = mediaKey, title = title, synopsis = synopsis,
        posterUrl = posterUrl, bannerUrl = bannerUrl,
        type = runCatching { dev.endlesssea.extensions.api.model.MediaType.valueOf(type) }
            .getOrDefault(dev.endlesssea.extensions.api.model.MediaType.ANIME),
        year = year, episodeCount = episodeCount, durationMin = durationMin,
        genres = genresJson.removeSurrounding("[", "]").split(",")
            .map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() },
    )
}
