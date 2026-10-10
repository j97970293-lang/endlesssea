package dev.endlesssea.downloader

/** Actions the foreground download service understands. Kept free of Android types so the lifecycle can be unit-tested. */
object DownloadServiceCommands {
    const val RESUME_ALL = "dev.endlesssea.action.RESUME_ALL"
    const val PAUSE = "dev.endlesssea.action.PAUSE"
    const val RESUME = "dev.endlesssea.action.RESUME"
    const val CANCEL = "dev.endlesssea.action.CANCEL"

    fun describe(action: String?, hasTaskId: Boolean): String = when (action) {
        RESUME_ALL -> "resume-queue"
        PAUSE -> if (hasTaskId) "pause" else "ignore"
        RESUME -> if (hasTaskId) "resume" else "ignore"
        CANCEL -> if (hasTaskId) "cancel" else "ignore"
        else -> "start"
    }
}
