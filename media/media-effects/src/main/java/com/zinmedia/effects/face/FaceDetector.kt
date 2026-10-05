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
import com.zinmedia.effects.FaceEffect
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
 * Pendeteksi wajah MediaPipe untuk efek wajah. Frame tegak (tidak di-mirror) dikirim lewat [submit];
 * deteksi berjalan asinkron (frame baru ditolak selama deteksi sebelumnya berjalan), lalu posisi
 * efek dihaluskan dan dikirim ke [onCorners]: sudut-sudut tiap efek (kiri-atas, kanan-atas,
 * kiri-bawah, kanan-bawah) dalam piksel frame itu, beserta `tag` frame tersebut. Daftar kosong =
 * tidak ada wajah.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FaceDetector(
    context: Context,
    modelFile: File,
    private val maxFaces: Int = 1,
    private val onCorners: (corners: List<FloatArray>, tag: Any?) -> Unit,
) {

    /** Efek aktif dan rasio gambarnya (tinggi/lebar). Ganti efek = penghalus diulang dari awal. */
    @Volatile
    public var effect: Pair<FaceEffect, Float>? = null
        set(value) {
            field = value
            resetFilters = true
            generation++
        }

    /** Bertambah tiap efek diganti; hasil yang dihitung untuk efek sebelumnya dibuang. */
    @Volatile
    private var generation = 0

    @Volatile
    private var resetFilters = false

    private val busy = AtomicBoolean(false)
    private var lastTimestamp = 0L
    private var lastSubmitMs = 0L
    private var frameWidth = 1f
    private var frameHeight = 1f
    private var frameTag: Any? = null
    /** Penghalus per wajah per posisi (efek di kedua sisi = 2 posisi). */
    // Posisi/ukuran dalam pecahan frame; sudut (radian) memakai beta lebih kecil.
    private val filters = List(maxFaces * MAX_SLOTS) {
        List(4) { OneEuroFilter() } + OneEuroFilter(minCutoff = 1.7f, beta = 1.5f)
    }
    /** Slot yang tampil di hasil sebelumnya; slot yang baru muncul dimulai tanpa meluncur. */
    private val visibleSlots = BooleanArray(maxFaces * MAX_SLOTS)
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
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
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
            build(Delegate.CPU)
        }
    }

    /**
     * `true` bila frame berikutnya akan dideteksi: ada efek, deteksi sebelumnya selesai, dan sudah
     * lewat jeda minimum (~30 deteksi/detik, hemat baterai & panas). Cek ini sebelum menyiapkan
     * bitmap agar frame yang akan dilewati tidak perlu dibaca.
     */
    public fun wantsFrame(): Boolean =
        effect != null && !busy.get() && SystemClock.uptimeMillis() - lastSubmitMs >= MIN_INTERVAL_MS

    /**
     * Deteksi [bitmap] (frame tegak). [bitmap] tidak boleh diubah sampai hasilnya keluar (lihat
     * [wantsFrame]). Mengembalikan `false` bila frame dilewati.
     */
    public fun submit(bitmap: Bitmap, tag: Any? = null): Boolean {
        if (effect == null || !busy.compareAndSet(false, true)) return false
        lastSubmitMs = SystemClock.uptimeMillis()
        frameWidth = bitmap.width.toFloat()
        frameHeight = bitmap.height.toFloat()
        frameTag = tag
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
        val startGeneration = generation
        val current = effect
        val width = frameWidth
        val height = frameHeight
        val tag = frameTag
        // Frame berikutnya boleh dikirim setelah nilai frame ini dibaca.
        busy.set(false)
        if (current == null) return
        val now = System.nanoTime()
        val faces = result.faceLandmarks().take(maxFaces)
        if (faces.isEmpty() || resetFilters) {
            resetFilters = false
            filters.forEach { group -> group.forEach { it.reset() } }
        }
        val shownNow = BooleanArray(visibleSlots.size)
        val corners = faces.flatMapIndexed { faceIndex, landmarks ->
            if (landmarks.size < Landmark.COUNT) return@flatMapIndexed emptyList()
            val xs = FloatArray(landmarks.size)
            val ys = FloatArray(landmarks.size)
            val zs = FloatArray(landmarks.size)
            landmarks.forEachIndexed { i, point ->
                xs[i] = point.x() * width
                ys[i] = point.y() * height
                // Kedalaman MediaPipe berskala sama dengan x (lebar frame).
                zs[i] = point.z() * width
            }
            placeFaceEffects(FacePoints(xs, ys, zs), current.first, current.second, width, height)
                .filter { it.slot in 0 until MAX_SLOTS }
                .map { raw ->
                    val index = faceIndex * MAX_SLOTS + raw.slot
                    val f = filters[index]
                    // Muncul lagi (mis. telinga kembali terlihat): langsung di tempatnya.
                    if (!visibleSlots[index]) f.forEach { it.reset() }
                    shownNow[index] = true
                    val smooth = FacePlacement(
                        centerX = f[0].filter(raw.centerX, now),
                        centerY = f[1].filter(raw.centerY, now),
                        width = f[2].filter(raw.width, now),
                        height = f[3].filter(raw.height, now),
                        angle = f[4].filter(raw.angle, now),
                    )
                    placementCorners(smooth, width, height)
                }
        }
        shownNow.copyInto(visibleSlots)
        // Efek diganti selama perhitungan: posisi ini milik efek lama.
        if (startGeneration != generation) return
        onCorners(corners, tag)
    }

    public fun close() {
        effect = null
        landmarker.close()
    }

    private companion object {
        const val TAG = "FaceDetector"
        /** Posisi efek maksimal per wajah (kedua telinga/pipi). */
        const val MAX_SLOTS = 2
        const val MIN_INTERVAL_MS = 33L
    }
}
