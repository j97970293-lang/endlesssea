package dev.endlesssea.app.ui.player

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.app.local.DownloadLocator
import dev.endlesssea.app.local.LocalDetailsRepository
import dev.endlesssea.app.local.localEpisode
import dev.endlesssea.app.ui.details.linksFlowCompat
import dev.endlesssea.app.ui.library.LocalVideoUi
import dev.endlesssea.app.ui.player.session.PlaybackRecoveryState
import dev.endlesssea.data.db.DownloadsDao
import dev.endlesssea.data.db.EpisodeDao
import dev.endlesssea.data.db.EpisodeEntity
import dev.endlesssea.data.db.MediaDao
import dev.endlesssea.extensions.api.model.Episode
import dev.endlesssea.extensions.api.model.LinkRequest
import dev.endlesssea.extensions.api.model.Quality
import dev.endlesssea.extensions.api.model.StreamType
import dev.endlesssea.extensions.api.model.VideoLink
import dev.endlesssea.extensions.loader.ExtensionRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

internal data class RestoredPlayback(
    val links: List<VideoLink>,
    val queue: List<PlayerLaunchStore.QueueItem>,
    val queueIndex: Int,
)

/** Rebuilds an expired playback session from stable database/file identities, never cached stream URLs. */
class PlaybackRecoveryResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localDetails: LocalDetailsRepository,
    private val episodeDao: EpisodeDao,
    private val mediaDao: MediaDao,
    private val downloadsDao: DownloadsDao,
    private val extensionRegistry: ExtensionRegistry,
    private val prefs: AppPrefs,
) {
    internal suspend fun restore(state: PlaybackRecoveryState): RestoredPlayback {
        require(state.isValid) { "Session de reprise invalide" }
        val (queue, index) = rebuildQueue(state)
        val links = resolveLinks(state.mediaId, state.episodeId)
        return RestoredPlayback(links = links, queue = queue, queueIndex = index)
    }

    /** Resolve one episode at the moment it is needed; provider URLs are never persisted. */
    internal suspend fun resolveLinks(mediaId: String, episodeId: String): List<VideoLink> {
        require(mediaId.isNotBlank() && episodeId.isNotBlank()) { "Identité de lecture manquante" }

        if (mediaId.startsWith(LOCAL_MEDIA_PREFIX)) {
            check(DownloadLocator.exists(context, episodeId)) { "Le fichier local n'est plus accessible." }
            return listOf(directFile(episodeId, "Fichier local"))
        }

        resolveDownloadedFile(mediaId, episodeId)?.let { return listOf(directFile(it, "Téléchargement")) }

        val row = episodeDao.byId(episodeId)
            ?: error("Épisode introuvable hors ligne ; rouvrez sa fiche pour actualiser la liste.")
        require(row.mediaId == mediaId) { "L'épisode enregistré n'appartient plus à cette fiche." }
        val episode = row.toEpisode()
        val extensionId = mediaId.substringBefore(':').takeIf(String::isNotBlank)
            ?: error("Source vidéo introuvable pour cette fiche.")
        val extension = withContext(Dispatchers.IO) { extensionRegistry.instance(extensionId) }
        val links = withTimeout(90_000L) {
            withContext(Dispatchers.IO) {
                extension.linksFlowCompat(LinkRequest(episode = episode, mediaId = mediaId)).toList()
            }
        }
        check(links.isNotEmpty()) { "La source n'a fourni aucun lien pour « ${episode.title ?: episodeId} »." }
        return orderByPreferredAudio(links)
    }

    private suspend fun rebuildQueue(state: PlaybackRecoveryState): Pair<List<PlayerLaunchStore.QueueItem>, Int> {
        val mediaId = state.mediaId
        val raw = if (mediaId.startsWith(LOCAL_MEDIA_PREFIX)) {
            val folder = mediaId.removePrefix(LOCAL_MEDIA_PREFIX)
            val files = try {
                localDetails.load(folder).files
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            files.map { file -> localQueueItem(file, mediaId) }
        } else {
            val media = mediaDao.byId(mediaId)
            val baseTitle = media?.customTitle?.takeIf { it.isNotBlank() }
                ?: media?.title?.takeIf { it.isNotBlank() }
                ?: state.title.ifBlank { "Lecture" }
            val isMovie = media?.type.equals("MOVIE", ignoreCase = true)
            episodeDao.ofMedia(mediaId).first()
                .sortedWith(compareBy<EpisodeEntity>({ it.season ?: 0 }, { it.number }))
                .map { row ->
                    val episode = row.toEpisode()
                    PlayerLaunchStore.QueueItem(
                        title = if (row.id == state.episodeId && state.title.isNotBlank()) state.title
                            else episodeTitle(baseTitle, episode, isMovie),
                        episodeId = row.id,
                        thumbnailUrl = row.thumbnailUrl,
                        season = row.season,
                        episodeNumber = row.number.takeIf { it.isFinite() },
                        durationMs = row.durationMs ?: 0L,
                        mediaId = mediaId,
                    )
                }
        }

        val withCurrent = if (raw.any { it.episodeId == state.episodeId }) raw else raw +
            PlayerLaunchStore.QueueItem(
                title = state.title.ifBlank { "Lecture" },
                episodeId = state.episodeId,
                mediaId = mediaId,
            )
        val queue = withCurrent.map { item ->
            if (item.episodeId == state.episodeId && state.title.isNotBlank()) item.copy(title = state.title)
            else item
        }
        val index = queue.indexOfFirst { it.episodeId == state.episodeId }
        return queue to index
    }

    private suspend fun resolveDownloadedFile(mediaId: String, episodeId: String): String? {
        val tasks = downloadsDao.completedForMedia(mediaId).filter { it.episodeId == episodeId }
        if (tasks.isEmpty()) return null
        val roots = listOfNotNull(prefs.storageRoot.value) + prefs.storageHistory.value
        for (task in tasks) {
            val resolved = DownloadLocator.resolve(context, task.targetUri, task.fileName, roots)
            if (DownloadLocator.exists(context, resolved)) return resolved
        }
        return null
    }

    private fun localQueueItem(file: LocalVideoUi, mediaId: String): PlayerLaunchStore.QueueItem {
        val episode = localEpisode(file)
        return PlayerLaunchStore.QueueItem(
            title = episode.title?.takeIf { it.isNotBlank() } ?: file.displayName,
            episodeId = file.uri,
            thumbnailUrl = file.uri,
            season = episode.season,
            episodeNumber = episode.number.takeIf { it.isFinite() },
            durationMs = episode.durationMs ?: 0L,
            mediaId = mediaId,
            markers = PlayerLaunchStore.SkipMarkers(
                introStartSec = file.introStartSec,
                introEndSec = file.introEndSec,
                outroStartSec = file.outroStartSec,
            ),
        )
    }

    private fun episodeTitle(mediaTitle: String, episode: Episode, isMovie: Boolean): String {
        if (isMovie) return mediaTitle
        val number = episode.number.takeIf { it.isFinite() }?.toString()?.removeSuffix(".0")
        val label = number?.let { "E$it" } ?: episode.title?.takeIf(String::isNotBlank) ?: "Épisode"
        val season = episode.season?.let { "S$it:" }.orEmpty()
        return "$mediaTitle $season$label"
    }

    private fun orderByPreferredAudio(links: List<VideoLink>): List<VideoLink> {
        val preferred = prefs.preferredAudioLang.value
        if (preferred == "auto" || links.size < 2) return links
        return links.sortedWith(
            compareByDescending<VideoLink> { it.audioLang.iso.equals(preferred, ignoreCase = true) }
                .thenByDescending { it.quality.ordinal },
        )
    }

    private fun EpisodeEntity.toEpisode() = Episode(
        id = id,
        number = number,
        season = season,
        title = title,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
        data = data,
    )

    private fun directFile(uri: String, server: String) = VideoLink(
        url = uri,
        streamType = StreamType.DIRECT_FILE,
        quality = Quality.UNKNOWN,
        server = server,
    )

    private companion object {
        const val LOCAL_MEDIA_PREFIX = "local:"
    }
}
