package dev.endlesssea.app.ui.details

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Custom Android deep link; the app opens the exact extension/media ID when installed. */
internal fun mediaShareLink(mediaId: String): String {
    val pathSegment = URLEncoder.encode(mediaId, StandardCharsets.UTF_8.name()).replace("+", "%20")
    return "endlesssea://media/$pathSegment"
}
