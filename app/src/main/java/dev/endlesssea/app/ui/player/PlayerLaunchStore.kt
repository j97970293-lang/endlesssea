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

    @Volatile var pending: Launch = Launch()
        private set

    fun set(
        title: String, mediaId: String?, episodeId: String?,
        links: List<VideoLink>, startIndex: Int,
        markers: SkipMarkers = SkipMarkers(),
    ) {
        pending = Launch(title, mediaId, episodeId, links, startIndex)
        lastMarkers = markers
    }

    /** Derniers marqueurs fournis (lue par PlayerScreen ; non effacés par consume()). */
    @Volatile var lastMarkers: SkipMarkers = SkipMarkers()
        private set

    fun consume(): Launch = pending.also { pending = Launch() }
}
