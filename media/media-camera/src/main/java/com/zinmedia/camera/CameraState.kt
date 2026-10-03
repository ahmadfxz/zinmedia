package com.zinmedia.camera

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.camera.video.Recording
import com.zinmedia.composer.AllowedMedia
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Mode tombol rana: foto, atau video dengan batas durasi. */
internal enum class CaptureMode(val maxMs: Long) {
    Photo(0),
    Video15(15_000),
    Video30(30_000),
    Video60(60_000),
}

internal val Speeds = listOf(0.3f, 0.5f, 1f, 2f, 3f)
internal val TimerOptions = listOf(0, 3, 10)

/** Kekuatan penghalus kulit saat aktif. */
private const val SmoothingStrength = 0.6f

/** Segmen lebih pendek dari ini dianggap ketukan tak sengaja dan dibuang. */
private const val MinSegmentMs = 300L

/**
 * State & aksi layar kamera: pilihan (mode, filter, kecepatan, timer, flash, grid), rekaman
 * bersegmen, foto, dan proses akhir (gabung segmen) sebelum dibuka di editor.
 */
internal class CameraState(
    private val context: Context,
    val session: CameraSession,
    private val scope: CoroutineScope,
    private val audioEnabled: () -> Boolean,
    private val onCaptured: (List<Uri>) -> Unit,
    allowedMedia: AllowedMedia = AllowedMedia.All,
    /** Batas foto yang ditampung sebelum ke editor; 1 = langsung ke editor. */
    private val maxPhotos: Int = 1,
) {
    val filters: List<CameraFilter> = cameraFilters()

    /** Mode rana yang tersedia sesuai jenis media yang diizinkan aplikasi. */
    val modes: List<CaptureMode> = when (allowedMedia) {
        AllowedMedia.All -> listOf(CaptureMode.Video15, CaptureMode.Video60, CaptureMode.Video30, CaptureMode.Photo)
        AllowedMedia.Image -> listOf(CaptureMode.Photo)
        AllowedMedia.Video -> listOf(CaptureMode.Video15, CaptureMode.Video60, CaptureMode.Video30)
    }

    /** Default: video 1 menit (atau Foto bila hanya foto yang diizinkan). */
    var mode by mutableStateOf(if (allowedMedia == AllowedMedia.Image) CaptureMode.Photo else CaptureMode.Video60)
        private set
    var filterIndex by mutableIntStateOf(0)
        private set
    var smoothing by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1f)
    var timerSeconds by mutableIntStateOf(0)
        private set
    var flash by mutableStateOf(false)
        private set
    var grid by mutableStateOf(false)
        private set
    var showSpeed by mutableStateOf(false)
    var showFilters by mutableStateOf(false)

    val segments = mutableStateListOf<Segment>()
    /** Foto yang sudah diambil (mode Foto, bila [maxPhotos] > 1). */
    val photos = mutableStateListOf<File>()
    val photoLimit: Int get() = maxPhotos
    /** Bertambah tiap foto diambil; dipakai untuk efek kedip rana. */
    var photoTakenCount by mutableIntStateOf(0)
        private set
    /** Sedang mengambil/menyimpan foto. */
    var capturing by mutableStateOf(false)
        private set
    var isRecording by mutableStateOf(false)
        private set
    /** Durasi segmen yang sedang direkam (ms, sebelum kecepatan). */
    var currentSegmentMs by mutableLongStateOf(0)
        private set
    /** Hitung mundur timer yang sedang berjalan, `null` bila tidak ada. */
    var countdown by mutableStateOf<Int?>(null)
        private set
    /** Layar putih untuk kamera depan (flash layar) saat memotret. */
    var screenFlash by mutableStateOf(false)
        private set
    /** Progres penggabungan segmen (0–1), `null` bila tidak sedang memproses. */
    var processing by mutableStateOf<Float?>(null)
        private set

    val filter: CameraFilter get() = filters[filterIndex]

    /** Total durasi hasil (ms), termasuk segmen yang sedang direkam. */
    val totalOutputMs: Long by derivedStateOf {
        segments.sumOf { it.outputMs } + (currentSegmentMs / speed).toLong()
    }

    val isBusy: Boolean get() = isRecording || countdown != null || processing != null || capturing

    /** Sudah ada klip video atau foto yang ditampung. */
    val hasCaptures: Boolean get() = segments.isNotEmpty() || photos.isNotEmpty()

    private var recording: Recording? = null
    private var countdownJob: Job? = null
    private var finishAfterRecording = false
    private val outputDir = File(context.cacheDir, "zinmedia/camera").apply { mkdirs() }

    init {
        applyFilter()
    }

    // ---- pilihan ----

    fun selectMode(newMode: CaptureMode) {
        // Mode tidak bisa diganti setelah ada segmen (durasi maksimum sudah dipakai).
        if (isBusy || hasCaptures || newMode !in modes) return
        mode = newMode
        if (newMode == CaptureMode.Photo) showSpeed = false
    }

    fun selectFilter(index: Int) {
        filterIndex = index.mod(filters.size)
        applyFilter()
    }

    fun toggleSmoothing() {
        smoothing = !smoothing
        applyFilter()
    }

    fun cycleTimer() {
        timerSeconds = TimerOptions[(TimerOptions.indexOf(timerSeconds) + 1) % TimerOptions.size]
    }

    fun toggleFlash() {
        flash = !flash
        // Video kamera belakang: senter menyala terus selama merekam saja.
        if (!flash) session.setTorch(false)
    }

    fun toggleGrid() {
        grid = !grid
    }

    fun flipCamera() {
        if (isRecording) return
        session.flipCamera()
    }

    private fun applyFilter() {
        val selected = filter
        val strength = if (smoothing) SmoothingStrength else 0f
        scope.launch { session.applyFilter(selected, strength) }
    }

    // ---- rana ----

    /** Ketuk rana: foto, atau mulai/hentikan video (dengan timer bila diatur). */
    fun onShutterTap() {
        when {
            countdown != null -> cancelCountdown()
            mode == CaptureMode.Photo -> withTimer { takePhoto() }
            isRecording -> stopRecording()
            else -> withTimer { startRecording() }
        }
    }

    /** Tahan rana (mode video): rekam selama ditahan. */
    fun onShutterHoldStart() {
        if (mode != CaptureMode.Photo && !isBusy) startRecording()
    }

    fun onShutterHoldEnd() {
        if (isRecording) stopRecording()
    }

    private fun withTimer(action: () -> Unit) {
        if (timerSeconds == 0) {
            action()
            return
        }
        countdownJob = scope.launch {
            for (second in timerSeconds downTo 1) {
                countdown = second
                delay(1000)
            }
            countdown = null
            action()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdown = null
    }

    // ---- video ----

    private fun startRecording() {
        if (isRecording || totalOutputMs >= mode.maxMs) return
        val file = File(outputDir, "segment_${System.currentTimeMillis()}.mp4")
        val segmentSpeed = speed
        if (flash && !session.isFront) session.setTorch(true)
        showSpeed = false
        showFilters = false
        recording = session.startSegment(
            file = file,
            withAudio = audioEnabled(),
            onStatus = { recordedMs ->
                currentSegmentMs = recordedMs
                if (totalOutputMs >= mode.maxMs) {
                    // Batas durasi tercapai: hentikan lalu langsung selesai.
                    finishAfterRecording = true
                    stopRecording()
                }
            },
            onFinished = { recordedMs ->
                if (recordedMs != null && recordedMs >= MinSegmentMs) {
                    segments += Segment(file, segmentSpeed, recordedMs)
                } else {
                    file.delete()
                }
                currentSegmentMs = 0
                isRecording = false
                if (finishAfterRecording) {
                    finishAfterRecording = false
                    finish()
                }
            },
        )
        isRecording = recording != null
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        session.setTorch(false)
    }

    fun deleteLastSegment() {
        if (isBusy) return
        segments.removeLastOrNull()?.file?.delete()
    }

    /** Selesai: buka foto yang ditampung, atau gabungkan segmen video, di editor. */
    fun finish() {
        if (photos.isNotEmpty()) {
            if (!capturing) onCaptured(photos.map { Uri.fromFile(it) })
            return
        }
        if (isRecording) {
            finishAfterRecording = true
            stopRecording()
            return
        }
        if (segments.isEmpty() || processing != null) return
        processing = 0f
        val parts = segments.toList()
        scope.launch {
            try {
                val uri = mergeSegments(context, parts, audioEnabled(), outputDir) { processing = it }
                onCaptured(listOf(uri))
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menggabungkan video", e)
            } finally {
                processing = null
            }
        }
    }

    /** Hapus semua klip & foto (mis. setelah hasil dikirim, atau mulai ulang). */
    fun reset() {
        segments.forEach { it.file.delete() }
        segments.clear()
        photos.forEach { it.delete() }
        photos.clear()
    }

    fun removePhoto(index: Int) {
        if (capturing) return
        photos.removeAt(index).delete()
    }

    // ---- foto ----

    /**
     * Ambil foto. Batas 1: langsung ke editor. Lebih dari 1: ditampung dulu; editor dibuka lewat
     * tombol Selesai, atau otomatis saat batas tercapai.
     */
    private fun takePhoto() {
        if (capturing || processing != null || photos.size >= maxPhotos) return
        capturing = true
        scope.launch {
            try {
                val bitmap = withFlash { session.capturePhoto() } ?: return@launch
                photoTakenCount++
                val file = File(outputDir, "photo_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JpegQuality, it) }
                    bitmap.recycle()
                }
                if (maxPhotos <= 1) {
                    onCaptured(listOf(Uri.fromFile(file)))
                } else {
                    photos += file
                    if (photos.size >= maxPhotos) onCaptured(photos.map { Uri.fromFile(it) })
                }
            } finally {
                capturing = false
            }
        }
    }

    /** Flash untuk foto: senter (kamera belakang) atau layar putih (kamera depan). */
    private suspend fun <T> withFlash(capture: () -> T): T {
        if (!flash) return capture()
        if (session.isFront) screenFlash = true else session.setTorch(true)
        // Beri waktu eksposur menyesuaikan cahaya.
        delay(FlashSettleMs)
        return try {
            capture()
        } finally {
            screenFlash = false
            session.setTorch(false)
        }
    }

    fun release() {
        cancelCountdown()
        recording?.stop()
        recording = null
    }

    private companion object {
        const val TAG = "CameraState"
        const val JpegQuality = 92
        const val FlashSettleMs = 500L
    }
}

/** Hapus file kamera lama (>24 jam) dari sesi sebelumnya. */
internal fun deleteOldCameraFiles(context: Context) {
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    File(context.cacheDir, "zinmedia/camera").listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
}
