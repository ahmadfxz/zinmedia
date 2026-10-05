package com.zinmedia.effects.gl

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.zinmedia.effects.face.FaceDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/** Parameter filter warna; diganti dari thread mana saja, dibaca di thread GL saat frame berikutnya. */
internal data class ColorParams(
    val matrix: FloatArray,
    val offset: FloatArray,
    val lut: CubeLut?,
    /** 0 = mati, 1 = penghalus kulit penuh. */
    val smoothing: Float,
)

/**
 * Filter RootEncoder: penghalus kulit, filter warna/LUT, dan efek wajah, digambar pada frame yang
 * dikirim (preview & siaran sama). Frame yang sama dibaca dalam ukuran kecil untuk [detector].
 *
 * Alur RootEncoder: [draw] mengikat FBO filter, memanggil [drawFilter] (di sini: siapkan program
 * utama), menggambar persegi penuh, lalu [disableResources] (di sini: efek wajah di atasnya).
 */
internal class EffectsFilterRender : BaseFilterRender() {

    @Volatile
    var params: ColorParams = ColorParams(IdentityMatrix, FloatArray(3), null, 0f)

    /**
     * Frame adalah gambar cermin: frame deteksi dibalik lagi agar MediaPipe melihat wajah asli
     * (kiri/kanan orangnya benar). Posisi hasilnya dibalik kembali oleh pemanggil (lihat tag).
     */
    @Volatile
    var mirrored = false

    /** Pendeteksi wajah aktif; `null` = tidak membaca frame. */
    @Volatile
    var detector: FaceDetector? = null

    /** Gambar efek wajah (null = tidak ada); diunggah ke GPU di thread GL. */
    @Volatile
    private var overlayBitmap: Bitmap? = null

    @Volatile
    private var overlayChanged = false

    /**
     * Sudut-sudut efek wajah ternormalisasi (0..1, y ke bawah pada gambar tegak): kiri-atas,
     * kanan-atas, kiri-bawah, kanan-bawah. Kosong = tidak digambar.
     */
    @Volatile
    var quads: List<FloatArray> = emptyList()

    fun setOverlayImage(bitmap: Bitmap?) {
        // Posisi lama milik efek sebelumnya: buang, agar gambar baru tidak sempat tampil di sana.
        quads = emptyList()
        overlayBitmap = bitmap
        overlayChanged = true
    }

    private var program = 0
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private var uColorMatrix = 0
    private var uColorOffset = 0
    private var uLutSize = 0
    private var uLutMix = 0
    private var uSmooth = 0
    private var uTexel = 0
    private var lutTexture = 0
    private var uploadedLut: CubeLut? = null
    private var maxTextureSize = 0

    private var overlayProgram = 0
    private var oPosition = 0
    private var oTexCoord = 0
    private var overlayTexture = 0
    private var overlayReady = false

    /** Program salin polos + FBO kecil untuk membaca frame bagi pendeteksi wajah. */
    private var copyProgram = 0
    private var cPosition = 0
    private var cTexCoord = 0
    private var cTexMatrix = 0
    private var readFbo = 0
    private var readTexture = 0
    private var readWidth = 0
    private var readHeight = 0
    private var readBuffer: ByteBuffer? = null
    private var readBitmap: Bitmap? = null

    private val colorMatrix = FloatArray(9)
    private val vertices: FloatBuffer = floatBuffer(
        // x, y, u, v (dua segitiga penuh layar)
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f,
    )
    private val overlayVertices: FloatBuffer = floatBuffer(*FloatArray(16))

    override fun initGlFilter(context: Context) {
        program = createProgram(EffectShaders.VERTEX, EffectShaders.filterFragment(external = false))
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uColorMatrix = GLES20.glGetUniformLocation(program, "uColorMatrix")
        uColorOffset = GLES20.glGetUniformLocation(program, "uColorOffset")
        uLutSize = GLES20.glGetUniformLocation(program, "uLutSize")
        uLutMix = GLES20.glGetUniformLocation(program, "uLutMix")
        uSmooth = GLES20.glGetUniformLocation(program, "uSmooth")
        uTexel = GLES20.glGetUniformLocation(program, "uTexel")
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sLut"), 1)

        overlayProgram = createProgram(EffectShaders.OVERLAY_VERTEX, EffectShaders.OVERLAY_FRAGMENT)
        oPosition = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
        oTexCoord = GLES20.glGetAttribLocation(overlayProgram, "aTexCoord")
        GLES20.glUseProgram(overlayProgram)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(overlayProgram, "sOverlay"), 0)

        copyProgram = createProgram(EffectShaders.VERTEX, COPY_FRAGMENT)
        cPosition = GLES20.glGetAttribLocation(copyProgram, "aPosition")
        cTexCoord = GLES20.glGetAttribLocation(copyProgram, "aTexCoord")
        cTexMatrix = GLES20.glGetUniformLocation(copyProgram, "uTexMatrix")
        GLES20.glUseProgram(copyProgram)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(copyProgram, "sTexture"), 0)

        val textures = IntArray(3)
        GLES20.glGenTextures(3, textures, 0)
        lutTexture = textures[0]
        overlayTexture = textures[1]
        readTexture = textures[2]
        for (texture in textures) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            setTextureParams()
        }
        // Tekstur LUT kosong 1×1 agar sampler selalu valid.
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
            ByteBuffer.allocateDirect(4),
        )
        val max = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0)
        maxTextureSize = max[0]

        // Frame untuk deteksi: sisi pendek maks. READ_SHORT_SIDE piksel (cukup untuk MediaPipe).
        val scale = min(1f, READ_SHORT_SIDE / min(width, height).coerceAtLeast(1).toFloat())
        readWidth = (width * scale).roundToInt().coerceAtLeast(1)
        readHeight = (height * scale).roundToInt().coerceAtLeast(1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, readTexture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, readWidth, readHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        readFbo = fbo[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, readFbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, readTexture, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        readBuffer = ByteBuffer.allocateDirect(readWidth * readHeight * 4).order(ByteOrder.nativeOrder())
        readBitmap = null

        // Konteks GL baru (mis. filter dipasang ulang): unggah ulang LUT & gambar efek.
        uploadedLut = null
        overlayReady = false
        overlayChanged = true
    }

    override fun drawFilter() {
        val current = params
        if (current.lut !== uploadedLut) uploadLut(current.lut)
        if (overlayChanged) uploadOverlay()
        detector?.takeIf { it.wantsFrame() }?.let { readFrame(it) }

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)
        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, IdentityMatrix4, 0)
        // GLSL mat3 kolom-mayor (ES 2.0 tidak mendukung transpose=true): transpos manual.
        for (row in 0 until 3) for (col in 0 until 3) colorMatrix[col * 3 + row] = current.matrix[row * 3 + col]
        GLES20.glUniformMatrix3fv(uColorMatrix, 1, false, colorMatrix, 0)
        GLES20.glUniform3fv(uColorOffset, 1, current.offset, 0)
        val lut = uploadedLut
        GLES20.glUniform1f(uLutSize, (lut?.size ?: 2).toFloat())
        GLES20.glUniform1f(uLutMix, if (lut != null) 1f else 0f)
        GLES20.glUniform1f(uSmooth, current.smoothing)
        GLES20.glUniform2f(uTexel, 1f / width, 1f / height)
        bindQuad(aPosition, aTexCoord)
        // RootEncoder menggambar persegi penuh setelah ini, lalu memanggil disableResources().
    }

    override fun disableResources() {
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        drawFaceOverlays()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    override fun release() {
        GLES20.glDeleteProgram(program)
        GLES20.glDeleteProgram(overlayProgram)
        GLES20.glDeleteProgram(copyProgram)
        GLES20.glDeleteTextures(3, intArrayOf(lutTexture, overlayTexture, readTexture), 0)
        GLES20.glDeleteFramebuffers(1, intArrayOf(readFbo), 0)
        program = 0
        overlayProgram = 0
        copyProgram = 0
        readFbo = 0
    }

    /**
     * Salin frame masukan (kecil) ke [readFbo] dengan sumbu y dibalik, sehingga baris pertama yang
     * dibaca = atas gambar, lalu kirim ke [detector]. Setelahnya FBO & viewport filter dipulihkan.
     */
    private fun readFrame(detector: FaceDetector) {
        val buffer = readBuffer ?: return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, readFbo)
        GLES20.glViewport(0, 0, readWidth, readHeight)
        GLES20.glUseProgram(copyProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
        val mirror = mirrored
        GLES20.glUniformMatrix4fv(cTexMatrix, 1, false, if (mirror) FlipXYMatrix4 else FlipYMatrix4, 0)
        bindQuad(cPosition, cTexCoord)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(cPosition)
        GLES20.glDisableVertexAttribArray(cTexCoord)
        buffer.position(0)
        GLES20.glReadPixels(0, 0, readWidth, readHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderHandler.fboId[0])
        GLES20.glViewport(0, 0, width, height)

        // Bitmap dipakai ulang: aman karena deteksi sebelumnya sudah selesai (wantsFrame).
        val bitmap = readBitmap?.takeIf { it.width == readWidth && it.height == readHeight }
            ?: Bitmap.createBitmap(readWidth, readHeight, Bitmap.Config.ARGB_8888).also { readBitmap = it }
        buffer.position(0)
        bitmap.copyPixelsFromBuffer(buffer)
        // tag = ukuran frame (untuk menormalkan posisi efek) & apakah frame deteksi dibalik.
        detector.submit(bitmap, floatArrayOf(readWidth.toFloat(), readHeight.toFloat(), if (mirror) 1f else 0f))
    }

    private fun bindQuad(position: Int, texCoord: Int) {
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(2)
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(texCoord)
    }

    private fun drawFaceOverlays() {
        val current = quads
        if (!overlayReady || current.isEmpty()) return
        GLES20.glUseProgram(overlayProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        // Tekstur dari Bitmap ber-alfa premultiplied.
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexture)
        val data = FloatArray(16)
        for (quad in current) {
            // Sudut: kiri-atas, kanan-atas, kiri-bawah, kanan-bawah (tekstur t=0 di atas).
            for (i in 0 until 4) {
                data[i * 4] = quad[i * 2] * 2f - 1f
                data[i * 4 + 1] = 1f - quad[i * 2 + 1] * 2f
                data[i * 4 + 2] = OverlayTexCoords[i * 2]
                data[i * 4 + 3] = OverlayTexCoords[i * 2 + 1]
            }
            overlayVertices.position(0)
            overlayVertices.put(data)
            overlayVertices.position(0)
            GLES20.glVertexAttribPointer(oPosition, 2, GLES20.GL_FLOAT, false, 16, overlayVertices)
            GLES20.glEnableVertexAttribArray(oPosition)
            overlayVertices.position(2)
            GLES20.glVertexAttribPointer(oTexCoord, 2, GLES20.GL_FLOAT, false, 16, overlayVertices)
            GLES20.glEnableVertexAttribArray(oTexCoord)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES20.glDisableVertexAttribArray(oPosition)
        GLES20.glDisableVertexAttribArray(oTexCoord)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun uploadOverlay() {
        overlayChanged = false
        val bitmap = overlayBitmap
        overlayReady = false
        if (bitmap == null || bitmap.isRecycled) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTexture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        overlayReady = true
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

    private fun setTextureParams() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
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

    private companion object {
        /** Sisi pendek frame deteksi (piksel). */
        const val READ_SHORT_SIDE = 320f

        const val COPY_FRAGMENT = """
            precision mediump float;
            uniform sampler2D sTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}

internal val IdentityMatrix = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

private val IdentityMatrix4 = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 0f, 0f, 1f,
)

/** t -> 1 − t (kolom-mayor). */
private val FlipYMatrix4 = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, -1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 1f, 0f, 1f,
)

/** s -> 1 − s, t -> 1 − t (kolom-mayor). */
private val FlipXYMatrix4 = floatArrayOf(
    -1f, 0f, 0f, 0f,
    0f, -1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    1f, 1f, 0f, 1f,
)

private val OverlayTexCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

private fun floatBuffer(vararg values: Float): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(values)
        position(0)
    }
