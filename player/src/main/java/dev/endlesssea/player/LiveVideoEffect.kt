package dev.endlesssea.player

import android.content.Context
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import java.util.concurrent.atomic.AtomicReference

/** One permanent shader. The GL thread reads the newest settings once per frame. */
@UnstableApi
class LiveVideoEffect : GlEffect {
    private val settings = AtomicReference(LiveVideoSettings())
    fun update(next: LiveVideoSettings): Boolean {
        val bounded = next.bounded()
        return settings.getAndSet(bounded) != bounded
    }
    override fun toGlShaderProgram(context: Context, useHdr: Boolean) = object : BaseGlShaderProgram(useHdr, 1) {
        private val program = GlProgram(context, "shaders/vertex_es2.glsl", "shaders/live_video_es2.glsl")
        private var width = 1
        private var height = 1
        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            width = inputWidth.coerceAtLeast(1); height = inputHeight.coerceAtLeast(1)
            return Size(inputWidth, inputHeight)
        }
        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val s = settings.get()
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setFloatUniform("uTexelW", 1f / width)
                program.setFloatUniform("uTexelH", 1f / height)
                program.setFloatUniform("uBrightness", s.brightness)
                program.setFloatUniform("uSaturation", s.saturation)
                program.setFloatUniform("uHue", s.hue * .017453292f)
                program.setFloatUniform("uContrast", s.contrast)
                program.setFloatUniform("uGamma", s.gamma)
                program.setFloatUniform("uTemperature", s.temperature)
                program.setFloatUniform("uSharpness", s.sharpness)
                program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
                program.bindAttributesAndUniforms()
                android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GlUtil.checkGlError()
            } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e, presentationTimeUs) }
        }
        override fun release() {
            super.release()
            try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
        }
    }
}
