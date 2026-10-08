package dev.endlesssea.app.ui.player.session

/**
 * The ViewModel owns a session, not the Activity. Reattaching a recreated Activity
 * must not consume a second request or rebuild the engine. This is not persistence
 * across process death: an absent request is reported explicitly instead.
 */
internal class PlaybackSessionOwner<T>(private val isPlayable: (T) -> Boolean) {
    private var received = false

    sealed interface Attachment<out T> {
        data class Start<T>(val request: T) : Attachment<T>
        data object Retained : Attachment<Nothing>
        data object Unavailable : Attachment<Nothing>
    }

    @Synchronized
    fun attach(consume: () -> T?): Attachment<T> {
        if (received) return Attachment.Retained
        val request = consume() ?: return Attachment.Unavailable
        if (!isPlayable(request)) return Attachment.Unavailable
        received = true
        return Attachment.Start(request)
    }
}
