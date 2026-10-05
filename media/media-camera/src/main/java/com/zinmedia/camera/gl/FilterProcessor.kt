package com.zinmedia.camera.gl

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import com.zinmedia.effects.gl.CubeLut
import com.zinmedia.effects.gl.EffectShaders
import com.zinmedia.effects.gl.FaceEffectEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

/** Parameter shader; diganti dari thread UI, dibaca di thread GL saat frame berikutnya. */
internal data class FilterParams(
    val matrix: FloatArray,
    val offset: FloatArray,
    val lut: CubeLut?,
    /** 0 = mati, 1 = penghalus kulit penuh. */
    val smoothing: Float,
)

/** Efek CameraX: filter yang sama untuk preview dan rekaman video. */
internal class FilterEffect(processor: FilterProcessor) : CameraEffect(
    PREVIEW or VIDEO_CAPTURE,
    processor.executor,
    processor,
    Consumer<Throwable> { Log.e(TAG, "Efek kamera gagal", it) },
)

/**
 * Pemroses frame kamera di GPU. Input dari kamera (tekstur OES) digambar dengan shader filter ke
 * setiap output (preview, encoder video, siaran), lalu efek wajah ([faceEngine]) di atasnya. Dengan
 * efek wajah aktif, frame ditahan sampai titik wajahnya siap; cap waktu frame ikut ditahan, sehingga
 * rekaman tetap sinkron dengan audio. Semua kerja GL berjalan di satu thread khusus.
 */
internal class FilterProcessor : SurfaceProcessor {

    private val thread = HandlerThread("zm-camera-gl").apply { start() }
    private val handler = Handler(thread.looper)
    val executor: Executor = Executor { handler.post(it) }

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    /** Program filter untuk satu jenis sumber (OES kamera, atau tekstur 2D frame yang ditahan). */
    private class FilterProgram(val program: Int) {
        val aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        val aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        val uTexMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
        val uColorMatrix = GLES20.glGetUniformLocation(program, "uColorMatrix")
        val uColorOffset = GLES20.glGetUniformLocation(program, "uColorOffset")
        val uLutSize = GLES20.glGetUniformLocation(program, "uLutSize")
        val uLutMix = GLES20.glGetUniformLocation(program, "uLutMix")
        val uSmooth = GLES20.glGetUniformLocation(program, "uSmooth")
        val uTexel = GLES20.glGetUniformLocation(program, "uTexel")

        init {
            GLES20.glUseProgram(program)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sLut"), 1)
        }
    }

    private var oesProgram: FilterProgram? = null
    private var heldProgram: FilterProgram? = null

    /** Efek wajah: deteksi dari frame kamera, penahan frame, dan penggambar. */
    val faceEngine = FaceEffectEngine(external = true)

    /** Matriks tekstur & cap waktu per frame yang mungkin ditahan (indeks = nomor frame % ukuran). */
    private val frameMatrices = Array(FaceEffectEngine.MAX_DELAY + 1) { FloatArray(16) }
    private val frameTimestamps = LongArray(FaceEffectEngine.MAX_DELAY + 1)
    private var oesTexture = 0
    private var lutTexture = 0
    private var maxTextureSize = 0

    private var input: SurfaceTexture? = null
    private var inputWidth = 1
    private var inputHeight = 1
    private val outputs = LinkedHashMap<SurfaceOutput, EGLSurface>()

    @Volatile
    private var pending: FilterParams? = null
    private var params = FilterParams(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), FloatArray(3), null, 0f)
    private var uploadedLut: CubeLut? = null
    private var released = false

    private val texMatrix = FloatArray(16)
    private val outMatrix = FloatArray(16)
    private val colorMatrix = FloatArray(9)
    private val vertices: FloatBuffer = floatBuffer(
        // x, y, u, v (dua segitiga penuh layar)
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f,
    )

    init {
        handler.post { initGl() }
    }

    /** Ganti filter; berlaku mulai frame berikutnya. */
    fun setParams(params: FilterParams) {
        pending = params
    }

    override fun onInputSurface(request: SurfaceRequest) {
        if (released) {
            request.willNotProvideSurface()
            return
        }
        inputWidth = request.resolution.width
        inputHeight = request.resolution.height
        val texture = SurfaceTexture(oesTexture).apply {
            setDefaultBufferSize(inputWidth, inputHeight)
        }
        val surface = Surface(texture)
        request.provideSurface(surface, executor) {
            texture.setOnFrameAvailableListener(null)
            texture.release()
            surface.release()
            if (input === texture) input = null
        }
        texture.setOnFrameAvailableListener({ onFrame(it) }, handler)
        // Crop (bingkai 9:16), rotasi ke tegak, dan sensor->buffer: untuk output siaran.
        request.setTransformationInfoListener(executor) { info -> inputInfo = info }
        input = texture
    }

    // ---- output siaran (mis. encoder live) ----

    private class StreamOutput(val surface: Surface, val width: Int, val height: Int, val egl: EGLSurface)

    private val streamOutputs = ArrayList<StreamOutput>()
    private var inputInfo: SurfaceRequest.TransformationInfo? = null

    /**
     * Tambah output siaran [surface] berukuran [width]×[height]: frame tegak, bingkai sama dengan
     * preview, **tidak** di-mirror (apa adanya untuk penonton), lengkap dengan filter & efek wajah.
     */
    fun addStreamOutput(surface: Surface, width: Int, height: Int) {
        handler.post {
            if (released || display == EGL14.EGL_NO_DISPLAY || streamOutputs.any { it.surface === surface }) return@post
            val egl = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            if (egl == EGL14.EGL_NO_SURFACE) {
                Log.e(TAG, "Gagal membuat EGL surface siaran")
                return@post
            }
            streamOutputs += StreamOutput(surface, width, height, egl)
        }
    }

    fun removeStreamOutput(surface: Surface) {
        handler.post {
            streamOutputs.removeAll { output ->
                (output.surface === surface).also { match ->
                    if (match && display != EGL14.EGL_NO_DISPLAY) EGL14.eglDestroySurface(display, output.egl)
                }
            }
        }
    }

    /**
     * Matriks tekstur & sensor->output untuk output siaran tegak: koordinat output (u, v; v ke bawah)
     * -> buffer kamera lewat crop & rotasi dari [SurfaceRequest.TransformationInfo].
     */
    private fun streamTransform(info: SurfaceRequest.TransformationInfo, width: Int, height: Int): Pair<FloatArray, android.graphics.Matrix> {
        val crop = info.cropRect
        val bw = inputWidth.toFloat()
        val bh = inputHeight.toFloat()
        // Tegak ternormalisasi (u, v) -> buffer-crop ternormalisasi (bx, by).
        fun toBufferCrop(u: Float, v: Float): Pair<Float, Float> = when ((info.rotationDegrees % 360 + 360) % 360) {
            90 -> v to 1f - u
            180 -> 1f - u to 1f - v
            270 -> 1f - v to u
            else -> u to v
        }
        // (s, t) tekstur standar (t ke atas) dari titik output.
        fun texAt(u: Float, v: Float): Pair<Float, Float> {
            val (bx, by) = toBufferCrop(u, v)
            val x = (crop.left + bx * crop.width()) / bw
            val y = (crop.top + by * crop.height()) / bh
            return x to 1f - y
        }
        // Output (s, t) dengan t ke atas: (0,0)=kiri-bawah -> v = 1.
        val (ox, oy) = texAt(0f, 1f)
        val (sx, sy) = texAt(1f, 1f)
        val (tx, ty) = texAt(0f, 0f)
        val base = floatArrayOf(
            sx - ox, sy - oy, 0f, 0f,
            tx - ox, ty - oy, 0f, 0f,
            0f, 0f, 1f, 0f,
            ox, oy, 0f, 1f,
        )
        // Crop & rotasi CameraX mengacu ke buffer mentah. Matriks SurfaceTexture juga memuat rotasi
        // bawaan perangkat (berbeda kamera depan/belakang) yang di sini tidak boleh ikut: cukup
        // pembalikan sumbu y standar (baris pertama buffer = atas gambar).
        val tex = FloatArray(16)
        android.opengl.Matrix.multiplyMM(tex, 0, RawBufferTexMatrix, 0, base, 0)

        // sensor -> buffer (piksel) -> tegak ternormalisasi -> piksel output.
        val toOutput = android.graphics.Matrix(info.sensorToBufferTransform)
        toOutput.postTranslate(-crop.left.toFloat(), -crop.top.toFloat())
        toOutput.postScale(1f / crop.width(), 1f / crop.height())
        val inverseRotation = android.graphics.Matrix().apply {
            setValues(
                when ((info.rotationDegrees % 360 + 360) % 360) {
                    90 -> floatArrayOf(0f, -1f, 1f, 1f, 0f, 0f, 0f, 0f, 1f)
                    180 -> floatArrayOf(-1f, 0f, 1f, 0f, -1f, 1f, 0f, 0f, 1f)
                    270 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 1f, 0f, 0f, 1f)
                    else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
                }
            )
        }
        toOutput.postConcat(inverseRotation)
        toOutput.postScale(width.toFloat(), height.toFloat())
        return tex to toOutput
    }

    override fun onOutputSurface(output: SurfaceOutput) {
        if (released) {
            output.close()
            return
        }
        val surface = output.getSurface(executor) {
            outputs.remove(output)?.let { EGL14.eglDestroySurface(display, it) }
            output.close()
        }
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "Gagal membuat EGL surface output")
            output.close()
            return
        }
        outputs[output] = eglSurface
    }

    fun release() {
        handler.post {
            released = true
            outputs.forEach { (output, surface) ->
                EGL14.eglDestroySurface(display, surface)
                output.close()
            }
            outputs.clear()
            streamOutputs.forEach { EGL14.eglDestroySurface(display, it.egl) }
            streamOutputs.clear()
            input?.release()
            input = null
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                oesProgram?.let { GLES20.glDeleteProgram(it.program) }
                heldProgram?.let { GLES20.glDeleteProgram(it.program) }
                faceEngine.release()
                EGL14.eglDestroySurface(display, pbuffer)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
            display = EGL14.EGL_NO_DISPLAY
            thread.quitSafely()
        }
    }

    private fun onFrame(texture: SurfaceTexture) {
        if (released || texture !== input) return
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        texture.updateTexImage()
        texture.getTransformMatrix(texMatrix)
        pending?.let {
            pending = null
            params = it
        }
        if (params.lut !== uploadedLut) uploadLut(params.lut)

        // Efek wajah: simpan frame, deteksi frame tegak, lalu gambar frame yang ditahan.
        val info = inputInfo
        var source = oesTexture
        var external = true
        var frameMatrix = texMatrix
        var timestamp = texture.timestamp
        var uprightToSensor: android.graphics.Matrix? = null
        if (info != null) {
            val (detect, sensorToUpright) = streamTransform(info, 1, 1)
            val held = faceEngine.push(
                oesTexture, inputWidth, inputHeight,
                detectMatrix = detect, detectAspect = uprightAspect(info), flipped = false,
                restoreFbo = 0, restoreWidth = 1, restoreHeight = 1,
            )
            if (faceEngine.heldFrame >= 0) {
                val slot = (faceEngine.pushedFrame % frameMatrices.size).toInt()
                texMatrix.copyInto(frameMatrices[slot])
                frameTimestamps[slot] = texture.timestamp
                val heldSlot = (faceEngine.heldFrame % frameMatrices.size).toInt()
                source = held
                external = false
                frameMatrix = frameMatrices[heldSlot]
                timestamp = frameTimestamps[heldSlot]
                uprightToSensor = android.graphics.Matrix().also { sensorToUpright.invert(it) }
            }
        }

        for ((output, eglSurface) in outputs) {
            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
            GLES20.glViewport(0, 0, output.size.width, output.size.height)
            output.updateTransformMatrix(outMatrix, frameMatrix)
            draw(source, external)
            uprightToSensor?.let { toSensor ->
                // Frame tegak (ternormalisasi) -> sensor -> buffer output (rotasi, crop, cermin ikut).
                val toOutput = android.graphics.Matrix(toSensor).apply { postConcat(output.sensorToBufferTransform) }
                faceEngine.draw(affine(toOutput), output.size.width, output.size.height, 0)
            }
            EGLExt.eglPresentationTimeANDROID(display, eglSurface, timestamp)
            EGL14.eglSwapBuffers(display, eglSurface)
        }

        if (info != null) {
            for (stream in streamOutputs) {
                streamTransform(info, stream.width, stream.height).first.copyInto(outMatrix)
                EGL14.eglMakeCurrent(display, stream.egl, stream.egl, context)
                GLES20.glViewport(0, 0, stream.width, stream.height)
                draw(source, external)
                // Siaran: frame tegak, tidak di-mirror, bingkai sama dengan frame deteksi.
                faceEngine.draw(floatArrayOf(stream.width.toFloat(), 0f, 0f, stream.height.toFloat(), 0f, 0f), stream.width, stream.height, 0)
                EGLExt.eglPresentationTimeANDROID(display, stream.egl, timestamp)
                EGL14.eglSwapBuffers(display, stream.egl)
            }
        }
    }

    /** Rasio lebar/tinggi frame tegak (crop setelah rotasi). */
    private fun uprightAspect(info: SurfaceRequest.TransformationInfo): Float {
        val crop = info.cropRect
        val turned = (info.rotationDegrees % 180 + 180) % 180 == 90
        return if (turned) crop.height().toFloat() / crop.width() else crop.width().toFloat() / crop.height()
    }

    /** android.graphics.Matrix -> (a, b, c, d, tx, ty) untuk [FaceEffectEngine.draw]. */
    private fun affine(m: android.graphics.Matrix): FloatArray {
        val v = FloatArray(9).also(m::getValues)
        return floatArrayOf(
            v[android.graphics.Matrix.MSCALE_X], v[android.graphics.Matrix.MSKEW_Y],
            v[android.graphics.Matrix.MSKEW_X], v[android.graphics.Matrix.MSCALE_Y],
            v[android.graphics.Matrix.MTRANS_X], v[android.graphics.Matrix.MTRANS_Y],
        )
    }

    /** Gambar [source] (OES kamera bila [external], selain itu tekstur 2D) dengan filter ke output aktif. */
    private fun draw(source: Int, external: Boolean) {
        val p = (if (external) oesProgram else heldProgram) ?: return
        GLES20.glUseProgram(p.program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D, source)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)

        GLES20.glUniformMatrix4fv(p.uTexMatrix, 1, false, outMatrix, 0)
        // GLSL mat3 kolom-mayor (ES 2.0 tidak mendukung transpose=true): transpos manual.
        for (row in 0 until 3) for (col in 0 until 3) colorMatrix[col * 3 + row] = params.matrix[row * 3 + col]
        GLES20.glUniformMatrix3fv(p.uColorMatrix, 1, false, colorMatrix, 0)
        GLES20.glUniform3fv(p.uColorOffset, 1, params.offset, 0)
        val lut = uploadedLut
        GLES20.glUniform1f(p.uLutSize, (lut?.size ?: 2).toFloat())
        GLES20.glUniform1f(p.uLutMix, if (lut != null) 1f else 0f)
        GLES20.glUniform1f(p.uSmooth, params.smoothing)
        GLES20.glUniform2f(p.uTexel, 1f / inputWidth, 1f / inputHeight)

        vertices.position(0)
        GLES20.glVertexAttribPointer(p.aPosition, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(p.aPosition)
        vertices.position(2)
        GLES20.glVertexAttribPointer(p.aTexCoord, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(p.aTexCoord)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(p.aPosition)
        GLES20.glDisableVertexAttribArray(p.aTexCoord)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
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

    private fun initGl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize gagal" }
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            // Wajib agar bisa menggambar ke surface encoder video.
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "eglChooseConfig gagal"
        }
        config = configs[0]
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        pbuffer = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
        )
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)

        oesProgram = FilterProgram(createProgram(EffectShaders.VERTEX, EffectShaders.filterFragment(external = true)))
        heldProgram = FilterProgram(createProgram(EffectShaders.VERTEX, EffectShaders.filterFragment(external = false)))

        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        oesTexture = textures[0]
        lutTexture = textures[1]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        setTextureParams(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)
        setTextureParams(GLES20.GL_TEXTURE_2D)
        // Tekstur LUT kosong 1×1 agar sampler selalu valid.
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE,
            ByteBuffer.allocateDirect(4),
        )
        val max = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0)
        maxTextureSize = max[0]
        faceEngine.init()
    }

    private fun setTextureParams(target: Int) {
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
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
        const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}

private const val TAG = "FilterProcessor"

/** Matriks tekstur buffer mentah: hanya membalik sumbu y (kolom-mayor). */
private val RawBufferTexMatrix = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, -1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 1f, 0f, 1f,
)

private fun floatBuffer(vararg values: Float): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(values)
        position(0)
    }
