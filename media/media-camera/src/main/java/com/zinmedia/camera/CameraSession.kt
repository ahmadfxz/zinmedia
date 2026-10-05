package com.zinmedia.camera

import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.ImageAnalysis
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.zinmedia.camera.gl.FilterEffect
import com.zinmedia.camera.gl.FilterParams
import com.zinmedia.camera.gl.FilterProcessor
import com.zinmedia.effects.gl.loadCubeLut
import java.io.File

/**
 * Kamera CameraX: preview + perekam video dengan filter GPU, kontrol lensa/zoom/fokus/senter,
 * foto dari frame preview (sudah terfilter), dan rekaman per segmen.
 */
internal class CameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    val previewView: PreviewView,
    /** `false` untuk live: tanpa perekam video (encoder dipakai pengirim siaran). */
    private val withVideoCapture: Boolean = true,
    /** Pasang analisis frame (efek wajah) saat diikat; live selalu, kamera biasa bila ada efek. */
    private val wantsAnalysis: Boolean = CameraConfig.faceEffects.isNotEmpty(),
) {
    private val processor = FilterProcessor()
    private val effect = FilterEffect(processor)
    private val mainExecutor = ContextCompat.getMainExecutor(context)

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var faceAnalyzer: ImageAnalysis.Analyzer? = null
    /**
     * Analisis frame dipasang sekali saat kamera diikat (hanya bila aplikasi punya efek wajah),
     * agar memilih/menghapus efek tidak mengikat ulang kamera (layar berkedip hitam).
     */
    private var imageAnalysis: ImageAnalysis? = null
    private val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    var lensFacing by mutableIntStateOf(CameraSelector.LENS_FACING_BACK)
        private set
    var hasFrontAndBack by mutableStateOf(false)
        private set
    var hasFlashUnit by mutableStateOf(false)
        private set
    var zoomRatio by mutableFloatStateOf(1f)
        private set
    var minZoom by mutableFloatStateOf(1f)
        private set
    var maxZoom by mutableFloatStateOf(1f)
        private set

    val isFront: Boolean get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    suspend fun start() {
        val provider = ProcessCameraProvider.awaitInstance(context)
        this.provider = provider
        hasFrontAndBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) &&
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) lensFacing = CameraSelector.LENS_FACING_FRONT
        bind()
    }

    fun flipCamera() {
        if (!hasFrontAndBack) return
        lensFacing = if (isFront) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        bind()
    }

    private fun bind() {
        val provider = provider ?: return
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
        val video = if (!withVideoCapture) null else VideoCapture.Builder(recorder)
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()
        // Analisis frame (efek wajah) hanya dipasang saat efek aktif: frame kecil, hanya yang terbaru.
        val analysis = if (!wantsAnalysis) null else run {
            ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(640, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        )
                        .build()
                )
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
        }
        fun group(withAnalysis: Boolean) = UseCaseGroup.Builder()
            .addUseCase(preview)
            .apply { if (video != null) addUseCase(video) }
            .apply { if (withAnalysis && analysis != null) addUseCase(analysis) }
            .addEffect(effect)
            .apply { previewView.viewPort?.let(::setViewPort) }
            .build()
        provider.unbindAll()
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        camera = try {
            provider.bindToLifecycle(lifecycleOwner, selector, group(withAnalysis = true)).also {
                imageAnalysis = analysis
            }
        } catch (e: IllegalArgumentException) {
            imageAnalysis = null
            // Sebagian HP tidak sanggup preview + rekam + analisis sekaligus: kamera tetap jalan tanpa efek wajah.
            Log.w(TAG, "Kombinasi kamera tidak didukung, tanpa analisis wajah", e)
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, group(withAnalysis = false))
            } catch (e2: IllegalArgumentException) {
                Log.e(TAG, "Gagal memakai kamera", e2)
                null
            }
        }
        videoCapture = video
        // Ikat ulang (mis. balik kamera): pasang lagi penganalisis efek yang aktif.
        applyAnalyzer()
        camera?.cameraInfo?.let { info ->
            hasFlashUnit = info.hasFlashUnit()
            info.zoomState.value?.let {
                minZoom = it.minZoomRatio
                maxZoom = it.maxZoomRatio
            }
            zoomRatio = 1f
        }
    }

    fun setZoom(ratio: Float) {
        val clamped = ratio.coerceIn(minZoom, maxZoom)
        zoomRatio = clamped
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    fun focusAt(x: Float, y: Float) {
        val point = previewView.meteringPointFactory.createPoint(x, y)
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
    }

    /** Pasang/lepas penganalisis frame untuk efek wajah (kamera diikat ulang). */
    fun setFaceAnalyzer(analyzer: ImageAnalysis.Analyzer?) {
        faceAnalyzer = analyzer
        applyAnalyzer()
    }

    /** Pasang/lepas penganalisis tanpa mengikat ulang kamera; tanpa penganalisis frame dibuang. */
    private fun applyAnalyzer() {
        val analysis = imageAnalysis ?: return
        val analyzer = faceAnalyzer
        if (analyzer != null) analysis.setAnalyzer(analysisExecutor, analyzer) else analysis.clearAnalyzer()
    }

    /** Output siaran (mis. encoder live): frame tegak, tidak di-mirror, dengan filter & efek. */
    fun addStreamOutput(surface: android.view.Surface, width: Int, height: Int) =
        processor.addStreamOutput(surface, width, height)

    fun removeStreamOutput(surface: android.view.Surface) = processor.removeStreamOutput(surface)

    fun setFaceOverlayImage(bitmap: android.graphics.Bitmap?) = processor.setOverlayImage(bitmap)

    fun setFaceQuads(quads: List<com.zinmedia.camera.face.FaceQuad>) {
        processor.faceQuads = quads
    }

    fun setTorch(on: Boolean) {
        if (hasFlashUnit) camera?.cameraControl?.enableTorch(on)
    }

    /** Terapkan filter (LUT diunduh dulu bila perlu). */
    suspend fun applyFilter(filter: CameraFilter, smoothing: Float) {
        val lut = filter.lutUrl?.let { loadCubeLut(it) }
        processor.setParams(FilterParams(filter.matrix, filter.offset, lut, smoothing))
    }

    /** Foto = frame preview saat ini (filter & penghalus sudah diterapkan, bingkai sama dengan layar). */
    fun capturePhoto(): Bitmap? = previewView.bitmap

    /**
     * Mulai merekam satu segmen ke [file]. [onStatus] menerima durasi rekaman berjalan (ms);
     * [onFinished] dipanggil dengan durasi akhir, atau `null` bila gagal.
     */
    @SuppressLint("MissingPermission")
    fun startSegment(
        file: File,
        withAudio: Boolean,
        onStatus: (Long) -> Unit,
        onFinished: (Long?) -> Unit,
    ): Recording? {
        val capture = videoCapture ?: return null
        return capture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            .apply { if (withAudio) withAudioEnabled() }
            .start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Status -> onStatus(event.recordingStats.recordedDurationNanos / 1_000_000)
                    is VideoRecordEvent.Finalize -> {
                        val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                        if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED) {
                            Log.w(TAG, "Rekaman gagal: ${event.error}", event.cause)
                            file.delete()
                            onFinished(null)
                        } else {
                            onFinished(durationMs)
                        }
                    }
                }
            }
    }

    fun release() {
        provider?.unbindAll()
        analysisExecutor.shutdown()
        processor.release()
    }

    private companion object {
        const val TAG = "CameraSession"
    }
}
