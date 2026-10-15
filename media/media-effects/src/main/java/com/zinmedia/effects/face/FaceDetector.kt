package com.zinmedia.effects.face

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RestrictTo
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unduh model titik wajah sekali lalu simpan permanen di perangkat. [onProgress] menerima 0..1.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public suspend fun ensureFaceModel(context: Context, url: String, onProgress: (Float) -> Unit): File =
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "zinmedia/face").apply { mkdirs() }
        val file = File(dir, "face_landmarker_${url.hashCode().toUInt()}.task")
        if (file.length() > 0) return@withContext file
        val partial = File(dir, file.name + ".part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Gagal mengunduh model: ${connection.responseCode}" }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            check(partial.renameTo(file)) { "Gagal menyimpan model" }
            file
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

/**
 * Pendeteksi wajah MediaPipe. Frame tegak (tidak di-mirror) dikirim lewat [submit]; deteksi berjalan
 * asinkron (frame baru ditolak selama deteksi sebelumnya berjalan), lalu titik wajah mentah per
 * wajah dikirim ke [onFaces] beserta `tag` frame tersebut. Daftar kosong = tidak ada wajah.
 *
 * Titik wajah: [POINTS] titik × (x, y, z) berurutan; x, y pecahan lebar/tinggi frame, z (kedalaman,
 * + menjauh dari kamera) dibagi lebar frame, seperti keluaran MediaPipe.
 *
 * Di HP lambat (deteksi di CPU, atau satu deteksi lebih dari [SLOW_DETECT_MS]) deteksi diberi jeda
 * sepanjang durasinya sendiri, agar CPU tidak habis untuk wajah saja (encoder siaran, UI); frame di
 * antaranya diinterpolasi oleh `FrameSync`. [slow] menyala bila deteksi rata-rata lebih dari
 * [VERY_SLOW_DETECT_MS]: efek wajah tidak akan lancar di HP ini; [onSlowChanged] dipanggil saat
 * berubah.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FaceDetector(
    context: Context,
    modelFile: File,
    private val maxFaces: Int = 1,
    private val onFaces: (faces: List<FloatArray>, tag: Any?) -> Unit,
    private val onSlowChanged: (slow: Boolean) -> Unit = {},
) {

    /** `false` = tidak mendeteksi; mematikan/menyalakan membuang hasil yang sedang dihitung. */
    @Volatile
    public var enabled: Boolean = false
        set(value) {
            field = value
            generation++
        }

    /** Bertambah tiap [enabled] diubah; hasil yang dihitung sebelumnya dibuang. */
    @Volatile
    private var generation = 0

    private val busy = AtomicBoolean(false)
    private var lastTimestamp = 0L

    /** Deteksi berjalan di CPU (GPU tidak tersedia): selalu diberi jeda. */
    private var onCpu = false

    /** Rata-rata durasi satu deteksi (ms), dan berapa hasil yang sudah diukur. */
    @Volatile
    private var detectAvgMs = 0f
    private var measured = 0

    /** Deteksi rata-rata terlalu lambat untuk efek wajah yang lancar. */
    @Volatile
    public var slow: Boolean = false
        private set
    private var lastSubmitMs = 0L
    private var frameTag: Any? = null
    private var frameGeneration = 0
    private val landmarker: FaceLandmarker = createLandmarker(context, modelFile)

    private fun createLandmarker(context: Context, modelFile: File): FaceLandmarker {
        val buffer = RandomAccessFile(modelFile, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }
        fun build(delegate: Delegate): FaceLandmarker = FaceLandmarker.createFromOptions(
            context,
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(buffer).setDelegate(delegate).build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(maxFaces)
                .setMinFaceDetectionConfidence(0.5f)
                // Sedikit di bawah bawaan: wajah yang sudah ditemukan tidak lepas karena cahaya
                // kurang atau gambar kabur (penyebab efek berkedip di HP murah).
                .setMinFacePresenceConfidence(0.4f)
                .setMinTrackingConfidence(0.4f)
                .setOutputFaceBlendshapes(false)
                .setOutputFacialTransformationMatrixes(false)
                .setResultListener { result, _ -> onResult(result) }
                .setErrorListener { error ->
                    Log.w(TAG, "Deteksi wajah gagal", error)
                    busy.set(false)
                }
                .build(),
        )
        // GPU lebih cepat; sebagian HP (terutama murah) tidak mendukung, pakai CPU.
        return try {
            build(Delegate.GPU)
        } catch (e: Exception) {
            Log.i(TAG, "GPU tidak tersedia untuk deteksi wajah, memakai CPU", e)
            onCpu = true
            build(Delegate.CPU)
        }
    }

    /**
     * `true` bila frame berikutnya akan dideteksi: menyala, deteksi sebelumnya selesai, dan sudah
     * lewat jeda minimum (~30 deteksi/detik, hemat baterai & panas; lebih jarang di HP lambat). Cek
     * ini sebelum menyiapkan bitmap agar frame yang akan dilewati tidak perlu dibaca.
     */
    public fun wantsFrame(): Boolean =
        enabled && !busy.get() && SystemClock.uptimeMillis() - lastSubmitMs >= minIntervalMs()

    /**
     * Jeda antar deteksi: [MIN_INTERVAL_MS], atau di HP lambat sepanjang satu deteksi (CPU kira-kira
     * separuh waktu untuk wajah, separuh untuk yang lain).
     */
    private fun minIntervalMs(): Long {
        val avg = detectAvgMs
        return if (onCpu || avg > SLOW_DETECT_MS) maxOf(MIN_INTERVAL_MS, avg.toLong()) else MIN_INTERVAL_MS
    }

    /**
     * Deteksi [bitmap] (frame tegak). [bitmap] tidak boleh diubah sampai hasilnya keluar (lihat
     * [wantsFrame]). Mengembalikan `false` bila frame dilewati.
     */
    public fun submit(bitmap: Bitmap, tag: Any? = null): Boolean {
        if (!enabled || !busy.compareAndSet(false, true)) return false
        lastSubmitMs = SystemClock.uptimeMillis()
        frameTag = tag
        frameGeneration = generation
        // Timestamp harus naik terus untuk mode LIVE_STREAM.
        val timestamp = maxOf(SystemClock.uptimeMillis(), lastTimestamp + 1)
        lastTimestamp = timestamp
        return try {
            landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), timestamp)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Deteksi wajah gagal", e)
            busy.set(false)
            false
        }
    }

    private fun onResult(result: FaceLandmarkerResult) {
        val tag = frameTag
        val startGeneration = frameGeneration
        recordDuration(SystemClock.uptimeMillis() - lastSubmitMs)
        // Frame berikutnya boleh dikirim setelah nilai frame ini dibaca.
        busy.set(false)
        if (!enabled || startGeneration != generation) return
        val faces = result.faceLandmarks().take(maxFaces).filter { it.size >= POINTS }.map { landmarks ->
            FloatArray(POINTS * 3) { i ->
                val point = landmarks[i / 3]
                when (i % 3) {
                    0 -> point.x()
                    1 -> point.y()
                    else -> point.z()
                }
            }
        }
        onFaces(faces, tag)
    }

    private fun recordDuration(ms: Long) {
        val avg = if (measured == 0) ms.toFloat() else detectAvgMs + (ms - detectAvgMs) * DURATION_SMOOTHING
        detectAvgMs = avg
        measured++
        // Beberapa deteksi pertama lambat (pemanasan model): belum dinilai.
        if (measured < WARMUP) return
        val nowSlow = if (slow) avg > VERY_SLOW_DETECT_MS * 0.8f else avg > VERY_SLOW_DETECT_MS
        if (nowSlow != slow) {
            slow = nowSlow
            onSlowChanged(nowSlow)
        }
    }

    public fun close() {
        enabled = false
        landmarker.close()
    }

    public companion object {
        /** Titik wajah MediaPipe Face Mesh, termasuk iris. */
        public const val POINTS: Int = 478

        private const val TAG = "FaceDetector"
        private const val MIN_INTERVAL_MS = 33L

        /** Satu deteksi lebih lama dari ini: diberi jeda (lihat [minIntervalMs]). */
        private const val SLOW_DETECT_MS = 40f

        /** Rata-rata lebih lama dari ini: [slow] (kurang dari ±8 deteksi/detik). */
        private const val VERY_SLOW_DETECT_MS = 120f
        private const val DURATION_SMOOTHING = 0.1f
        private const val WARMUP = 10
    }
}
