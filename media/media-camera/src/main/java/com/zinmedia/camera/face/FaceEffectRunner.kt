package com.zinmedia.camera.face

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.request.allowHardware
import coil3.toBitmap
import com.zinmedia.camera.CameraConfig
import com.zinmedia.camera.CameraSession
import com.zinmedia.camera.FaceEffect
import com.zinmedia.effects.face.ensureFaceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memasang/melepas satu efek wajah pada [session]: unduh model sekali, muat gambar efek, lalu
 * aktifkan pelacak. Dipakai kamera biasa dan kamera live. Panggilan baru sebaiknya membatalkan
 * coroutine panggilan sebelumnya.
 */
internal class FaceEffectRunner(private val context: Context, private val session: CameraSession) {

    /** Progres unduh model (0..1) selama disiapkan, `null` bila tidak sedang mengunduh. */
    var modelProgress by mutableStateOf<Float?>(null)
        private set

    private var tracker: FaceTracker? = null

    /** Siapkan pelacak (model diunduh sekali lalu disimpan) tanpa memasang efek. */
    suspend fun prepare(): FaceTracker {
        tracker?.let { return it }
        try {
            modelProgress = 0f
            val model = ensureFaceModel(context, CameraConfig.faceModelUrl) { modelProgress = it }
            return withContext(Dispatchers.Default) {
                FaceTracker(
                    context = context,
                    modelFile = model,
                    maxFaces = CameraConfig.maxFaces.coerceIn(1, 3),
                    onQuads = session::setFaceQuads,
                )
            }.also { tracker = it }
        } finally {
            modelProgress = null
        }
    }

    /** Pasang [effect] (`null` = lepas). Gagal (model/gambar tidak bisa dimuat) = lempar exception. */
    suspend fun apply(effect: FaceEffect?) {
        if (effect == null) {
            clear()
            return
        }
        val active = prepare()
        val image = loadImage(effect.imageUrl) ?: error("Gambar efek tidak bisa dimuat: ${effect.imageUrl}")
        active.effect = effect to image.height.toFloat() / image.width.coerceAtLeast(1)
        session.setFaceOverlayImage(image)
        session.setFaceAnalyzer(active)
    }

    fun clear() {
        tracker?.effect = null
        session.setFaceOverlayImage(null)
        session.setFaceAnalyzer(null)
    }

    private suspend fun loadImage(url: String): Bitmap? {
        val request = coil3.request.ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val result = coil3.SingletonImageLoader.get(context).execute(request)
        return (result as? coil3.request.SuccessResult)?.image?.toBitmap()
    }

    fun close() {
        tracker?.close()
        tracker = null
    }
}
