package dev.endlesssea.app.ui.player.session

import androidx.lifecycle.SavedStateHandle

/**
 * Minimal process-restoration record. It intentionally stores stable content IDs,
 * never provider links, headers, cookies, or other potentially signed URLs.
 */
internal data class PlaybackRecoveryState(
    val mediaId: String,
    val episodeId: String,
    val title: String,
    val positionMs: Long,
) {
    val isValid: Boolean
        get() = mediaId.isNotBlank() && episodeId.isNotBlank() && positionMs >= 0L
}

/** SavedStateHandle-backed record restored by Android when the player task is recreated. */
internal class PlaybackRecoveryStore(private val handle: SavedStateHandle) {
    fun read(): PlaybackRecoveryState? {
        val mediaId = handle.get<String>(KEY_MEDIA_ID)?.takeIf { it.isNotBlank() } ?: return null
        val episodeId = handle.get<String>(KEY_EPISODE_ID)?.takeIf { it.isNotBlank() } ?: return null
        val title = handle.get<String>(KEY_TITLE).orEmpty()
        val positionMs = handle.get<Long>(KEY_POSITION_MS)?.coerceAtLeast(0L) ?: 0L
        return PlaybackRecoveryState(mediaId, episodeId, title, positionMs)
    }

    fun save(state: PlaybackRecoveryState?) {
        if (state == null || !state.isValid) {
            clear()
            return
        }
        handle[KEY_MEDIA_ID] = state.mediaId
        handle[KEY_EPISODE_ID] = state.episodeId
        handle[KEY_TITLE] = state.title
        handle[KEY_POSITION_MS] = state.positionMs.coerceAtLeast(0L)
    }

    /** Update only the active matching session; a stale callback cannot corrupt another episode. */
    fun updatePosition(mediaId: String?, episodeId: String?, positionMs: Long) {
        val current = read() ?: return
        if (current.mediaId != mediaId || current.episodeId != episodeId || positionMs < 0L) return
        handle[KEY_POSITION_MS] = positionMs
    }

    fun clear() {
        handle.remove<String>(KEY_MEDIA_ID)
        handle.remove<String>(KEY_EPISODE_ID)
        handle.remove<String>(KEY_TITLE)
        handle.remove<Long>(KEY_POSITION_MS)
    }

    private companion object {
        const val KEY_MEDIA_ID = "player.recovery.media_id"
        const val KEY_EPISODE_ID = "player.recovery.episode_id"
        const val KEY_TITLE = "player.recovery.title"
        const val KEY_POSITION_MS = "player.recovery.position_ms"
    }
}
