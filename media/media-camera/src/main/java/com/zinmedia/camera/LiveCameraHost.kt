package com.zinmedia.camera

import android.content.Context
import android.view.Surface
import androidx.annotation.RestrictTo
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleOwner
import com.zinmedia.camera.face.FaceEffectRunner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Kamera untuk siaran langsung (dipakai modul `media-live`): hanya isi kamera, tanpa tombol.
 * Filter, penghalus, dan efek wajah diatur lewat fungsi; frame yang sama (tegak, tidak di-mirror)
 * dikirim ke encoder lewat [addStreamOutput]. Panggil [release] saat selesai.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LiveCameraHost(context: Context, lifecycleOwner: LifecycleOwner) {

    private val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    private val session = CameraSession(
        context = context,
        lifecycleOwner = lifecycleOwner,
        previewView = previewView,
        withVideoCapture = false,
        // Efek bisa dipasang kapan saja (mis. dari gift): analisis frame selalu siap.
        wantsAnalysis = true,
    )
    private val faceRunner = FaceEffectRunner(context, session)
    private val filters = cameraFilters()
    private var filterIndex = 0
    private var smoothing = 0f
    private var started = false

    /** Nama filter yang tersedia (indeks 0 = tanpa filter), untuk [setFilter]. */
    public val filterNames: List<String> = filters.map { it.name }

    public val isFrontCamera: Boolean get() = session.isFront
    public val hasFlash: Boolean get() = session.hasFlashUnit
    public val zoomRatio: Float get() = session.zoomRatio
    public val minZoom: Float get() = session.minZoom
    public val maxZoom: Float get() = session.maxZoom

    /** Progres unduh model wajah (0..1), `null` bila tidak sedang mengunduh. */
    public val faceModelProgress: Float? get() = faceRunner.modelProgress

    /**
     * Isi kamera (9:16 disarankan). [gestures] = ketuk untuk fokus & cubit untuk zoom.
     * Kamera mulai menyala saat pertama kali ditampilkan.
     */
    @Composable
    public fun Preview(modifier: Modifier = Modifier, gestures: Boolean = true) {
        LaunchedEffect(Unit) {
            if (started) return@LaunchedEffect
            started = true
            // ViewPort (bingkai) baru tersedia setelah preview diukur.
            suspendCancellableCoroutine { cont -> previewView.doOnLayout { cont.resume(Unit) } }
            session.start()
        }
        Box(
            modifier
                .clipToBounds()
                .then(if (gestures) Modifier.cameraGestures() else Modifier)
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier)
        }
    }

    private fun Modifier.cameraGestures(): Modifier = this
        .pointerInput(Unit) { detectTapGestures(onTap = { session.focusAt(it.x, it.y) }) }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val (a, b) = pressed
                        val now = (a.position - b.position).getDistance()
                        val before = (a.previousPosition - b.previousPosition).getDistance()
                        if (before > 0f) session.setZoom(session.zoomRatio * now / before)
                    }
                } while (event.changes.any { it.pressed })
            }
        }

    public fun flipCamera() {
        session.flipCamera()
    }

    /** Senter (kamera belakang). */
    public fun setTorch(on: Boolean) {
        session.setTorch(on)
    }

    public fun setZoom(ratio: Float) {
        session.setZoom(ratio)
    }

    /** Filter berdasarkan indeks [filterNames] (0 = tanpa filter). */
    public suspend fun setFilter(index: Int) {
        filterIndex = index.coerceIn(0, filters.lastIndex)
        session.applyFilter(filters[filterIndex], smoothing)
    }

    /** Penghalus kulit 0 (mati)..1. */
    public suspend fun setSmoothing(strength: Float) {
        smoothing = strength.coerceIn(0f, 1f)
        session.applyFilter(filters[filterIndex], smoothing)
    }

    /** Unduh model wajah lebih awal, agar efek pertama tampil tanpa menunggu. */
    public suspend fun prepareFaceEffects() {
        faceRunner.prepare()
    }

    /**
     * Pasang satu efek wajah (menggantikan efek sebelumnya); `null` = lepas. Selesai saat efek
     * tampil; gagal (model/gambar tidak bisa dimuat) = exception.
     */
    public suspend fun setFaceEffect(effect: FaceEffect?) {
        faceRunner.apply(effect)
    }

    /** Kirim frame kamera (dengan filter & efek) ke [surface] berukuran [width]×[height]. */
    public fun addStreamOutput(surface: Surface, width: Int, height: Int) {
        session.addStreamOutput(surface, width, height)
    }

    public fun removeStreamOutput(surface: Surface) {
        session.removeStreamOutput(surface)
    }

    public fun release() {
        faceRunner.close()
        session.release()
    }
}
