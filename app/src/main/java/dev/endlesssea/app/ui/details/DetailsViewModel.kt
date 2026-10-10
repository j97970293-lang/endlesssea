package dev.endlesssea.app.ui.details

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.endlesssea.app.di.MediaPlaybackPreference
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** §29 Watchlist : statuts de suivi (chaînés aux chips de la fiche + filtre bibliothèque). */
val WATCH_STATUSES = listOf("NONE", "WISHLIST", "WATCHING", "COMPLETED", "DROPPED")

fun watchStatusLabel(status: String) = when (status) {
    "WISHLIST" -> "À regarder"
    "WATCHING" -> "En cours"
    "COMPLETED" -> "Terminé"
    "DROPPED" -> "Abandonné"
    else -> "Sans statut"
}

/** Ligne « Sur l'appareil » §hors-ligne : fichier téléchargé disponible sur cette fiche. */
data class DeviceFileUi(
    val id: String,
    val label: String,
    val sizeBytes: Long,
    val targetUri: String,
    /** §hors-ligne : épisode correspondant, pour marquer la liste d'épisodes. */
    val episodeId: String? = null,
    /** Chemin lisible (« downloads/Source/Série ») affiché sous le titre. */
    val location: String = "",
    val managedDownload: Boolean = true,
)

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
    /** « Tout télécharger » en cours (sélection auto meilleure qualité directe). */
    val batchRunning: Boolean = false,
    /** « Continuer » : dernier épisode commencé sur cette fiche (historique local). */
    val resumeEpisodeId: String? = null,
    val resumeLabel: String? = null,
    /** Statut watchlist §29 : NONE / WISHLIST / WATCHING / COMPLETED / DROPPED. */
    val libraryStatus: String = "NONE",
    /** §hors-ligne : fichiers présents sur l'appareil pour cette fiche. */
    val deviceFiles: List<DeviceFileUi> = emptyList(),
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val registry: ExtensionRegistry,
    private val localDetails: dev.endlesssea.app.local.LocalDetailsRepository,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao,
    private val libraryDao: LibraryDao,
    private val historyDao: dev.endlesssea.data.db.WatchHistoryDao,
    private val downloads: DownloadEngine,
    private val downloadsDao: dev.endlesssea.data.db.DownloadsDao,
    /** §suivi (conversation 11) : comptes AniList / MAL / Shikimori / TMDB. */
    private val trackers: dev.endlesssea.app.tracking.TrackerRepository,
    private val prefs: dev.endlesssea.app.di.AppPrefs,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val mediaId: String = savedStateHandle.get<String>("id")
        ?: dev.endlesssea.app.local.LocalMediaIds.series(checkNotNull(savedStateHandle.get<String>("folder")))
    val isLocal: Boolean = mediaId.startsWith("local:")
    val localFolder: String? = mediaId.removePrefix("local:").takeIf { isLocal }
    var localFiles: List<dev.endlesssea.app.ui.library.LocalVideoUi> = emptyList()
        private set
    private var loadJob: kotlinx.coroutines.Job? = null

    private val _uiState = MutableStateFlow(DetailsUiState())

    /** §glisser-serveurs : ordre de priorité persistant des serveurs. */
    /** §progression-immersive : épisodes terminés / total (carte « Watching progress »). */
    val watchedCount = MutableStateFlow(0)
    val resumeEpisodeId = MutableStateFlow<String?>(null)

    /** §use-poster-color : teinte émotionnelle extraite de l'affiche (Réglages → Thème). */
    val usePosterColor = prefs.usePosterColor

    val serverOrder = prefs.serverOrder
    val preferredAudioLanguage = prefs.preferredAudioLang
    val preferredPlaybackQuality = prefs.preferredPlaybackQuality
    private val _mediaPlaybackPreference = MutableStateFlow(prefs.mediaPlaybackPreference(mediaId))
    val mediaPlaybackPreference: StateFlow<MediaPlaybackPreference?> = _mediaPlaybackPreference
    fun saveServerOrder(order: List<String>) = prefs.setServerOrder(order)
    val uiState: StateFlow<DetailsUiState> = _uiState

    /** Paquet d'extension associé (préfixe avant le premier « : »). */
    private val extensionId = mediaId.substringBefore(":")
    private val mediaKey = mediaId.substringAfter(":", missingDelimiterValue = mediaId)

    private val _trackerLink = MutableStateFlow<dev.endlesssea.data.db.TrackerLinkEntity?>(null)
    init {
        load()
        viewModelScope.launch { trackers.links.collect { list -> _trackerLink.value = list.firstOrNull { it.mediaId == mediaId } } }
    }

    fun load(): kotlinx.coroutines.Job {
        loadJob?.cancel()
        return viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        if (isLocal) {
            try {
                val catalog = localDetails.load(checkNotNull(localFolder))
                localFiles = catalog.files
                val episodes = catalog.files.map { dev.endlesssea.app.local.localEpisode(it) }
                _uiState.value = _uiState.value.copy(loading = false, details = catalog.media.toDetails(), episodes = episodes,
                    deviceFiles = catalog.files.map { DeviceFileUi(id = it.uri, label = it.displayName, sizeBytes = it.sizeBytes,
                        targetUri = it.uri, episodeId = it.uri, location = it.folderName, managedDownload = false) })
                refreshLibraryFlags(); refreshResume(); refreshTracker()
                watchedCount.value = historyDao.watchedCount(mediaId)
                resumeEpisodeId.value = historyDao.resumeForMedia(mediaId)?.episodeId
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _uiState.value = _uiState.value.copy(loading = false, error = "Dossier inaccessible : ${e.message}") }
            return@launch
        }
        // §transition-fiche : contenu local AFFICHÉ TOUT DE SUITE (titre, affiche, épisodes
        // déjà vus, fichiers sur l'appareil) puis la source rafraîchit par-dessus.
        mediaDao.byId(mediaId)?.let { cached ->
            if (_uiState.value.details == null) {
                _uiState.value = _uiState.value.copy(details = cached.toDetails())
                refreshLibraryFlags()
            }
        }
        // §hors-ligne : épisodes déjà en cache Room (la fiche n'apparaît jamais vide hors-ligne)
        if (_uiState.value.episodes.isEmpty()) {
            val cachedEpisodes = episodeDao.ofMedia(mediaId).first()
            if (cachedEpisodes.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    episodes = cachedEpisodes.map { it.toEpisode() },
                )
            }
        }
        refreshDeviceFiles()
        // §suivi : rattachement + services connectés (bloc « Suivi » de la fiche)
        refreshTracker()
        // §progression-immersive : compteur d'épisodes terminés (carte « Watching progress »)
        viewModelScope.launch {
            watchedCount.value = runCatching { historyDao.watchedCount(mediaId) }.getOrDefault(0)
            resumeEpisodeId.value = runCatching { historyDao.resumeForMedia(mediaId)?.episodeId }.getOrNull()
        }
        val remote = runCatching {
            val ext = registry.instance(extensionId)
            ext.load(mediaKey)
        }
        remote.onSuccess { details ->
            cacheLocally(details)
            val downloadedIds = downloadsDao.completedForMedia(mediaId).mapNotNull { it.episodeId }.toSet()
            val episodes = (details.seasons.flatMap { it.episodes } +
                episodeDao.ofMedia(mediaId).first().filter { it.id in downloadedIds }.map { it.toEpisode() })
                .distinctBy { it.id }.sortedWith(compareBy({ it.season ?: 0 }, { it.number }))
            val stored = mediaDao.byId(mediaId)
            val metadataPinned = stored?.externalIdsJson?.let { org.json.JSONObject(it).has("metadata_provider") } == true
            _uiState.value = _uiState.value.copy(
                loading = false, details = details.copy(title = stored?.customTitle ?: details.title,
                    posterUrl = stored?.customCoverUri ?: details.posterUrl,
                    bannerUrl = if (metadataPinned) stored?.bannerUrl else details.bannerUrl,
                    synopsis = if (metadataPinned) stored?.synopsis else details.synopsis,
                    genres = if (metadataPinned) stored?.toDetails()?.genres.orEmpty() else details.genres,
                    year = if (metadataPinned) stored?.year else details.year), episodes = episodes,
            )
            refreshLibraryFlags()
            refreshResume()
            refreshDeviceFiles()
            // §bandes-annonces (conversation 11) : la source ne fournit pas
            // toujours d'affiche ni de bande-annonce — TMDB complète SI une clé
            // est enregistrée (jamais d'appel réseau sans compte connecté).
            enrichFromTmdb(details)
        }
        remote.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
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
        }.also { loadJob = it }
    }

    private suspend fun cacheLocally(details: MediaDetails) = withContext(Dispatchers.IO) {
        val previous = mediaDao.byId(mediaId)
        val pinned = previous?.externalIdsJson?.let { org.json.JSONObject(it).has("metadata_provider") } == true
        val incoming = details.toEntity()
        mediaDao.upsertAll(listOf(incoming.copy(customTitle = previous?.customTitle,
            customCoverUri = previous?.customCoverUri, externalIdsJson = previous?.externalIdsJson ?: "{}",
            bannerUrl = if (pinned) previous?.bannerUrl else incoming.bannerUrl,
            synopsis = if (pinned) previous?.synopsis else incoming.synopsis,
            genresJson = if (pinned) previous!!.genresJson else incoming.genresJson,
            year = if (pinned) previous?.year else incoming.year)))
        val episodes = details.seasons.flatMap { season ->
            season.episodes.map { it.toEntity(mediaId) }
        }
        if (episodes.isNotEmpty()) episodeDao.upsertAll(episodes)
    }

    /** §hors-ligne : fichiers téléchargés affichés sur la fiche (lisibles sans réseau). */
    fun refreshDeviceFiles() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        if (isLocal) return@launch
        val list = downloadsDao.completedForMedia(mediaId).mapNotNull { t ->
            if (!dev.endlesssea.app.local.DownloadLocator.exists(context, t.targetUri)) return@mapNotNull null
            DeviceFileUi(
                id = t.id,
                label = listOfNotNull(
                    t.fileName.removeSuffix(".part").take(60),
                    if (t.totalBytes > 0) formatBytes(t.totalBytes) else null,
                ).joinToString(" · "),
                sizeBytes = t.totalBytes,
                targetUri = t.targetUri,
                episodeId = t.episodeId,
                location = t.displayPath.substringBeforeLast('/', ""),
            )
        }
        _uiState.value = _uiState.value.copy(deviceFiles = list)
        if (list.isNotEmpty()) {
            dev.endlesssea.app.ui.components.DownloadedRegistry.add(mediaId)
        } else {
            dev.endlesssea.app.ui.components.DownloadedRegistry.remove(mediaId)
        }
    }

    private fun markersForEpisode(episodeId: String): dev.endlesssea.app.ui.player.PlayerLaunchStore.SkipMarkers {
        val local = localFiles.firstOrNull { it.uri == episodeId }
        if (local != null) return dev.endlesssea.app.ui.player.PlayerLaunchStore.SkipMarkers(
            local.introStartSec, local.introEndSec, local.outroStartSec,
        )
        val stored = prefs.localFileMeta(episodeId)
        return dev.endlesssea.app.ui.player.PlayerLaunchStore.SkipMarkers(
            stored.introStartSec, stored.introEndSec, stored.outroStartSec,
        )
    }

    /** §deplacer-téléchargement : lecture d'un fichier local (SAF supporté). */
    fun playDeviceFile(f: DeviceFileUi, onReady: () -> Unit) {
        // §emplacement : si le dossier de téléchargement a changé, on retrouve
        // le fichier par son nom dans les emplacements connus.
        val roots = listOfNotNull(prefs.storageRoot.value) + prefs.storageHistory.value
        val playable = dev.endlesssea.app.local.DownloadLocator.resolve(
            context, f.targetUri, f.label.substringBefore(" · "), roots,
        ) ?: f.targetUri
        val store = dev.endlesssea.app.ui.player.PlayerLaunchStore
        // A direct tap on an offline file must build its own queue, not inherit another series.
        if (store.queue.none { it.mediaId == mediaId && it.episodeId == (f.episodeId ?: f.id) }) {
            store.resolver = null
            val queue = _uiState.value.deviceFiles.map { file ->
                val ep = _uiState.value.episodes.firstOrNull { it.id == file.episodeId }
                store.let { dev.endlesssea.app.ui.player.PlayerLaunchStore.QueueItem(
                    title = file.label, episodeId = file.episodeId ?: file.id, mediaId = mediaId,
                    episodeNumber = ep?.number, season = ep?.season, downloaded = true,
                    markers = markersForEpisode(file.episodeId ?: file.id),
                    links = listOf(VideoLink(url = file.targetUri,
                        streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                        quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN, server = "Téléchargé")),
                ) }
            }.sortedWith(compareBy({ it.season ?: 0 }, { it.episodeNumber ?: Float.MAX_VALUE }, { it.title }))
            store.setQueue(queue, queue.indexOfFirst { it.episodeId == (f.episodeId ?: f.id) })
        }
        dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
            title = _uiState.value.episodes.firstOrNull { it.id == f.episodeId }?.let { buildEpisodeTitle(it) }
                ?: f.label.substringBefore(" · "),
            mediaId = mediaId, episodeId = f.episodeId ?: f.id,
            links = listOf(
                dev.endlesssea.extensions.api.model.VideoLink(
                    url = playable,
                    streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                    quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN,
                    server = "Sur l'appareil",
                ),
            ),
            startIndex = 0,
            // Resolved episode markers include the folder defaults for local files.
            markers = markersForEpisode(f.episodeId ?: playable),
        )
        onReady()
    }

    /** §reprise-fiche : proposer la reprise en ouvrant la fiche. */
    val resumePrompt: StateFlow<Boolean> = prefs.resumePrompt

    /** §metadonnees-hors-ligne : details.json + cover.jpg façon Aniyomi. */
    private fun writeOfflineMetadata(
        seriesDirs: List<String>,
        sourceName: String,
        seriesName: String,
    ) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val d = _uiState.value.details
        val json = buildString {
            append("{\n")
            append("  \"title\": ").append(jsonStr(d?.title ?: seriesName)).append(",\n")
            append("  \"author\": ").append(jsonStr(sourceName)).append(",\n")
            append("  \"description\": ").append(jsonStr(d?.synopsis ?: "")).append(",\n")
            append("  \"genre\": [")
            append(d?.genres.orEmpty().joinToString(", ") { g -> jsonStr(g) })
            append("],\n")
            append("  \"status\": \"0\",\n")
            append("  \"poster\": ").append(jsonStr(d?.posterUrl ?: "")).append("\n")
            append("}\n")
        }
        dev.endlesssea.app.local.DownloadStorage.writeText(
            context, prefs.storageRoot.value, seriesDirs, "details.json", json,
        )
        dev.endlesssea.app.local.DownloadStorage.writeCover(
            context, prefs.storageRoot.value, seriesDirs, d?.posterUrl,
        )
    }

    private fun jsonStr(v: String): String =
        "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""

    private fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> "%.1f Go".format(b.toDouble() / (1L shl 30))
        b >= 1L shl 20 -> "%.1f Mo".format(b.toDouble() / (1L shl 20))
        b >= 1L shl 10 -> "%.1f Ko".format(b.toDouble() / (1L shl 10))
        else -> "$b o"
    }

    /** Met à jour l'état « Continuer » depuis l'historique local de cette fiche. */
    private fun refreshResume() = viewModelScope.launch {
        val resume = historyDao.resumeForMedia(mediaId)
        val ep = _uiState.value.episodes.firstOrNull { it.id == resume?.episodeId }
        _uiState.value = _uiState.value.copy(
            resumeEpisodeId = if (ep != null) resume?.episodeId else null,
            resumeLabel = if (ep != null && resume != null) {
                val mm = resume.positionMs / 60_000
                val ss = (resume.positionMs / 1000) % 60
                "▶ Continuer — ${if (ep.number.isFinite()) "Ép. ${ep.number.toString().removeSuffix(".0")}" else ep.title.orEmpty()} · ${mm}:${"%02d".format(ss)}"
            } else null,
        )
    }

    /** Joue l'épisode « Continuer » (la position est restaurée par la reprise auto du lecteur). */
    fun playResume(onReady: () -> Unit) {
        val id = _uiState.value.resumeEpisodeId ?: return
        _uiState.value.episodes.firstOrNull { it.id == id }
            ?.let { playEpisode(it, onReady = onReady) }
    }

    private fun refreshLibraryFlags() = viewModelScope.launch {
        val inLib = libraryDao.contains(mediaId)
        val fav = libraryDao.observeFavorites().first().any { it.mediaId == mediaId }
        val status = if (inLib) libraryDao.byMediaId(mediaId)?.status ?: "NONE" else "NONE"
        _uiState.value = _uiState.value.copy(inLibrary = inLib, favorite = fav, libraryStatus = status)
    }

    /** §29 : change le statut watchlist de la fiche (impose l'appartenance bibliothèque). */
    fun setLibraryStatus(status: String) = viewModelScope.launch {
        if (status !in WATCH_STATUSES) return@launch
        if (!_uiState.value.inLibrary) {
            libraryDao.upsert(
                LibraryEntity(mediaId = mediaId, category = _uiState.value.details?.type?.name ?: "ANIME"),
            )
            _uiState.value = _uiState.value.copy(inLibrary = true)
        }
        libraryDao.setStatus(mediaId, status)
        _uiState.value = _uiState.value.copy(libraryStatus = status)
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

    // ------------------------------------------------ §suivi (conversation 11)

    /**
     * §bandes-annonces (conversation 11) : complète la fiche avec TMDB — affiche,
     * bannière et bande-annonce manquantes. Aucun appel n'est fait sans clé TMDB
     * enregistrée, et rien de ce que fournit l'extension n'est écrasé.
     */
    private fun enrichFromTmdb(details: dev.endlesssea.extensions.api.model.MediaDetails) {
        if (!prefs.enrichWithTmdb.value) return
        if (details.posterUrl != null && details.bannerUrl != null && details.trailerUrl != null) return
        viewModelScope.launch {
            val hit = trackers.searchTmdb(details.title).firstOrNull() ?: return@launch
            val tmdb = trackers.enrich(hit.remoteId) ?: return@launch
            val current = _uiState.value.details ?: return@launch
            // Champs immuables : on reconstruit la fiche (les extensions restent
            // prioritaires — TMDB ne remplit que ce qui manque).
            val enriched = current.copy(
                posterUrl = current.posterUrl ?: tmdb.posterUrl,
                bannerUrl = current.bannerUrl ?: tmdb.bannerUrl,
                synopsis = current.synopsis ?: tmdb.synopsis,
            )
            enriched.trailerUrl = current.trailerUrl ?: tmdb.trailerUrl
            enriched.characters = current.characters
            enriched.rating = current.rating
            enriched.ratingCount = current.ratingCount
            _uiState.value = _uiState.value.copy(details = enriched)
        }
    }

    // ------------------------------------------- §suivi (conversation 11)

    /** Résultats de recherche d'un service (dialogue de rattachement). */
    data class TrackerSearchState(
        val service: String? = null,
        val loading: Boolean = false,
        val hits: List<dev.endlesssea.app.tracking.TrackerSearchHit> = emptyList(),
        val error: String? = null,
    )

    private val _trackerSearch = MutableStateFlow(TrackerSearchState())
    val trackerSearch: StateFlow<TrackerSearchState> = _trackerSearch

    val metadataServices = trackers.accounts.map { accounts ->
        accounts.filter { it.enabled && it.userName.isNotBlank() && it.service in setOf("ANILIST", "TMDB") }.map { it.service }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _metadataSearch = MutableStateFlow(TrackerSearchState())
    val metadataSearch: StateFlow<TrackerSearchState> = _metadataSearch
    private var metadataSearchJob: kotlinx.coroutines.Job? = null
    fun searchMetadata(service: String, query: String) {
        metadataSearchJob?.cancel()
        metadataSearchJob = viewModelScope.launch {
            _metadataSearch.value = TrackerSearchState(service = service, loading = true)
            val title = query.trim().ifBlank { _uiState.value.details?.title.orEmpty() }
            try {
                val hits = trackers.searchChecked(service, title)
                kotlin.coroutines.coroutineContext.ensureActive()
                _metadataSearch.value = TrackerSearchState(service = service, hits = hits)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                _metadataSearch.value = TrackerSearchState(service = service, error = e.message ?: "Recherche indisponible")
            }
        }
    }

    /** Rattachement courant de cette fiche (null = non suivie). */
    val trackerLink: StateFlow<dev.endlesssea.data.db.TrackerLinkEntity?> = _trackerLink

    /** Services connectés et actifs : seuls ceux-là sont proposés au rattachement. */
    private val _connectedServices = MutableStateFlow<List<String>>(emptyList())
    val connectedServices: StateFlow<List<String>> = _connectedServices

    /** Tic de recomposition du bloc « Suivi » (mises à jour manuelles). */
    val trackerTick = MutableStateFlow(0)
    val defaultTrackerService get() = trackers.defaultService

    private fun refreshTracker() = viewModelScope.launch {
        _trackerLink.value = trackers.linkOf(mediaId)
        _connectedServices.value = trackers.accounts.first().filter { it.enabled && it.userName.isNotBlank() && it.service != "TMDB" }
            .map { it.service }
    }

    fun dismissTrackerSearch() { _trackerSearch.value = TrackerSearchState() }

    /** Rattachement manuel : recherche le titre de la fiche sur le service choisi. */
    fun searchTracker(service: String, query: String? = null) = viewModelScope.launch {
        _trackerSearch.value = TrackerSearchState(service = service, loading = true)
        val title = query?.takeIf { it.isNotBlank() } ?: _uiState.value.details?.title
            ?: dev.endlesssea.app.local.LocalNames.pretty(mediaId)
        val hits = trackers.search(service, title)
        _trackerSearch.value = TrackerSearchState(service = service, loading = false, hits = hits)
        if (hits.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                message = "Aucun résultat sur ${dev.endlesssea.app.tracking.TrackerRegistry.LABELS[service] ?: service}",
            )
        }
    }

    fun linkTracker(
        service: String, hit: dev.endlesssea.app.tracking.TrackerSearchHit,
        autoMatch: Boolean = false, season: Int? = null,
    ) {
        viewModelScope.launch {
            val linked = trackers.link(mediaId, service, hit, status = "WATCHING", autoMatchEpisodes = autoMatch, autoMatchSeason = season)
            if (!linked) {
                _uiState.value = _uiState.value.copy(message = "Rattachement annulé : impossible de vérifier la progression distante. Réessayez.")
                return@launch
            }
            _trackerSearch.value = TrackerSearchState()
            refreshTracker()
            trackerTick.value += 1
            _uiState.value = _uiState.value.copy(message = "Rattaché : ${hit.title}")
        }
    }

    /** Explicit metadata import is independent from choosing a progress tracker. */
    fun importTrackerMetadata(service: String, remoteId: String) = viewModelScope.launch {
        try {
            val metadata = trackers.metadata(service, remoteId) ?: error("Métadonnées non fournies par ce service")
            val old = mediaDao.byId(mediaId) ?: error("Fiche absente du cache")
            val ids = org.json.JSONObject(old.externalIdsJson)
            val title = old.customTitle?.takeUnless { it == ids.optString("metadata_import_title") } ?: metadata.title ?: old.customTitle
            val cover = old.customCoverUri?.takeUnless { it == ids.optString("metadata_import_cover") } ?: metadata.posterUrl ?: old.customCoverUri
            val manual = ids.optBoolean("local_manual_meta")
            ids.put("metadata_provider",service).put("metadata_remote_id",remoteId)
                .put("metadata_import_title",metadata.title).put("metadata_import_cover",metadata.posterUrl)
            val imported = old.copy(customTitle = title, customCoverUri = cover,
                synopsis = if (manual) old.synopsis else metadata.synopsis ?: old.synopsis,
                bannerUrl = metadata.bannerUrl ?: old.bannerUrl, year = metadata.year ?: old.year,
                genresJson = if (manual || metadata.genres.isEmpty()) old.genresJson else org.json.JSONArray(metadata.genres).toString(),
                externalIdsJson = ids.toString())
            mediaDao.upsertAll(listOf(imported))
            // No source reload: importing metadata must not wait on the video extension,
            // and must preserve its existing episodes and playback identifiers.
            val display = imported.toDetails()
            _uiState.value = _uiState.value.copy(details = (_uiState.value.details ?: display).copy(
                title = display.title, posterUrl = display.posterUrl, bannerUrl = display.bannerUrl,
                synopsis = display.synopsis, genres = display.genres, year = display.year))
            _trackerSearch.value = TrackerSearchState()
            trackerTick.value += 1
            _uiState.value = _uiState.value.copy(message = "Métadonnées importées ; vos modifications personnelles restent prioritaires. Aucun suivi distant modifié.")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { _uiState.value = _uiState.value.copy(message = "Import impossible : ${e.message}") }
    }

    fun setEpisodeMatching(enabled: Boolean, season: Int?) = viewModelScope.launch {
        trackers.setEpisodeMatching(mediaId, enabled, season)
        refreshTracker()
    }

    /** Action explicite sur l'historique local ; ne modifie pas le compte distant. */
    fun markAllWatched() = markEpisodesWatched(_uiState.value.episodes.map { it.id }.toSet())

    /** Marque une sélection d'épisodes comme vue, sans modifier le tracker distant. */
    fun markEpisodesWatched(episodeIds: Set<String>) = viewModelScope.launch {
        val episodes = _uiState.value.episodes.filter { it.id in episodeIds }
        if (episodes.isEmpty()) return@launch
        historyDao.upsertAll(episodes.map { ep ->
            val old = historyDao.byEpisode(ep.id)
            dev.endlesssea.data.db.WatchHistoryEntity(
                episodeId = ep.id, mediaId = mediaId,
                positionMs = old?.positionMs ?: 0L,
                durationMs = ep.durationMs ?: old?.durationMs ?: 0L, watched = true,
            )
        })
        watchedCount.value = historyDao.watchedCount(mediaId)
        if (resumeEpisodeId.value?.let { it in episodeIds } == true) resumeEpisodeId.value = null
        refreshResume()
        _uiState.value = _uiState.value.copy(message = "${episodes.size} épisode(s) marqué(s) vu(s) dans l'historique local")
    }

    fun unlinkTracker() = viewModelScope.launch {
        trackers.unlink(mediaId)
        refreshTracker()
        trackerTick.value += 1
        _uiState.value = _uiState.value.copy(message = "Rattachement supprimé")
    }

    fun setTrackerProgress(progress: Int) = viewModelScope.launch {
        val sent = trackers.setProgress(mediaId, progress)
        refreshTracker()
        trackerTick.value += 1
        _uiState.value = _uiState.value.copy(message = if (sent) "Progression synchronisée" else "Progression conservée, synchronisation en attente")
    }

    /** Bouton « +1 » : marque l'épisode suivant comme vu (envoi immédiat si possible). */
    fun markNextWatched() = viewModelScope.launch {
        val link = _trackerLink.value
        if (link == null) {
            _uiState.value = _uiState.value.copy(
                message = "Rattache d'abord cette fiche à un service (bloc Suivi).",
            )
            return@launch
        }
        trackers.setProgress(mediaId, link.progress + 1)
        refreshTracker()
        trackerTick.value += 1
        _uiState.value = _uiState.value.copy(message = "Progression : ${link.progress + 1} épisode(s) vu(s)")
    }

    /** Changement de statut : En cours / Terminé / À voir / Abandonné. */
    fun setTrackerStatus(status: String) = viewModelScope.launch {
        val link = _trackerLink.value
        if (link == null) {
            _uiState.value = _uiState.value.copy(message = "Rattache d'abord cette fiche à un service.")
            return@launch
        }
        val progress = if (status == "COMPLETED") {
            link.totalEpisodes.takeIf { it > 0 } ?: link.progress
        } else link.progress
        trackers.setProgress(mediaId, progress, status)
        refreshTracker()
        trackerTick.value += 1
        _uiState.value = _uiState.value.copy(message = "Statut mis à jour")
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
    /**
     * Résout les lecteurs d'un épisode **au fil de l'eau** : chaque serveur prêt
     * est publié immédiatement (`EsExtension.loadLinksFlow`, repli automatique
     * sur `loadLinks` pour les extensions qui ne l'implémentent pas). La feuille
     * « Serveurs & priorité » affiche donc les lecteurs déjà résolus pendant que
     * les plus lents continuent de charger, au lieu d'attendre le dernier
     * timeout de 20 s.
     */
    fun loadLinks(episode: Episode, onDone: (List<VideoLink>) -> Unit = {}) = viewModelScope.launch {
        val cached = _uiState.value.linksByEpisode[episode.id]
        if (_uiState.value.linksLoadingEpisode == episode.id) return@launch // déjà en cours
        if (cached != null) { onDone(cached); return@launch }
        _uiState.value = _uiState.value.copy(linksLoadingEpisode = episode.id)

        val collected = mutableListOf<VideoLink>()
        // §serveurs-bloques : une source qui ne répond jamais laissait la feuille
        // « serveurs » tourner à l'infini. Plafond dur à 90 s, et on garde ce qui
        // est déjà arrivé. L'erreur éventuelle est remontée à l'écran.
        val failure = runCatching {
            withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(90_000) {
                    registry.instance(extensionId)
                        .linksFlowCompat(LinkRequest(episode = episode, mediaId = mediaId))
                        .collect { link ->
                            collected += link
                            // Publication immédiate : la feuille serveurs se remplit en direct.
                            // Préférence VF/VOSTFR appliquée au fur et à mesure.
                            val partial = orderByLangPref(collected.toList())
                            _uiState.value = _uiState.value.copy(
                                linksByEpisode = _uiState.value.linksByEpisode + (episode.id to partial),
                            )
                        }
                }
            }
        }.exceptionOrNull()

        if (collected.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                linksLoadingEpisode = null,
                linksByEpisode = _uiState.value.linksByEpisode - episode.id,
                message = when {
                    failure != null -> "Lecture impossible : " +
                        (failure.message?.take(120) ?: failure::class.java.simpleName)
                    else -> "Cette source n'a renvoyé aucun serveur pour cet épisode"
                },
            )
        } else {
            val ordered = orderByLangPref(collected.toList())
            _uiState.value = _uiState.value.copy(
                linksLoadingEpisode = null,
                linksByEpisode = _uiState.value.linksByEpisode + (episode.id to ordered),
            )
            onDone(ordered)
        }
    }

    /** §fiche-serveurs : priorité des serveurs configurée par l'utilisateur (réglages). */
    fun serverPriority(): List<String> = prefs.serverOrder.value

    /** Apply this media's last explicit choice when the exact combination still exists. */
    private fun orderByLangPref(links: List<VideoLink>): List<VideoLink> = orderPlaybackLinks(
        links = links,
        preferredAudioLanguage = prefs.preferredAudioLang.value,
        serverPriority = prefs.serverOrder.value,
        preferredQuality = prefs.preferredPlaybackQuality.value,
        rememberedPreference = _mediaPlaybackPreference.value,
    )

    private fun rememberPlaybackPreference(link: VideoLink) {
        val server = link.server.trim()
        if (server.isBlank()) return
        val preference = MediaPlaybackPreference(
            server = server,
            qualityPixels = link.quality.pixels.takeIf { it > 0 },
            audioLanguage = link.audioLang.iso,
        )
        prefs.setMediaPlaybackPreference(mediaId, preference)
        _mediaPlaybackPreference.value = prefs.mediaPlaybackPreference(mediaId) ?: preference
        // Any links resolved earlier for this media must also reflect the new choice.
        val current = _uiState.value
        _uiState.value = current.copy(
            linksByEpisode = current.linksByEpisode.mapValues { (_, links) -> orderByLangPref(links) },
        )
    }

    private fun includeSelectedLink(links: List<VideoLink>, selectedLink: VideoLink?): List<VideoLink> =
        if (selectedLink == null || links.any { it == selectedLink }) links else links + selectedLink

    /** Lecture : met les liens dans le canal mémoire, puis [onReady] lance PlayerActivity. */
    /** §hors-ligne-prioritaire : fichier téléchargé correspondant à un épisode. */
    fun downloadedFor(episodeId: String): DeviceFileUi? =
        _uiState.value.deviceFiles.firstOrNull { it.episodeId == episodeId }

    /** Supprime un téléchargement (appui long). */
    fun deleteDeviceFile(f: DeviceFileUi) = viewModelScope.launch {
        if (!deleteDeviceFileNow(f)) {
            _uiState.value = _uiState.value.copy(message = "Suppression impossible : le fichier hors ligne est conservé.")
            return@launch
        }
        if (isLocal) load() else refreshDeviceFiles()
        _uiState.value = _uiState.value.copy(message = if (isLocal) "Fichier local supprimé." else "Fichier hors ligne supprimé ; l'épisode reste disponible depuis sa source.")
    }

    /** Supprime plusieurs téléchargements terminés après une confirmation explicite. */
    fun deleteDeviceFiles(files: List<DeviceFileUi>) = viewModelScope.launch {
        val uniqueFiles = files.filter { it.managedDownload }.distinctBy { it.targetUri }
        if (uniqueFiles.isEmpty()) return@launch
        var removed = 0
        uniqueFiles.forEach { file -> if (deleteDeviceFileNow(file)) removed++ }
        refreshDeviceFiles()
        val failed = uniqueFiles.size - removed
        _uiState.value = _uiState.value.copy(
            message = buildString {
                append("$removed téléchargement(s) supprimé(s)")
                if (failed > 0) append(" · $failed fichier(s) impossible(s) à supprimer")
            },
        )
    }

    private suspend fun deleteDeviceFileNow(f: DeviceFileUi): Boolean {
        val deleted = withContext(Dispatchers.IO) {
            if (!dev.endlesssea.app.local.DownloadLocator.exists(context, f.targetUri)) true
            else runCatching {
                val uri = android.net.Uri.parse(f.targetUri)
                if (uri.scheme == "content") androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.delete() == true
                else java.io.File(uri.path ?: f.targetUri).delete()
            }.getOrDefault(false)
        }
        if (!deleted) return false
        if (f.managedDownload) downloads.cancel(f.id, deleteFiles = true)
        if (isLocal) {
            dev.endlesssea.app.local.LocalLibraryCache.publish(
                dev.endlesssea.app.local.LocalLibraryCache.files.value.filterNot { it.uri == f.targetUri },
            )
        }
        return true
    }

    /** Select offline/online at the moment of the tap, not from a stale badge. */
    fun openEpisode(episode: Episode, onReady: () -> Unit, onChooseServer: () -> Unit) = viewModelScope.launch {
        refreshEpisodeAvailability(episode.id)
        if (downloadedFor(episode.id) != null) playEpisode(episode, onReady = onReady)
        else if (isLocal) _uiState.value = _uiState.value.copy(message = "Fichier local inaccessible. Vérifiez le stockage et ses autorisations.")
        else onChooseServer()
    }

    private suspend fun refreshEpisodeAvailability(id: String) {
        if (isLocal) {
            val exists = withContext(Dispatchers.IO) { dev.endlesssea.app.local.DownloadLocator.exists(context, id) }
            if (!exists) _uiState.value = _uiState.value.copy(deviceFiles = _uiState.value.deviceFiles.filterNot { it.episodeId == id })
            return
        }
        val file = withContext(Dispatchers.IO) {
            dev.endlesssea.app.local.readableDownload(downloadsDao.completedForMedia(mediaId), id,
                { it.episodeId }, { it.targetUri }, { dev.endlesssea.app.local.DownloadLocator.exists(context, it) })
                ?.let { DeviceFileUi(id = it.id, label = it.fileName.removeSuffix(".part"),
                    sizeBytes = it.totalBytes, targetUri = it.targetUri, episodeId = it.episodeId,
                    location = it.displayPath.substringBeforeLast('/', "")) }
        }
        _uiState.value = _uiState.value.copy(deviceFiles =
            _uiState.value.deviceFiles.filterNot { it.episodeId == id } + listOfNotNull(file))
    }

    private suspend fun offlineLinks(episodeId: String): List<VideoLink> = withContext(Dispatchers.IO) {
        if (isLocal) return@withContext if (dev.endlesssea.app.local.DownloadLocator.exists(context, episodeId))
            listOf(VideoLink(url = episodeId, streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN, server = "Hors ligne")) else emptyList()
        val task = dev.endlesssea.app.local.readableDownload(downloadsDao.completedForMedia(mediaId), episodeId,
            { it.episodeId }, { it.targetUri }, { dev.endlesssea.app.local.DownloadLocator.exists(context, it) })
        task?.let { listOf(VideoLink(url = it.targetUri,
            streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
            quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN, server = "Hors ligne")) }.orEmpty()
    }

    fun playEpisode(
        episode: Episode,
        startIndex: Int = 0,
        selectedLink: VideoLink? = null,
        onReady: () -> Unit = {},
    ) = viewModelScope.launch {
        refreshEpisodeAvailability(episode.id)
        if (selectedLink != null && downloadedFor(episode.id) == null) {
            rememberPlaybackPreference(selectedLink)
        }
        // §épisode-suivant : la file = tous les épisodes de la fiche, dans l'ordre ;
        // les liens des voisins sont résolus à la demande par ce résolveur.
        val all = _uiState.value.episodes
        dev.endlesssea.app.ui.player.PlayerLaunchStore.setQueue(
            all.map { ep ->
                val offline = downloadedFor(ep.id)
                val existingLinks = offline?.let { local ->
                    listOf(VideoLink(url = local.targetUri, streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                        quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN, server = "Téléchargement"))
                } ?: _uiState.value.linksByEpisode[ep.id].orEmpty()
                val queuedLinks = if (ep.id == episode.id && offline == null) {
                    includeSelectedLink(existingLinks, selectedLink)
                } else existingLinks
                dev.endlesssea.app.ui.player.PlayerLaunchStore.QueueItem(
                    title = buildEpisodeTitle(ep),
                    episodeId = ep.id,
                    links = queuedLinks,
                    downloaded = offline != null,
                    thumbnailUrl = ep.thumbnailUrl, season = ep.season, episodeNumber = ep.number,
                    durationMs = ep.durationMs ?: 0L, mediaId = mediaId,
                    markers = markersForEpisode(ep.id),
                )
            },
            all.indexOfFirst { it.id == episode.id },
        )
        dev.endlesssea.app.ui.player.PlayerLaunchStore.resolver = { id ->
            val localLinks = offlineLinks(id)
            val ep = _uiState.value.episodes.firstOrNull { it.id == id }
            if (localLinks.isNotEmpty()) {
                localLinks
            } else if (ep == null || isLocal) {
                emptyList()
            } else {
                _uiState.value.linksByEpisode[id] ?: withContext(Dispatchers.IO) {
                    val out = mutableListOf<VideoLink>()
                    runCatching {
                        registry.instance(extensionId)
                            .linksFlowCompat(LinkRequest(episode = ep, mediaId = mediaId))
                            .collect { out += it }
                    }
                    orderByLangPref(out)
                }
            }
        }

        // §hors-ligne-prioritaire : si l'épisode est déjà téléchargé, on lit le
        // FICHIER LOCAL même quand les données mobiles sont actives.
        val local = downloadedFor(episode.id)
        if (local != null) {
            playDeviceFile(local, onReady)
            return@launch
        }

        if (isLocal) {
            _uiState.value = _uiState.value.copy(message = "Fichier local inaccessible. Vérifiez le stockage.")
            return@launch
        }
        val existing = _uiState.value.linksByEpisode[episode.id]
        if (existing != null) {
            val launchLinks = includeSelectedLink(existing, selectedLink)
            val launchIndex = selectedLink?.let(launchLinks::indexOf)?.takeIf { it >= 0 } ?: startIndex
            dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
                title = buildEpisodeTitle(episode), mediaId = mediaId, episodeId = episode.id,
                links = launchLinks, startIndex = launchIndex,
            )
            onReady()
            return@launch
        }
        loadLinks(episode) { links ->
            val launchLinks = includeSelectedLink(links, selectedLink)
            val launchIndex = selectedLink?.let(launchLinks::indexOf)?.takeIf { it >= 0 } ?: startIndex
            dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
                title = buildEpisodeTitle(episode), mediaId = mediaId, episodeId = episode.id,
                links = launchLinks, startIndex = launchIndex,
            )
            onReady()
        }
    }

    /** Téléchargement en un clic : met en file le lien choisi (spec §14). */
    fun enqueue(episode: Episode, link: VideoLink) = viewModelScope.launch {
        val fileName = enqueueAndWait(episode, link)
        _uiState.value = _uiState.value.copy(message = "Téléchargement ajouté : $fileName")
    }

    /**
     * « Tout télécharger » : pour chaque épisode, résout les liens et met en file
     * un fichier direct ou HLS compatible avec les filtres. Les embeds restent
     * réservés à la lecture.
     */
    private suspend fun resolveBatchLinks(episode: Episode): List<VideoLink> {
        _uiState.value.linksByEpisode[episode.id]?.takeIf { it.isNotEmpty() }?.let { return it }
        val links = mutableListOf<VideoLink>()
        kotlinx.coroutines.withTimeout(90_000) {
            withContext(Dispatchers.IO) {
                registry.instance(extensionId).linksFlowCompat(LinkRequest(episode = episode, mediaId = mediaId))
                    .collect { links += it }
            }
        }
        val resolved = links.distinctBy { Triple(it.url, it.audioLang, it.quality) }
        if (resolved.isNotEmpty()) _uiState.value = _uiState.value.copy(
            linksByEpisode = _uiState.value.linksByEpisode + (episode.id to resolved),
        )
        return resolved
    }

    val batchScanProgress = MutableStateFlow<String?>(null)
    private var batchScanJob: kotlinx.coroutines.Job? = null
    fun scanBatchServers() {
        if (batchScanJob?.isActive == true) return
        batchScanJob = viewModelScope.launch {
            var failed = 0
            try {
                val episodes = _uiState.value.episodes
                episodes.forEachIndexed { index, episode ->
                    batchScanProgress.value = "Scan des serveurs : ${index + 1}/${episodes.size}"
                    try { resolveBatchLinks(episode) }
                    catch (e: kotlinx.coroutines.TimeoutCancellationException) { failed++ }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { failed++ }
                }
                if (failed > 0) _uiState.value = _uiState.value.copy(message = "$failed épisode(s) sans réponse pendant le scan")
            } finally { batchScanProgress.value = null }
        }
    }
    fun cancelBatchScan() { batchScanJob?.cancel() }

    fun enqueueAll(
        episodes: List<Episode> = this._uiState.value.episodes,
        serverPriority: List<String> = prefs.serverOrder.value,
        excludedServers: Set<String> = emptySet(),
        language: dev.endlesssea.extensions.api.model.AudioLang? = null,
        quality: dev.endlesssea.extensions.api.model.Quality? = null,
    ) = viewModelScope.launch {
        if (_uiState.value.batchRunning || episodes.isEmpty()) return@launch
        _uiState.value = _uiState.value.copy(batchRunning = true, message = "Résolution des liens…")
        var added = 0; var streamOnly = 0; var failed = 0
        try {
        episodes.forEachIndexed { i, episode ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            _uiState.value = _uiState.value.copy(message = "Résolution des liens… (${i + 1}/${episodes.size})")
            try {
                val links = resolveBatchLinks(episode)
                val best = selectBatchDownload(
                    links, serverPriority, excludedServers, language, quality,
                    preferredQuality = prefs.preferredPlaybackQuality.value,
                    preferredLanguage = prefs.preferredAudioLang.value,
                )
                if (best == null) { streamOnly++; return@forEachIndexed }
                enqueueAndWait(episode, best)
                added++
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) { failed++ }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { failed++ }
        }
        val parts = buildList {
            if (added > 0) add("$added téléchargement${if (added > 1) "s" else ""} ajouté${if (added > 1) "s" else ""}")
            if (streamOnly > 0) add("$streamOnly sans lien correspondant aux filtres")
            if (failed > 0) add("$failed sans réponse de la source")
        }
        _uiState.value = _uiState.value.copy(
            batchRunning = false,
            message = if (parts.isEmpty()) "Aucun fichier téléchargeable trouvé" else parts.joinToString(" · "),
        )
        } finally { _uiState.value = _uiState.value.copy(batchRunning = false) }
    }

    private suspend fun enqueueAndWait(episode: Episode, link: VideoLink): String {
        val quality = link.quality.name
        val details = _uiState.value.details
        val fileName = FileNames.videoName(details?.title ?: mediaKey,
            movie = details?.type == dev.endlesssea.extensions.api.model.MediaType.MOVIE,
            year = details?.year, season = episode.season, episode = episode.number,
            episodeTitle = episode.title, quality = link.quality.takeUnless { it == dev.endlesssea.extensions.api.model.Quality.UNKNOWN }?.label.orEmpty(), language = link.audioLang.name,
            extension = extensionFor(link))
        val sourceName = runCatching { registry.instance(extensionId).info.name }
            .getOrDefault(extensionId)
        val seriesName = _uiState.value.details?.title ?: mediaId
        val relDirs = listOf(
            dev.endlesssea.app.local.DownloadStorage.DOWNLOADS_DIR,
            sourceName, seriesName,
        )
        writeOfflineMetadata(relDirs, sourceName, seriesName)
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
            displayPath = (relDirs + fileName).joinToString("/"),
            status = "QUEUED",
        )
        downloads.enqueue(task)
        startDownloadService()
        return fileName
    }

    /**
     * §service-telechargement (conversation 10) : la file était purement
     * en mémoire — écran éteint, Android pouvait la geler. On remonte le moteur
     * en service au premier plan (wake-lock + notifications) dès le premier
     * épisode mis en file.
     */
    private fun startDownloadService() {
        runCatching {
            dev.endlesssea.downloader.DownloadService.start(context, downloads)
        }.onFailure { e ->
            dev.endlesssea.core.diag.EsLog.e(
                "Download", "service", "Impossible de démarrer le service",
                e.message ?: e.javaClass.simpleName,
            )
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }

    private fun buildEpisodeTitle(episode: Episode): String {
        val base = _uiState.value.details?.title ?: mediaKey
        if (_uiState.value.details?.type == dev.endlesssea.extensions.api.model.MediaType.MOVIE) return base
        val e = if (episode.number.isFinite()) "E${episode.number.toString().removeSuffix(".0")}" else episode.title.orEmpty()
        val s = episode.season?.let { "S$it:" } ?: ""
        return "$base $s$e"
    }

    private fun extensionFor(link: VideoLink): String = when (link.streamType) {
        dev.endlesssea.extensions.api.model.StreamType.HLS -> ".ts"   // segments MPEG-TS assemblés
        dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE ->
            runCatching { java.net.URI(link.url).path.substringAfterLast('.', "").lowercase() }.getOrDefault("")
                .let { if (it in setOf("mp4","mkv","webm","ts","m4v","avi","mov")) ".$it" else ".mp4" }
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

    /** §hors-ligne : re-hydrate un épisode de la base (affichage sans la source). */
    private fun EpisodeEntity.toEpisode() = Episode(
        id = id, number = number, season = season, title = title,
        thumbnailUrl = thumbnailUrl, durationMs = durationMs, data = data,
    )

    private fun MediaEntity.toDetails() = MediaDetails(
        id = mediaId, url = mediaKey, title = customTitle ?: title, synopsis = synopsis,
        posterUrl = customCoverUri ?: posterUrl, bannerUrl = bannerUrl,
        type = runCatching { dev.endlesssea.extensions.api.model.MediaType.valueOf(type) }
            .getOrDefault(dev.endlesssea.extensions.api.model.MediaType.ANIME),
        year = year, episodeCount = episodeCount, durationMin = durationMin,
        studios = runCatching { val a = org.json.JSONArray(studiosJson); (0 until a.length()).map { a.getString(it) } }.getOrDefault(emptyList()),
        genres = genresJson.removeSurrounding("[", "]").split(",")
            .map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() },
    )
}
