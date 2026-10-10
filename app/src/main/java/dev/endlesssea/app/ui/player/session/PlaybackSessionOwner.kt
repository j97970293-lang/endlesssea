package dev.endlesssea.app.ui.player.session

/**
 * The ViewModel owns a session, not the Activity. Reattaching a recreated Activity
 * must not consume a second request or rebuild the engine. An optional restore
 * supplier may provide a separately persisted request after process death; this
 * owner itself keeps no disk or saved-state data.
 */
internal class PlaybackSessionOwner<T>(private val isPlayable: (T) -> Boolean) {
    private var received = false

    sealed interface Attachment<out T> {
        data class Start<T>(val request: T) : Attachment<T>
        data object Retained : Attachment<Nothing>
        data object Unavailable : Attachment<Nothing>
    }

    @Synchronized
    fun attach(consume: () -> T?): Attachment<T> = attach(consume, restore = { null })

    @Synchronized
    fun attach(consume: () -> T?, restore: () -> T?): Attachment<T> {
        if (received) return Attachment.Retained
        val request = consume()?.takeIf(isPlayable)
            ?: restore()?.takeIf(isPlayable)
            ?: return Attachment.Unavailable
        received = true
        return Attachment.Start(request)
    }
}
