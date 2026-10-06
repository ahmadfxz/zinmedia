package com.zinmedia.effects.gl

import android.graphics.Color
import android.opengl.GLES20
import androidx.annotation.RestrictTo
import com.zinmedia.effects.BeautyFeature
import com.zinmedia.effects.BeautyParams

/**
 * Lokasi & pengunggah uniform beauty pada program filter ([EffectShaders.filterFragment]); dipakai
 * filter siaran dan kamera zinmedia. Masker dari [FaceEffectEngine] diikat ke unit tekstur 2..4.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class BeautyUniforms(program: Int) {
    private val skin = GLES20.glGetUniformLocation(program, "uSkin")
    private val areas = GLES20.glGetUniformLocation(program, "uAreas")
    private val makeup = GLES20.glGetUniformLocation(program, "uMakeup")
    private val lipColor = GLES20.glGetUniformLocation(program, "uLipColor")

    init {
        GLES20.glUseProgram(program)
        for (k in 0 until BeautyPass.MASKS) {
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sMask$k"), FIRST_UNIT + k)
        }
    }

    /**
     * Unggah [params] dan ikat masker [engine] (bila siap) untuk frame yang sedang digambar. Unit
     * tekstur aktif dikembalikan ke 0.
     */
    public fun upload(params: BeautyParams, engine: FaceEffectEngine) {
        val ready = engine.masksReady
        GLES20.glUniform4f(
            skin,
            params[BeautyFeature.Smooth], params[BeautyFeature.Brighten], params[BeautyFeature.Rosy], params[BeautyFeature.Sharpen],
        )
        GLES20.glUniform4f(
            areas,
            params[BeautyFeature.DarkCircles], params[BeautyFeature.SmileLines], params[BeautyFeature.WhitenTeeth], params[BeautyFeature.BrightenEyes],
        )
        GLES20.glUniform4f(
            makeup,
            params[BeautyFeature.Lipstick], params[BeautyFeature.Blush], params[BeautyFeature.Contour], if (ready) 1f else 0f,
        )
        GLES20.glUniform3f(lipColor, Color.red(params.lipColor) / 255f, Color.green(params.lipColor) / 255f, Color.blue(params.lipColor) / 255f)
        val textures = engine.maskTextures
        for (k in 0 until BeautyPass.MASKS) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + FIRST_UNIT + k)
            // Tanpa masker: unit tetap diikat ke tekstur sah agar sampler tidak bermasalah.
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (ready) textures[k] else 0)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    /** Lepas ikatan masker setelah menggambar. */
    public fun unbind() {
        for (k in 0 until BeautyPass.MASKS) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + FIRST_UNIT + k)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    private companion object {
        const val FIRST_UNIT = 2
    }
}
