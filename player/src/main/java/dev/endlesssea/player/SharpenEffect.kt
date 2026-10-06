package dev.endlesssea.player

import android.content.Context
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect

/**
 * §amelioration-video — « Netteté anime » : masque flou (unsharp mask) en un seul
 * passage, 5 échantillons par pixel.
 *
 * Pourquoi pas un vrai Anime4K : ses passes multiples (gradient, refine, upscale)
 * coûtent 3 à 6 ms par image sur un GPU mobile, c'est-à-dire des saccades sur un
 * film 1080p. Ici on obtient l'essentiel du rendu « lignes nettes » pour un coût
 * négligeable, et l'effet est désactivable.
 */
@UnstableApi
class SharpenEffect(private val intensity: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean) =
        SharpenShaderProgram(context, useHdr, intensity)
}

@UnstableApi
class SharpenShaderProgram(
    context: Context,
    useHdr: Boolean,
    private val intensity: Float,
) : BaseGlShaderProgram(useHdr, 1) {

    private val program: GlProgram = try {
        GlProgram(context, VERTEX, FRAGMENT)
    } catch (e: Exception) {
        throw VideoFrameProcessingException(e)
    }

    private var texelW = 0f
    private var texelH = 0f

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texelW = 1f / inputWidth.coerceAtLeast(1)
        texelH = 1f / inputHeight.coerceAtLeast(1)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatUniform("uIntensity", intensity)
            program.setFloatUniform("uTexelW", texelW)
            program.setFloatUniform("uTexelH", texelH)
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
            program.bindAttributesAndUniforms()
            android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX = "shaders/vertex_es2.glsl"
        const val FRAGMENT = "shaders/sharpen_es2.glsl"
    }
}
