package dev.endlesssea.player

/** Immutable frame snapshot; changes never replace the GL program or its output buffers. */
data class LiveVideoSettings(
    val brightness: Float = 0f, val saturation: Float = 0f, val hue: Float = 0f,
    val contrast: Float = 1f, val gamma: Float = 1f, val temperature: Float = 0f,
    val sharpness: Float = 0f,
) {
    fun bounded() = copy(brightness = brightness.coerceIn(-1f, 1f), saturation = saturation.coerceIn(-1f, 1f),
        hue = hue.coerceIn(-180f, 180f), contrast = contrast.coerceIn(0f, 2f), gamma = gamma.coerceIn(.1f, 4f),
        temperature = temperature.coerceIn(-1f, 1f), sharpness = sharpness.coerceIn(0f, 2f))
}

data class VideoFrameBounds(val width: Float, val height: Float)

/** Source aspect ratio is independent from the processed/output surface dimensions.
 * Modes: 0 = fit, 1 = cover/crop, 2 = stretch, 3 = fit over a separate backdrop.
 */
fun videoFrameBounds(width: Float, height: Float, sourceAspect: Float, mode: Int): VideoFrameBounds {
    if (mode == 2 || width <= 0f || height <= 0f || !sourceAspect.isFinite() || sourceAspect <= 0f)
        return VideoFrameBounds(width, height)
    val fitWidth = if (width / height > sourceAspect) height * sourceAspect else width
    val fitHeight = fitWidth / sourceAspect
    val scale = if (mode == 1) maxOf(width / fitWidth, height / fitHeight) else 1f
    return VideoFrameBounds(fitWidth * scale, fitHeight * scale)
}

/** Pan only as far as an edge of the transformed picture, including pinch on top of the framing mode. */
fun videoPanLimits(width: Float, height: Float, sourceAspect: Float, mode: Int, zoom: Float): VideoFrameBounds {
    if (width <= 0f || height <= 0f) return VideoFrameBounds(0f, 0f)
    val aspect = sourceAspect.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    val fit = videoFrameBounds(width, height, aspect, 0)
    val display = videoDisplayScale(width, height, aspect, mode)
    val pinch = zoom.takeIf { it.isFinite() }?.coerceIn(1f, 12f) ?: 1f
    val shownW = fit.width * display.scaleX * pinch
    val shownH = fit.height * display.scaleY * pinch
    return VideoFrameBounds(kotlin.math.abs(shownW - width) / 2f, kotlin.math.abs(shownH - height) / 2f)
}
