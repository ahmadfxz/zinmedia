package com.zinmedia.effects.gl

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.SystemClock
import androidx.annotation.RestrictTo
import com.zinmedia.effects.BeautyParams
import com.zinmedia.effects.face.BeautyMesh
import com.zinmedia.effects.face.FaceDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/** Efek wajah siap gambar: gambar peta UV (cat wajah) atau model 3D, beserta data jaring wajah. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class ActiveFaceEffect(internal val mesh: FaceMeshData) {
    internal class Paint(mesh: FaceMeshData, val texture: Bitmap) : ActiveFaceEffect(mesh)

    /** [textures] = tekstur tiap bagian [GlbModel.parts] yang sudah didekode (atau `null`). */
    internal class Model(mesh: FaceMeshData, val model: GlbModel, val textures: List<Bitmap?>) : ActiveFaceEffect(mesh)
}

/**
 * Mesin efek wajah untuk satu pipeline GL (filter RootEncoder `ZinEffects` atau kamera zinmedia):
 * frame disimpan di cincin & ditahan sampai titik wajahnya siap ([FrameSync]), frame kecil dibaca
 * untuk [detector], lalu efek digambar ke tiap keluaran. Semua fungsi kecuali [onFaces] dipanggil di
 * thread GL.
 *
 * Per frame: [push] (simpan, deteksi, pilih frame yang ditahan) -> gambar frame [push] ke tiap
 * keluaran -> [draw] efek di atasnya dengan afin keluaran itu.
 *
 * @param external sumber berupa tekstur kamera OES (`samplerExternalOES`), bukan tekstur 2D.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FaceEffectEngine(private val external: Boolean) {

    /** Pendeteksi aktif (hasilnya ke [onFaces]); `null` = tanpa efek, frame tidak ditahan. */
    @Volatile
    public var detector: FaceDetector? = null

    /** Lacak wajah walaupun tidak ada aksesori wajah, misalnya untuk beauty face-aware. */
    @Volatile
    public var trackingEnabled: Boolean = false
        set(value) {
            if (field != value) sync.reset()
            field = value
        }

    /** Beauty (bentuk, kulit, riasan) pada wajah frame yang ditahan; butuh [trackingEnabled]. */
    @Volatile
    public var beauty: BeautyParams = BeautyParams.None

    /** Jaring beauty (dimuat [FaceEffectLoader.prepareBeauty]); `null` = beauty belum siap. */
    @Volatile
    internal var beautyMesh: BeautyMesh? = null

    private val beautyPass = BeautyPass()

    /** Tekstur masker beauty (lihat [BeautyPass]); sah bila [masksReady]. */
    public val maskTextures: IntArray get() = beautyPass.maskTextures

    /** Masker beauty frame [heldFrame] siap dipakai shader filter. */
    public val masksReady: Boolean get() = beautyPass.masksReady && heldFrame >= 0

    /** Efek yang digambar; ganti efek = hasil deteksi lama dibuang. */
    @Volatile
    public var effect: ActiveFaceEffect? = null
        set(value) {
            sync.reset()
            field = value
        }

    private val sync = FrameSync(MAX_DELAY)
    private val renderer = MeshRenderer()
    private var frameCounter = 0L
    private var syncing = false

    private var width = 0
    private var height = 0
    private val ringTextures = IntArray(MAX_DELAY + 1)
    private val ringFbos = IntArray(MAX_DELAY + 1)
    private val ringTimestamps = LongArray(MAX_DELAY + 1)

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

    private var heldFaces: List<FloatArray> = emptyList()
    private var heldTimestampMs = 0L

    /** Nomor frame terakhir yang masuk lewat [push] (−1 bila tanpa efek). */
    public var pushedFrame: Long = -1
        private set

    /** Nomor frame yang sedang ditahan & digambar, atau −1 bila tanpa efek (frame langsung). */
    public var heldFrame: Long = -1
        private set

    /** Frame [heldFrame] dideteksi dalam keadaan dibalik dari sumbernya (cermin). */
    public var heldFlipped: Boolean = false
        private set

    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        // x, y, u, v (dua segitiga penuh layar)
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
        position(0)
    }

    /** Siapkan program di konteks GL saat ini (konteks baru = panggil lagi). */
    public fun init() {
        copyProgram = createProgram(COPY_VERTEX, if (external) COPY_FRAGMENT_EXTERNAL else COPY_FRAGMENT)
        cPosition = GLES20.glGetAttribLocation(copyProgram, "aPosition")
        cTexCoord = GLES20.glGetAttribLocation(copyProgram, "aTexCoord")
        cTexMatrix = GLES20.glGetUniformLocation(copyProgram, "uTexMatrix")
        GLES20.glUseProgram(copyProgram)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(copyProgram, "sTexture"), 0)
        renderer.init()
        beautyPass.init()
        // Cincin & frame baca dibuat saat dibutuhkan (ukuran sumber diketahui di push).
        ringTextures.fill(0)
        ringFbos.fill(0)
        readFbo = 0
        width = 0
        height = 0
        syncing = false
    }

    /**
     * Frame sumber baru [source] ([width]×[height]). Dengan efek aktif: simpan di cincin, kirim
     * salinan kecil ke [detector] bila bebas (diambil lewat [detectMatrix]: matriks tekstur yang
     * menghasilkan frame tegak berasio [detectAspect] (lebar/tinggi); [flipped] = frame itu dibalik
     * dari sumber), lalu kembalikan tekstur 2D frame yang ditahan ([heldFrame]). Tanpa efek:
     * [source] apa adanya. FBO [restoreFbo] dan viewport [restoreWidth]×[restoreHeight] dipulihkan
     * sebelum kembali.
     */
    public fun push(
        source: Int,
        width: Int,
        height: Int,
        detectMatrix: FloatArray,
        detectAspect: Float,
        flipped: Boolean,
        restoreFbo: Int,
        restoreWidth: Int,
        restoreHeight: Int,
    ): Int {
        val active = detector
        if (active == null || (!trackingEnabled && effect == null) || copyProgram == 0) {
            if (syncing) {
                syncing = false
                sync.reset()
            }
            heldFaces = emptyList()
            heldFrame = -1
            pushedFrame = -1
            return source
        }
        if (width != this.width || height != this.height || ringTextures[0] == 0) allocate(width, height)
        val (rw, rh) = readSize(detectAspect)
        if (rw != readWidth || rh != readHeight || readFbo == 0) allocateRead(rw, rh)
        syncing = true
        val frame = ++frameCounter
        pushedFrame = frame
        sync.onFrame(frame)
        // Cincin: salinan apa adanya (koordinat tekstur sumber tidak berubah).
        val ringIndex = (frame % ringFbos.size).toInt()
        copy(source, ringFbos[ringIndex], width, height, IDENTITY)
        ringTimestamps[ringIndex] = SystemClock.uptimeMillis()
        if (active.wantsFrame()) readFrame(active, source, detectMatrix, flipped, frame)

        val out = sync.outputFrame(frame)
        val result = sync.resultFor(out)
        heldFaces = result?.meshes.orEmpty()
        heldFlipped = result?.flipped ?: flipped
        heldFrame = out
        val outIndex = (out % ringTextures.size).toInt()
        heldTimestampMs = ringTimestamps[outIndex]
        var held = ringTextures[outIndex]
        // Beauty: warp & masker frame yang ditahan, sekali untuk semua keluaran.
        val mesh = beautyMesh
        if (mesh != null && trackingEnabled) {
            held = beautyPass.apply(held, heldFaces, beauty, mesh, detectMatrix, detectAspect, width, height)
        } else {
            beautyPass.clear()
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, restoreFbo)
        GLES20.glViewport(0, 0, restoreWidth, restoreHeight)
        return held
    }

    /**
     * Gambar efek untuk wajah di frame [heldFrame] ke [targetFbo] ([width]×[height]); [affine]
     * memetakan titik frame deteksi (u, v ternormalisasi) ke piksel keluaran (lihat
     * [MeshRenderer.draw]).
     */
    public fun draw(affine: FloatArray, width: Int, height: Int, targetFbo: Int) {
        val current = effect ?: return
        if (heldFrame < 0 || heldFaces.isEmpty()) return
        renderer.draw(current, heldFaces, affine, width, height, targetFbo, heldTimestampMs)
    }

    /** Titik wajah frame yang dideteksi (callback [FaceDetector]; tag dari [push]). */
    public fun onFaces(faces: List<FloatArray>, tag: Any?) {
        if (detector == null) return
        val frame = tag as DetectTag
        sync.add(FrameResult(frame.frame, faces, frame.flipped))
    }

    public fun release() {
        renderer.release()
        beautyPass.release()
        freeFrames()
        if (copyProgram != 0) GLES20.glDeleteProgram(copyProgram)
        copyProgram = 0
    }

    private fun allocate(width: Int, height: Int) {
        freeFrames()
        this.width = width
        this.height = height
        GLES20.glGenTextures(ringTextures.size, ringTextures, 0)
        GLES20.glGenFramebuffers(ringFbos.size, ringFbos, 0)
        for (i in ringTextures.indices) attach(ringTextures[i], ringFbos[i], width, height)
        sync.reset()
    }

    /** Ukuran frame deteksi berasio [aspect]: sisi pendek READ_SHORT_SIDE piksel (cukup untuk MediaPipe). */
    private fun readSize(aspect: Float): Pair<Int, Int> {
        val a = aspect.coerceIn(0.1f, 10f)
        return if (a >= 1f) (READ_SHORT_SIDE * a).roundToInt() to READ_SHORT_SIDE.toInt()
        else READ_SHORT_SIDE.toInt() to (READ_SHORT_SIDE / a).roundToInt()
    }

    private fun allocateRead(w: Int, h: Int) {
        if (readFbo != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(readTexture), 0)
            GLES20.glDeleteFramebuffers(1, intArrayOf(readFbo), 0)
        }
        readWidth = w
        readHeight = h
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        readTexture = ids[0]
        GLES20.glGenFramebuffers(1, ids, 0)
        readFbo = ids[0]
        attach(readTexture, readFbo, readWidth, readHeight)
        readBuffer = ByteBuffer.allocateDirect(readWidth * readHeight * 4).order(ByteOrder.nativeOrder())
        readBitmap = null
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

    private fun freeFrames() {
        if (ringTextures[0] != 0) {
            GLES20.glDeleteTextures(ringTextures.size, ringTextures, 0)
            GLES20.glDeleteFramebuffers(ringFbos.size, ringFbos, 0)
            ringTextures.fill(0)
            ringFbos.fill(0)
        }
        if (readFbo != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(readTexture), 0)
            GLES20.glDeleteFramebuffers(1, intArrayOf(readFbo), 0)
            readFbo = 0
        }
    }

    /** Gambar [source] ke [fbo] ([w]×[h]) lewat matriks tekstur [texMatrix]. */
    private fun copy(source: Int, fbo: Int, w: Int, h: Int, texMatrix: FloatArray) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, w, h)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(copyProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D, source)
        GLES20.glUniformMatrix4fv(cTexMatrix, 1, false, texMatrix, 0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(cPosition, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(cPosition)
        vertices.position(2)
        GLES20.glVertexAttribPointer(cTexCoord, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(cTexCoord)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(cPosition)
        GLES20.glDisableVertexAttribArray(cTexCoord)
    }

    /**
     * Salin frame tegak kecil ke [readFbo] lalu kirim ke [detector]. Baris pertama yang dibaca GL =
     * bawah gambar, jadi sumbu y dibalik di sini agar bitmap tegak.
     */
    private fun readFrame(detector: FaceDetector, source: Int, detectMatrix: FloatArray, flipped: Boolean, frame: Long) {
        val buffer = readBuffer ?: return
        val matrix = FloatArray(16)
        android.opengl.Matrix.multiplyMM(matrix, 0, detectMatrix, 0, FLIP_Y, 0)
        copy(source, readFbo, readWidth, readHeight, matrix)
        buffer.position(0)
        GLES20.glReadPixels(0, 0, readWidth, readHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        // Bitmap dipakai ulang: aman karena deteksi sebelumnya sudah selesai (wantsFrame).
        val bitmap = readBitmap?.takeIf { it.width == readWidth && it.height == readHeight }
            ?: Bitmap.createBitmap(readWidth, readHeight, Bitmap.Config.ARGB_8888).also { readBitmap = it }
        buffer.position(0)
        bitmap.copyPixelsFromBuffer(buffer)
        detector.submit(bitmap, DetectTag(flipped, frame))
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

    public companion object {
        /**
         * Frame terbanyak yang ditahan menunggu hasil deteksi (±200 ms pada 60 fps). Cincin =
         * MAX_DELAY + 1 tekstur seukuran sumber, dibuat saat efek wajah dipakai.
         */
        public const val MAX_DELAY: Int = 12

        /** Sisi pendek frame deteksi (piksel). */
        private const val READ_SHORT_SIDE = 320f

        /** Matriks satuan 4×4 (kolom-mayor). */
        public val IDENTITY: FloatArray = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

        /** t -> 1 − t (kolom-mayor). */
        private val FLIP_Y = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f)

        private const val COPY_VERTEX = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val COPY_FRAGMENT = """
            precision mediump float;
            uniform sampler2D sTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """

        private const val COPY_FRAGMENT_EXTERNAL = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}

/** Keterangan frame deteksi: apakah dibalik dari sumber, dan nomor frame. */
internal class DetectTag(val flipped: Boolean, val frame: Long)

