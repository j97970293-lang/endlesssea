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

    @Volatile var pending: Launch = Launch()
        private set

    fun set(
        title: String, mediaId: String?, episodeId: String?,
        links: List<VideoLink>, startIndex: Int,
    ) {
        pending = Launch(title, mediaId, episodeId, links, startIndex)
    }

    fun consume(): Launch = pending.also { pending = Launch() }
}
