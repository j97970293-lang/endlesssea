package dev.endlesssea.app.ui.player

import dev.endlesssea.extensions.api.model.VideoLink

/**
 * Canal mémoire Détails → PlayerActivity : évite de sérialiser les liens vidéo
 * dans les extras d'Intent (headers, sous-titres…). Processus unique : suffisant.
 */
object PlayerLaunchStore {

    data class Launch(
        val title: String = "",
        val mediaId: String? = null,
        val episodeId: String? = null,
        val links: List<VideoLink> = emptyList(),
        val startIndex: Int = 0,
    )

    /** §marqueurs intro/outro (§bibliothèque-locale) : plages à sauter, en secondes. */
    data class SkipMarkers(
        val introStartSec: Int? = null,
        val introEndSec: Int? = null,
        val outroStartSec: Int? = null,
    ) {
        val hasAny: Boolean get() = introStartSec != null || outroStartSec != null
    }

    /**
     * §épisode-suivant : élément de la file de lecture. Les liens peuvent être
     * vides pour un épisode pas encore résolu — le lecteur appelle alors
     * [resolver] au moment de passer dessus.
     */
    data class QueueItem(
        val title: String,
        val episodeId: String?,
        val links: List<VideoLink> = emptyList(),
        val thumbnailUrl: String? = null,
        val season: Int? = null,
        val episodeNumber: Float? = null,
        val durationMs: Long = 0L,
        val positionMs: Long = 0L,
        val watched: Boolean = false,
        val downloaded: Boolean = false,
        val mediaId: String? = null,
        val markers: SkipMarkers = SkipMarkers(),
    )

    @Volatile var queue: List<QueueItem> = emptyList()
        private set

    @Volatile var queueIndex: Int = -1

    /** Résolveur de liens à la demande (posé par l'écran Détails, même processus). */
    @Volatile var resolver: (suspend (String) -> List<VideoLink>)? = null

    fun setQueue(items: List<QueueItem>, index: Int) {
        queue = items
        queueIndex = index.takeIf { it in items.indices } ?: -1
    }

    @Volatile var pending: Launch = Launch()
        private set

    fun set(
        title: String, mediaId: String?, episodeId: String?,
        links: List<VideoLink>, startIndex: Int,
        markers: SkipMarkers = SkipMarkers(),
    ) {
        // Never reuse an unrelated playlist from an earlier playback session.
        val matchingIndex = queue.indexOfFirst { episodeId != null && it.episodeId == episodeId && it.mediaId == mediaId }
        if (matchingIndex >= 0) queueIndex = matchingIndex else {
            setQueue(listOf(QueueItem(title, episodeId, links, mediaId = mediaId, markers = markers)), 0)
            resolver = null
        }
        pending = Launch(title, mediaId, episodeId, links, startIndex)
        lastMarkers = markers
    }

    /** Derniers marqueurs fournis (lue par PlayerScreen ; non effacés par consume()). */
    @Volatile var lastMarkers: SkipMarkers = SkipMarkers()
        private set

    fun updateMarkers(markers: SkipMarkers) { lastMarkers = markers }

    fun consume(): Launch = pending.also { pending = Launch() }
}
