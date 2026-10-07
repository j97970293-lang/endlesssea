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

    val mediaId: String = checkNotNull(savedStateHandle["id"])

    private val _uiState = MutableStateFlow(DetailsUiState())

    /** §glisser-serveurs : ordre de priorité persistant des serveurs. */
    /** §progression-immersive : épisodes terminés / total (carte « Watching progress »). */
    val watchedCount = MutableStateFlow(0)
    val resumeEpisodeId = MutableStateFlow<String?>(null)

    /** §use-poster-color : teinte émotionnelle extraite de l'affiche (Réglages → Thème). */
    val usePosterColor = prefs.usePosterColor

    val serverOrder = prefs.serverOrder
    fun saveServerOrder(order: List<String>) = prefs.setServerOrder(order)
    val uiState: StateFlow<DetailsUiState> = _uiState

    /** Paquet d'extension associé (préfixe avant le premier « : »). */
    private val extensionId = mediaId.substringBefore(":")
    private val mediaKey = mediaId.substringAfter(":", missingDelimiterValue = mediaId)

    init { load() }

    fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
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
            val episodes = details.seasons.flatMap { it.episodes }
            _uiState.value = _uiState.value.copy(
                loading = false, details = details, episodes = episodes,
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

    /** §hors-ligne : fichiers téléchargés affichés sur la fiche (lisibles sans réseau). */
    fun refreshDeviceFiles() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val list = downloadsDao.completedForMedia(mediaId).map { t ->
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

    /** §deplacer-téléchargement : lecture d'un fichier local (SAF supporté). */
    fun playDeviceFile(f: DeviceFileUi, onReady: () -> Unit) {
        // §emplacement : si le dossier de téléchargement a changé, on retrouve
        // le fichier par son nom dans les emplacements connus.
        val roots = listOfNotNull(prefs.storageRoot.value) + prefs.storageHistory.value
        val playable = dev.endlesssea.app.local.DownloadLocator.resolve(
            context, f.targetUri, f.label.substringBefore(" · "), roots,
        ) ?: f.targetUri
        dev.endlesssea.app.ui.player.PlayerLaunchStore.set(
            title = f.label.substringBefore(" · "),
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
            // §auto-intro-telechargements : si des marqueurs ont été réglés pour
            // ce fichier, le saut automatique fonctionne aussi hors ligne.
            markers = prefs.localFileMeta(playable).let { m ->
                dev.endlesssea.app.ui.player.PlayerLaunchStore.SkipMarkers(
                    introStartSec = m.introStartSec,
                    introEndSec = m.introEndSec,
                    outroStartSec = m.outroStartSec,
                )
            },
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
                "▶ Continuer — Ép. ${ep.number.toInt()} · ${mm}:${"%02d".format(ss)}"
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
    )

    private val _trackerSearch = MutableStateFlow(TrackerSearchState())
    val trackerSearch: StateFlow<TrackerSearchState> = _trackerSearch

    /** Rattachement courant de cette fiche (null = non suivie). */
    private val _trackerLink = MutableStateFlow<dev.endlesssea.data.db.TrackerLinkEntity?>(null)
    val trackerLink: StateFlow<dev.endlesssea.data.db.TrackerLinkEntity?> = _trackerLink

    /** Services connectés et actifs : seuls ceux-là sont proposés au rattachement. */
    private val _connectedServices = MutableStateFlow<List<String>>(emptyList())
    val connectedServices: StateFlow<List<String>> = _connectedServices

    /** Tic de recomposition du bloc « Suivi » (mises à jour manuelles). */
    val trackerTick = MutableStateFlow(0)

    private fun refreshTracker() = viewModelScope.launch {
        _trackerLink.value = trackers.linkOf(mediaId)
        _connectedServices.value = trackers.accounts.first().filter { it.enabled }
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

    fun linkTracker(service: String, hit: dev.endlesssea.app.tracking.TrackerSearchHit) {
        viewModelScope.launch {
            trackers.link(mediaId, service, hit, status = "WATCHING")
            _trackerSearch.value = TrackerSearchState()
            refreshTracker()
            trackerTick.value += 1
            _uiState.value = _uiState.value.copy(message = "Rattaché : ${hit.title}")
        }
    }

    fun unlinkTracker() = viewModelScope.launch {
        trackers.unlink(mediaId)
        refreshTracker()
        trackerTick.value += 1
        _uiState.value = _uiState.value.copy(message = "Rattachement supprimé")
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

    /** Met la langue préférée (réglage global) en premier, sans casser l'ordre qualité. */
    private fun orderByLangPref(links: List<VideoLink>): List<VideoLink> {
        val pref = prefs.preferredAudioLang.value
        if (pref == "auto" || links.size < 2) return links
        return links.sortedWith(
            compareByDescending<VideoLink> { it.audioLang.iso == pref }
                .thenByDescending { it.quality.ordinal },
        )
    }

    /** Lecture : met les liens dans le canal mémoire, puis [onReady] lance PlayerActivity. */
    /** §hors-ligne-prioritaire : fichier téléchargé correspondant à un épisode. */
    fun downloadedFor(episodeId: String): DeviceFileUi? =
        _uiState.value.deviceFiles.firstOrNull { it.episodeId == episodeId }

    /** Supprime un téléchargement (appui long). */
    fun deleteDeviceFile(f: DeviceFileUi) = viewModelScope.launch {
        runCatching {
            val uri = android.net.Uri.parse(f.targetUri)
            if (uri.scheme == "content") {
                androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.delete()
            } else {
                java.io.File(uri.path ?: "").delete()
            }
        }
        runCatching { downloads.cancel(f.id, deleteFiles = true) }
        refreshDeviceFiles()
        _uiState.value = _uiState.value.copy(message = "Téléchargement supprimé")
    }

    fun playEpisode(episode: Episode, startIndex: Int = 0, onReady: () -> Unit = {}) = viewModelScope.launch {
        // §épisode-suivant : la file = tous les épisodes de la fiche, dans l'ordre ;
        // les liens des voisins sont résolus à la demande par ce résolveur.
        val all = _uiState.value.episodes
        dev.endlesssea.app.ui.player.PlayerLaunchStore.setQueue(
            all.map { ep ->
                dev.endlesssea.app.ui.player.PlayerLaunchStore.QueueItem(
                    title = buildEpisodeTitle(ep),
                    episodeId = ep.id,
                    links = downloadedFor(ep.id)?.let { local ->
                        listOf(VideoLink(url = local.targetUri, streamType = dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE,
                            quality = dev.endlesssea.extensions.api.model.Quality.UNKNOWN, server = "Téléchargement"))
                    } ?: _uiState.value.linksByEpisode[ep.id].orEmpty(),
                    downloaded = downloadedFor(ep.id) != null,
                    thumbnailUrl = ep.thumbnailUrl, season = ep.season, episodeNumber = ep.number,
                    durationMs = ep.durationMs ?: 0L, mediaId = mediaId,
                )
            },
            all.indexOfFirst { it.id == episode.id },
        )
        dev.endlesssea.app.ui.player.PlayerLaunchStore.resolver = { id ->
            val ep = _uiState.value.episodes.firstOrNull { it.id == id }
            if (ep == null) {
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
        // §stockage-public (arborescence Aniyomi) :
        //   downloads/<Source>/<Série>/<Épisode>/<fichier>
        val sourceName = runCatching { registry.instance(extensionId).info.name }
            .getOrDefault(extensionId)
        val seriesName = _uiState.value.details?.title ?: mediaId
        // §structure-plate : un dossier par SÉRIE, les épisodes dedans
        // (pas de sous-dossier par épisode — demande utilisateur).
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
        _uiState.value = _uiState.value.copy(message = "Téléchargement ajouté : $fileName")
    }

    /**
     * « Tout télécharger » : pour chaque épisode, résout les liens et met en file
     * la meilleure qualité en fichier direct (les flux HLS/embed ne sont pas
     * téléchargeables — ils restent en lecture seule).
     */
    fun enqueueAll(
        episodes: List<Episode> = this._uiState.value.episodes,
        serverPriority: List<String> = prefs.serverOrder.value,
    ) = viewModelScope.launch {
        if (_uiState.value.batchRunning || episodes.isEmpty()) return@launch
        _uiState.value = _uiState.value.copy(batchRunning = true, message = "Résolution des liens…")
        val extInstance = registry.instance(extensionId)
        var added = 0; var streamOnly = 0; var failed = 0
        episodes.forEachIndexed { i, episode ->
            _uiState.value = _uiState.value.copy(
                message = "Résolution des liens… (${i + 1}/${episodes.size})",
            )
            val cached = _uiState.value.linksByEpisode[episode.id]
            val links = if (cached != null) cached else runCatching {
                extInstance.loadLinks(LinkRequest(episode = episode, mediaId = mediaId))
            }.getOrNull()
            if (links == null) { failed++; return@forEachIndexed }
            if (cached == null && links.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    linksByEpisode = _uiState.value.linksByEpisode + (episode.id to links),
                )
            }
            // Serveurs par ordre de priorité (glisser-déposer utilisateur) : on essaie
            // le serveur n°1 d'abord ; s'il n'existe pas pour cet épisode → le suivant.
            fun linksFor(server: String?): List<dev.endlesssea.extensions.api.model.VideoLink> =
                if (server == null) links else links.filter { it.server.equals(server, true) }
            val pool = serverPriority.asSequence()
                .map { linksFor(it) }.firstOrNull { it.isNotEmpty() } ?: links
            // Meilleur lien téléchargeable du pool choisi : direct > HLS, puis qualité
            val downloadable = pool.filter {
                it.streamType == dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE ||
                    it.streamType == dev.endlesssea.extensions.api.model.StreamType.HLS
            }
            val best = downloadable
                .filter { it.streamType == dev.endlesssea.extensions.api.model.StreamType.DIRECT_FILE }
                .maxByOrNull { it.quality.pixels }
                ?: downloadable.maxByOrNull { it.quality.pixels }
            if (best == null) { streamOnly++; return@forEachIndexed }
            enqueueAndWait(episode, best); added++
        }
        val parts = buildList {
            if (added > 0) add("$added téléchargement${if (added > 1) "s" else ""} ajouté${if (added > 1) "s" else ""}")
            if (streamOnly > 0) add("$streamOnly en lecture seule (flux)")
            if (failed > 0) add("$failed sans réponse de la source")
        }
        _uiState.value = _uiState.value.copy(
            batchRunning = false,
            message = if (parts.isEmpty()) "Aucun fichier téléchargeable trouvé" else parts.joinToString(" · "),
        )
    }

    private suspend fun enqueueAndWait(episode: Episode, link: VideoLink) {
        val title = buildEpisodeTitle(episode)
        val quality = link.quality.name
        val fileName = FileNames.sanitize(
            "$title [$quality]${if (link.subtitles.isNotEmpty()) " [subs]" else ""}",
        ) + extensionFor(link)
        val sourceName2 = runCatching { registry.instance(extensionId).info.name }
            .getOrDefault(extensionId)
        val seriesName2 = _uiState.value.details?.title ?: mediaId
        val relDirs = listOf(
            dev.endlesssea.app.local.DownloadStorage.DOWNLOADS_DIR,
            sourceName2, seriesName2,
        )
        writeOfflineMetadata(relDirs, sourceName2, seriesName2)
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
        val e = "E${episode.number.toInt()}"
        val s = episode.season?.let { "S$it:" } ?: ""
        return "$base $s$e"
    }

    private fun extensionFor(link: VideoLink): String = when (link.streamType) {
        dev.endlesssea.extensions.api.model.StreamType.HLS -> ".ts"   // segments MPEG-TS assemblés
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

    /** §hors-ligne : re-hydrate un épisode de la base (affichage sans la source). */
    private fun EpisodeEntity.toEpisode() = Episode(
        id = id, number = number, season = season, title = title,
        thumbnailUrl = thumbnailUrl, durationMs = durationMs, data = data,
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
