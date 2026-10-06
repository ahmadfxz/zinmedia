package com.zinmedia.effects.gl

import android.content.Context
import android.opengl.GLES20
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.zinmedia.effects.BeautyParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Parameter filter warna; diganti dari thread mana saja, dibaca di thread GL saat frame berikutnya. */
internal data class ColorParams(
    val matrix: FloatArray,
    val offset: FloatArray,
    val lut: CubeLut?,
    val beauty: BeautyParams,
)

/**
 * Filter RootEncoder: penghalus kulit, filter warna/LUT, dan efek wajah ([engine]), digambar pada
 * frame yang dikirim (preview & siaran sama). Dengan efek wajah aktif, frame ditahan sampai titik
 * wajahnya siap.
 *
 * Alur RootEncoder: [draw] mengikat FBO filter, memanggil [drawFilter] (di sini: siapkan program
 * utama), menggambar persegi penuh, lalu [disableResources] (di sini: efek wajah di atasnya).
 */
internal class EffectsFilterRender : BaseFilterRender() {

    @Volatile
    var params: ColorParams = ColorParams(IdentityMatrix, FloatArray(3), null, BeautyParams.None)

    /**
     * Frame adalah gambar cermin: frame deteksi dibalik lagi agar MediaPipe melihat wajah asli
     * (kiri/kanan orangnya benar); efek digambar dibalik kembali.
     */
    @Volatile
    var mirrored = false

    /** Efek wajah: penahan frame, deteksi, dan penggambar. */
    val engine = FaceEffectEngine(external = false)

    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private var uColorMatrix = 0
    private var uColorOffset = 0
    private var uLutSize = 0
    private var uLutMix = 0
    private var beautyUniforms: BeautyUniforms? = null
    private var uTexel = 0
    private var lutTexture = 0
    private var uploadedLut: CubeLut? = null
    private var maxTextureSize = 0

    private val colorMatrix = FloatArray(9)
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        // x, y, u, v (dua segitiga penuh layar)
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
        position(0)
    }

    override fun initGlFilter(context: Context) {
        program = createProgram(EffectShaders.VERTEX, EffectShaders.filterFragment(external = false))
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uColorMatrix = GLES20.glGetUniformLocation(program, "uColorMatrix")
        uColorOffset = GLES20.glGetUniformLocation(program, "uColorOffset")
        uLutSize = GLES20.glGetUniformLocation(program, "uLutSize")
        uLutMix = GLES20.glGetUniformLocation(program, "uLutMix")
        beautyUniforms = BeautyUniforms(program)
        uTexel = GLES20.glGetUniformLocation(program, "uTexel")
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sLut"), 1)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        lutTexture = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        // Tekstur LUT kosong 1×1 agar sampler selalu valid.
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
            ByteBuffer.allocateDirect(4),
        )
        val max = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0)
        maxTextureSize = max[0]
        engine.init()
        // Konteks GL baru (mis. filter dipasang ulang): unggah ulang LUT.
        uploadedLut = null
    }

    override fun drawFilter() {
        val current = params
        if (current.lut !== uploadedLut) uploadLut(current.lut)
        val mirror = mirrored
        val source = engine.push(
            previousTexId, width, height,
            detectMatrix = if (mirror) MirrorSMatrix4 else FaceEffectEngine.IDENTITY,
            detectAspect = width.toFloat() / height,
            flipped = mirror,
            restoreFbo = renderHandler.fboId[0], restoreWidth = width, restoreHeight = height,
        )

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, source)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, FaceEffectEngine.IDENTITY, 0)
        // GLSL mat3 kolom-mayor (ES 2.0 tidak mendukung transpose=true): transpos manual.
        for (row in 0 until 3) for (col in 0 until 3) colorMatrix[col * 3 + row] = current.matrix[row * 3 + col]
        GLES20.glUniformMatrix3fv(uColorMatrix, 1, false, colorMatrix, 0)
        GLES20.glUniform3fv(uColorOffset, 1, current.offset, 0)
        val lut = uploadedLut
        GLES20.glUniform1f(uLutSize, (lut?.size ?: 2).toFloat())
        GLES20.glUniform1f(uLutMix, if (lut != null) 1f else 0f)
        // Beauty kulit & riasan: masker wajah frame yang ditahan (bentuk sudah di frame).
        beautyUniforms?.upload(current.beauty, engine)
        GLES20.glUniform2f(uTexel, 1f / width, 1f / height)
        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        vertices.position(2)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        // RootEncoder menggambar persegi penuh setelah ini, lalu memanggil disableResources().
    }

    override fun disableResources() {
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        beautyUniforms?.unbind()
        engine.draw(faceAffine(), width, height, renderHandler.fboId[0])
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    /** Titik frame deteksi -> piksel frame ini; frame deteksi yang dibalik dibalik kembali. */
    private fun faceAffine(): FloatArray {
        val w = width.toFloat()
        val h = height.toFloat()
        return if (engine.heldFlipped) floatArrayOf(-w, 0f, 0f, h, w, 0f) else floatArrayOf(w, 0f, 0f, h, 0f, 0f)
    }

    override fun release() {
        engine.release()
        GLES20.glDeleteProgram(program)
        GLES20.glDeleteTextures(1, intArrayOf(lutTexture), 0)
        program = 0
    }

    private fun uploadLut(lut: CubeLut?) {
        uploadedLut = null
        if (lut == null || lut.width > maxTextureSize) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        lut.rgba.position(0)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, lut.width, lut.height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, lut.rgba,
        )
        uploadedLut = lut
    }

    private fun createProgram(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { "Shader gagal: ${GLES20.glGetShaderInfoLog(shader)}" }
        }
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, vertex))
            GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { "Program gagal: ${GLES20.glGetProgramInfoLog(program)}" }
        }
    }
}

internal val IdentityMatrix = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

/** s -> 1 − s (kolom-mayor): frame deteksi dibalik kiri-kanan. */
private val MirrorSMatrix4 = floatArrayOf(
    -1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    1f, 0f, 0f, 1f,
)
