package com.zinmedia.camera.gl

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
 * setiap output (preview, encoder video). Semua kerja GL berjalan di satu thread khusus.
 */
internal class FilterProcessor : SurfaceProcessor {

    private val thread = HandlerThread("zm-camera-gl").apply { start() }
    private val handler = Handler(thread.looper)
    val executor: Executor = Executor { handler.post(it) }

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

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
        input = texture
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
            input?.release()
            input = null
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (program != 0) GLES20.glDeleteProgram(program)
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

        for ((output, eglSurface) in outputs) {
            EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
            GLES20.glViewport(0, 0, output.size.width, output.size.height)
            output.updateTransformMatrix(outMatrix, texMatrix)
            draw()
            EGLExt.eglPresentationTimeANDROID(display, eglSurface, texture.timestamp)
            EGL14.eglSwapBuffers(display, eglSurface)
        }
    }

    private fun draw() {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)

        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, outMatrix, 0)
        // GLSL mat3 kolom-mayor (ES 2.0 tidak mendukung transpose=true): transpos manual.
        for (row in 0 until 3) for (col in 0 until 3) colorMatrix[col * 3 + row] = params.matrix[row * 3 + col]
        GLES20.glUniformMatrix3fv(uColorMatrix, 1, false, colorMatrix, 0)
        GLES20.glUniform3fv(uColorOffset, 1, params.offset, 0)
        val lut = uploadedLut
        GLES20.glUniform1f(uLutSize, (lut?.size ?: 2).toFloat())
        GLES20.glUniform1f(uLutMix, if (lut != null) 1f else 0f)
        GLES20.glUniform1f(uSmooth, params.smoothing)
        GLES20.glUniform2f(uTexel, 1f / inputWidth, 1f / inputHeight)

        vertices.position(0)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(aPosition)
        vertices.position(2)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
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

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
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

        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            uniform sampler2D sLut;
            uniform mat3 uColorMatrix;
            uniform vec3 uColorOffset;
            uniform float uLutSize;
            uniform float uLutMix;
            uniform float uSmooth;
            uniform vec2 uTexel;
            varying vec2 vTexCoord;

            vec3 applyLut(vec3 c) {
                float n = uLutSize;
                float blue = c.b * (n - 1.0);
                float b0 = floor(blue);
                float b1 = min(b0 + 1.0, n - 1.0);
                float x = c.r * (n - 1.0) + 0.5;
                float y = (c.g * (n - 1.0) + 0.5) / n;
                vec3 c0 = texture2D(sLut, vec2((b0 * n + x) / (n * n), y)).rgb;
                vec3 c1 = texture2D(sLut, vec2((b1 * n + x) / (n * n), y)).rgb;
                return mix(c0, c1, blue - b0);
            }

            // Penghalus kulit sederhana: rata-rata tetangga yang warnanya mirip (bilateral ringan),
            // sehingga tepi tetap tajam.
            vec3 smoothSkin(vec3 c) {
                vec3 sum = c;
                float total = 1.0;
                for (int i = 0; i < 8; i++) {
                    float a = float(i) * 0.785398;
                    vec2 offset = vec2(cos(a), sin(a)) * uTexel * 3.0;
                    vec3 s = texture2D(sTexture, vTexCoord + offset).rgb;
                    float w = max(0.0, 1.0 - distance(s, c) * 6.0);
                    sum += s * w;
                    total += w;
                }
                vec3 blurred = sum / total;
                return mix(c, blurred, uSmooth) + uSmooth * 0.03;
            }

            void main() {
                vec3 c = texture2D(sTexture, vTexCoord).rgb;
                if (uSmooth > 0.0) c = smoothSkin(c);
                c = clamp(uColorMatrix * c + uColorOffset, 0.0, 1.0);
                if (uLutMix > 0.0) c = mix(c, applyLut(c), uLutMix);
                gl_FragColor = vec4(c, 1.0);
            }
        """
    }
}

private const val TAG = "FilterProcessor"

private fun floatBuffer(vararg values: Float): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(values)
        position(0)
    }
