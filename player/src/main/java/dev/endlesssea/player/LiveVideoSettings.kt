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

/** Source aspect ratio is independent from the processed/output surface dimensions. */
fun videoFrameBounds(width: Float, height: Float, sourceAspect: Float, mode: Int): VideoFrameBounds {
    if (mode == 2 || width <= 0f || height <= 0f || !sourceAspect.isFinite() || sourceAspect <= 0f)
        return VideoFrameBounds(width, height)
    val fitWidth = if (width / height > sourceAspect) height * sourceAspect else width
    val fitHeight = fitWidth / sourceAspect
    val scale = if (mode == 1) maxOf(width / fitWidth, height / fitHeight) else 1f
    return VideoFrameBounds(fitWidth * scale, fitHeight * scale)
}

/** Pan only as far as an edge: preserve the image in fit mode, avoid new blank borders in fill mode. */
fun videoPanLimits(width: Float, height: Float, sourceAspect: Float, mode: Int, zoom: Float): VideoFrameBounds {
    val frame = videoFrameBounds(width, height, sourceAspect, mode)
    val scale = zoom.takeIf { it.isFinite() }?.coerceIn(1f,12f) ?: 1f
    return VideoFrameBounds(kotlin.math.abs(frame.width * scale - width) / 2f,
        kotlin.math.abs(frame.height * scale - height) / 2f)
}
