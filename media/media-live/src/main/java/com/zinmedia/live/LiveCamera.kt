package com.zinmedia.live

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner
import com.zinmedia.camera.FaceEffect
import com.zinmedia.camera.LiveCameraHost
import com.zinmedia.effects.BeautyParams
import kotlinx.coroutines.flow.StateFlow

/**
 * Kamera siaran langsung tanpa UI: aplikasi menampilkan [Preview] di layout-nya sendiri dan
 * memanggil fungsi di bawah dari tombol/peristiwa miliknya (mis. gift memasang efek wajah).
 * Video (dengan filter, penghalus, efek wajah) + mikrofon dikirim ke server RTMP.
 *
 * Buat satu instance per layar live (mis. di ViewModel) dan panggil [release] saat selesai.
 * Izin `CAMERA` & `RECORD_AUDIO` diminta oleh aplikasi sebelum [Preview] ditampilkan.
 */
public class LiveCamera(context: Context, lifecycleOwner: LifecycleOwner) {

    private val camera = LiveCameraHost(context, lifecycleOwner)
    private val broadcaster = LiveBroadcaster(context, camera)

    /** Status siaran (tahap, galat, mikrofon, sejak kapan mengudara). */
    public val state: StateFlow<LiveState> get() = broadcaster.state

    /** Isi kamera saja. [gestures] = ketuk untuk fokus & cubit untuk zoom. */
    @Composable
    public fun Preview(modifier: Modifier = Modifier, gestures: Boolean = true) {
        camera.Preview(modifier, gestures)
    }

    // ---- siaran ----

    /** Mulai mengudara ke [publishUrl] (mis. `rtmp://server/live/<kunci>`). Hasil lewat [state]. */
    public fun start(publishUrl: String) {
        broadcaster.start(publishUrl)
    }

    public fun stop() {
        broadcaster.stop()
    }

    public fun setMicMuted(muted: Boolean) {
        broadcaster.setMicMuted(muted)
    }

    /** Kembali ke [LivePhase.Idle] setelah [LivePhase.Error]. */
    public fun clearError() {
        broadcaster.clearError()
    }

    // ---- kamera ----

    public val isFrontCamera: Boolean get() = camera.isFrontCamera
    public val hasFlash: Boolean get() = camera.hasFlash
    public val zoomRatio: Float get() = camera.zoomRatio
    public val minZoom: Float get() = camera.minZoom
    public val maxZoom: Float get() = camera.maxZoom

    public fun flipCamera() {
        camera.flipCamera()
    }

    /** Senter (kamera belakang). */
    public fun setTorch(on: Boolean) {
        camera.setTorch(on)
    }

    public fun setZoom(ratio: Float) {
        camera.setZoom(ratio)
    }

    // ---- filter & efek ----

    /** Nama filter yang tersedia (indeks 0 = tanpa filter; filter LUT dari konfigurasi ikut). */
    public val filterNames: List<String> get() = camera.filterNames

    /** Filter berdasarkan indeks [filterNames]. */
    public suspend fun setFilter(index: Int) {
        camera.setFilter(index)
    }

    /** Penghalus kulit 0 (mati)..1. */
    public suspend fun setSmoothing(strength: Float) {
        camera.setSmoothing(strength)
    }

    /** Beauty face-aware yang dapat diubah real-time dan digabungkan dengan filter/efek wajah. */
    public suspend fun setBeauty(params: BeautyParams) {
        camera.setBeauty(params)
    }

    /** Progres unduh model wajah (0..1), `null` bila tidak sedang mengunduh. */
    public val faceModelProgress: Float? get() = camera.faceModelProgress

    /** Unduh model wajah lebih awal (mis. saat layar live dibuka), agar efek pertama langsung tampil. */
    public suspend fun prepareFaceEffects() {
        camera.prepareFaceEffects()
    }

    /**
     * Pasang satu efek wajah, menggantikan efek sebelumnya; `null` = lepas. Ikut tersiar ke
     * penonton. Selesai saat efek tampil; gagal (model/gambar tidak bisa dimuat) = exception.
     */
    public suspend fun setFaceEffect(effect: FaceEffect?) {
        camera.setFaceEffect(effect)
    }

    public fun release() {
        broadcaster.release()
        camera.release()
    }
}
