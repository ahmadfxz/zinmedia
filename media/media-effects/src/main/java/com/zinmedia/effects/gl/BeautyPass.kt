package com.zinmedia.effects.gl

import android.opengl.GLES20
import com.zinmedia.effects.BeautyFeature
import com.zinmedia.effects.BeautyParams
import com.zinmedia.effects.face.BeautyMesh
import com.zinmedia.effects.face.FaceShaper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Tahap beauty di GPU, sekali per frame di ruang tekstur sumber (sama dengan cincin frame), sehingga
 * semua keluaran cukup memakai hasilnya:
 * 1. Warp: frame digambar ulang lewat jaring wajah yang titiknya digeser [FaceShaper].
 * 2. Masker: [MASKS] tekstur RGBA (setengah resolusi) dari bobot per titik jaring beauty, untuk
 *    kulit, bawah mata, garis senyum, bibir, gigi, mata, perona, highlight, kontur.
 */
internal class BeautyPass {
    private var warpProgram = 0
    private var wPosition = 0
    private var wTexCoord = 0
    private var maskProgram = 0
    private var mPosition = 0
    private var mWeight = 0
    private var copyProgram = 0
    private var cPosition = 0
    private var solidProgram = 0
    private var sPosition = 0

    private var width = 0
    private var height = 0
    private var warpTexture = 0
    private var warpFbo = 0

    /** Tekstur masker (RGBA, setengah resolusi), sah bila [masksReady]. */
    val maskTextures = IntArray(MASKS)
    private val maskFbos = IntArray(MASKS)
    var masksReady = false
        private set

    private var mesh: BeautyMesh? = null
    private var indices: ShortBuffer? = null
    private var regionIndices: List<ShortBuffer> = emptyList()
    private var weights: Array<ByteBuffer> = emptyArray()
    private var texCoords: FloatBuffer? = null
    private val work = FaceShaper.Workspace()
    /** Per wajah (dipakai ulang): posisi asli & setelah beauty, dan posisi NDC untuk digambar. */
    private val sources = ArrayList<FloatArray>()
    private val targets = ArrayList<FloatArray>()
    private val drawPositions = ArrayList<FloatBuffer>()
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        position(0)
    }

    fun init() {
        warpProgram = createProgram(WARP_VERTEX, WARP_FRAGMENT)
        wPosition = GLES20.glGetAttribLocation(warpProgram, "aPosition")
        wTexCoord = GLES20.glGetAttribLocation(warpProgram, "aTexCoord")
        GLES20.glUseProgram(warpProgram)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(warpProgram, "sTexture"), 0)
        maskProgram = createProgram(MASK_VERTEX, MASK_FRAGMENT)
        mPosition = GLES20.glGetAttribLocation(maskProgram, "aPosition")
        mWeight = GLES20.glGetAttribLocation(maskProgram, "aWeight")
        solidProgram = createProgram(SOLID_VERTEX, SOLID_FRAGMENT)
        sPosition = GLES20.glGetAttribLocation(solidProgram, "aPosition")
        copyProgram = createProgram(COPY_VERTEX, WARP_FRAGMENT)
        cPosition = GLES20.glGetAttribLocation(copyProgram, "aPosition")
        GLES20.glUseProgram(copyProgram)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(copyProgram, "sTexture"), 0)
        // Konteks baru: tekstur & FBO lama milik konteks lama.
        width = 0
        height = 0
        warpFbo = 0
        maskFbos.fill(0)
        masksReady = false
    }

    /**
     * Terapkan [beauty] pada frame [held] ([width]×[height], tekstur 2D) untuk wajah [faces].
     * [detectMatrix] memetakan frame tegak (s, t ke atas) ke koordinat tekstur sumber; [aspect] =
     * lebar/tinggi frame tegak. Mengembalikan tekstur hasil warp (atau [held] bila tanpa bentuk).
     * FBO & viewport tidak dipulihkan (pemanggil yang memulihkan).
     */
    fun apply(held: Int, faces: List<FloatArray>, beauty: BeautyParams, data: BeautyMesh, detectMatrix: FloatArray, aspect: Float, width: Int, height: Int): Int {
        masksReady = false
        if (faces.isEmpty() || !beauty.enabled || warpProgram == 0) return held
        if (width != this.width || height != this.height || warpFbo == 0) allocate(width, height)
        if (mesh !== data) bind(data)
        val shapedWidth = 1000f * aspect.coerceIn(0.1f, 10f)
        val shapedHeight = 1000f
        // Bentuk tiap wajah dihitung sekali per frame: posisi asli, setelah beauty, dan NDC-nya.
        val count = faces.size
        while (sources.size < count) {
            sources += FloatArray(data.vertexCount * 2)
            targets += FloatArray(data.vertexCount * 2)
            drawPositions += ByteBuffer.allocateDirect(data.vertexCount * 8).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }
        for (f in 0 until count) {
            FaceShaper.shape(faces[f], shapedWidth, shapedHeight, beauty, data.oval, sources[f], targets[f], work)
            fill(drawPositions[f], targets[f], detectMatrix, ndc = true)
        }
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        var result = held
        if (beauty.reshapes) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, warpFbo)
            GLES20.glViewport(0, 0, width, height)
            // Latar: frame apa adanya; wajah (dan cincin yang menyambung ke latar) di atasnya.
            GLES20.glUseProgram(copyProgram)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, held)
            quad.position(0)
            GLES20.glVertexAttribPointer(cPosition, 2, GLES20.GL_FLOAT, false, 8, quad)
            GLES20.glEnableVertexAttribArray(cPosition)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(cPosition)
            GLES20.glUseProgram(warpProgram)
            for (f in 0 until count) {
                fill(texCoords!!, sources[f], detectMatrix, ndc = false)
                GLES20.glVertexAttribPointer(wPosition, 2, GLES20.GL_FLOAT, false, 8, drawPositions[f].position(0))
                GLES20.glEnableVertexAttribArray(wPosition)
                GLES20.glVertexAttribPointer(wTexCoord, 2, GLES20.GL_FLOAT, false, 8, texCoords)
                GLES20.glEnableVertexAttribArray(wTexCoord)
                indices!!.position(0)
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, data.indices.size, GLES20.GL_UNSIGNED_SHORT, indices)
            }
            GLES20.glDisableVertexAttribArray(wPosition)
            GLES20.glDisableVertexAttribArray(wTexCoord)
            result = warpTexture
        }

        // Masker mengikuti wajah setelah warp; hanya tekstur yang dipakai fitur aktif.
        val needed = BooleanArray(MASKS) { k -> MASK_FEATURES[k].any { beauty[it] != 0f } }
        if (needed.none { it }) return result
        GLES20.glViewport(0, 0, (width / 2).coerceAtLeast(1), (height / 2).coerceAtLeast(1))
        for (k in 0 until MASKS) {
            if (!needed[k]) continue
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, maskFbos[k])
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(maskProgram)
            for (f in 0 until count) {
                GLES20.glVertexAttribPointer(mPosition, 2, GLES20.GL_FLOAT, false, 8, drawPositions[f].position(0))
                GLES20.glEnableVertexAttribArray(mPosition)
                weights[k].position(0)
                GLES20.glVertexAttribPointer(mWeight, 4, GLES20.GL_UNSIGNED_BYTE, true, 4, weights[k])
                GLES20.glEnableVertexAttribArray(mWeight)
                indices!!.position(0)
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, data.indices.size, GLES20.GL_UNSIGNED_SHORT, indices)
            }
            GLES20.glDisableVertexAttribArray(mWeight)
            // Area bertepi tegas (bibir, mulut, mata): terisi penuh, hanya ke kanalnya.
            GLES20.glUseProgram(solidProgram)
            for ((r, channel) in BeautyMesh.REGION_CHANNELS.withIndex()) {
                if (channel / 4 != k || data.regions[r].isEmpty()) continue
                val c = channel % 4
                GLES20.glColorMask(c == 0, c == 1, c == 2, c == 3)
                for (f in 0 until count) {
                    GLES20.glVertexAttribPointer(sPosition, 2, GLES20.GL_FLOAT, false, 8, drawPositions[f].position(0))
                    GLES20.glEnableVertexAttribArray(sPosition)
                    regionIndices[r].position(0)
                    GLES20.glDrawElements(GLES20.GL_TRIANGLES, data.regions[r].size, GLES20.GL_UNSIGNED_SHORT, regionIndices[r])
                }
                GLES20.glColorMask(true, true, true, true)
            }
        }
        GLES20.glDisableVertexAttribArray(mPosition)
        GLES20.glDisableVertexAttribArray(sPosition)
        masksReady = true
        return result
    }

    /** Frame tanpa beauty: masker tidak dipakai. */
    fun clear() {
        masksReady = false
    }

    fun release() {
        free()
        if (warpProgram != 0) {
            GLES20.glDeleteProgram(warpProgram)
            GLES20.glDeleteProgram(maskProgram)
            GLES20.glDeleteProgram(copyProgram)
            GLES20.glDeleteProgram(solidProgram)
        }
        warpProgram = 0
    }

    /** (u, v) frame tegak (v ke bawah) -> koordinat tekstur sumber (atau NDC untuk digambar). */
    private fun fill(buffer: FloatBuffer, points: FloatArray, m: FloatArray, ndc: Boolean) {
        buffer.position(0)
        for (i in 0 until points.size / 2) {
            val s = points[i * 2]
            val t = 1f - points[i * 2 + 1]
            val x = m[0] * s + m[4] * t + m[12]
            val y = m[1] * s + m[5] * t + m[13]
            buffer.put(if (ndc) x * 2f - 1f else x)
            buffer.put(if (ndc) y * 2f - 1f else y)
        }
        buffer.position(0)
    }

    private fun bind(data: BeautyMesh) {
        mesh = data
        sources.clear()
        targets.clear()
        drawPositions.clear()
        indices = ByteBuffer.allocateDirect(data.indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
            .apply { put(data.indices); position(0) }
        regionIndices = data.regions.map { region ->
            ByteBuffer.allocateDirect(region.size.coerceAtLeast(1) * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
                .apply { put(region); position(0) }
        }
        weights = Array(MASKS) { k ->
            ByteBuffer.allocateDirect(data.vertexCount * 4).order(ByteOrder.nativeOrder()).apply {
                for (v in 0 until data.vertexCount) for (c in 0 until 4) {
                    val channel = k * 4 + c
                    put(if (channel < data.channels) data.weights[v * data.channels + channel] else 0)
                }
                position(0)
            }
        }
        texCoords = ByteBuffer.allocateDirect(data.vertexCount * 8).order(ByteOrder.nativeOrder()).asFloatBuffer()
    }

    private fun allocate(width: Int, height: Int) {
        free()
        this.width = width
        this.height = height
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        warpTexture = ids[0]
        GLES20.glGenFramebuffers(1, ids, 0)
        warpFbo = ids[0]
        attach(warpTexture, warpFbo, width, height)
        GLES20.glGenTextures(MASKS, maskTextures, 0)
        GLES20.glGenFramebuffers(MASKS, maskFbos, 0)
        for (k in 0 until MASKS) attach(maskTextures[k], maskFbos[k], (width / 2).coerceAtLeast(1), (height / 2).coerceAtLeast(1))
    }

    private fun attach(texture: Int, fbo: Int, w: Int, h: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun free() {
        if (warpFbo != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(warpTexture), 0)
            GLES20.glDeleteFramebuffers(1, intArrayOf(warpFbo), 0)
            GLES20.glDeleteTextures(MASKS, maskTextures, 0)
            GLES20.glDeleteFramebuffers(MASKS, maskFbos, 0)
        }
        warpFbo = 0
        maskTextures.fill(0)
        maskFbos.fill(0)
        masksReady = false
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

    companion object {
        /** Tekstur masker (× 4 kanal), sama dengan sampler `sMask0..2` di shader filter. */
        const val MASKS = 3

        /** Fitur yang membaca tiap tekstur masker (urutan kanal: lihat shader filter). */
        private val MASK_FEATURES = arrayOf(
            listOf(
                BeautyFeature.Smooth, BeautyFeature.Brighten, BeautyFeature.Rosy,
                BeautyFeature.DarkCircles, BeautyFeature.SmileLines, BeautyFeature.Lipstick,
            ),
            listOf(BeautyFeature.WhitenTeeth, BeautyFeature.BrightenEyes, BeautyFeature.Blush, BeautyFeature.Contour),
            listOf(BeautyFeature.Contour),
        )

        private const val WARP_VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        private const val COPY_VERTEX = """
            attribute vec2 aPosition;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aPosition * 0.5 + 0.5;
            }
        """

        private const val WARP_FRAGMENT = """
            precision mediump float;
            uniform sampler2D sTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        private const val SOLID_VERTEX = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val SOLID_FRAGMENT = """
            precision mediump float;
            void main() {
                gl_FragColor = vec4(1.0);
            }
        """

        private const val MASK_VERTEX = """
            attribute vec2 aPosition;
            attribute vec4 aWeight;
            varying vec4 vWeight;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vWeight = aWeight;
            }
        """

        private const val MASK_FRAGMENT = """
            precision mediump float;
            varying vec4 vWeight;
            void main() {
                gl_FragColor = vWeight;
            }
        """
    }
}
