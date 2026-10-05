package com.zinmedia.camera.face

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zinmedia.camera.CameraConfig
import com.zinmedia.camera.CameraSession
import com.zinmedia.camera.FaceEffect
import com.zinmedia.effects.face.FaceDetector
import com.zinmedia.effects.face.ensureFaceModel
import com.zinmedia.effects.gl.FaceEffectLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memasang/melepas satu efek wajah pada [session]: unduh model deteksi sekali, muat efek, lalu
 * aktifkan deteksi di pemroses frame kamera (frame ditahan sampai titik wajahnya siap). Dipakai
 * kamera biasa dan kamera live. Panggilan baru sebaiknya membatalkan coroutine panggilan sebelumnya.
 */
internal class FaceEffectRunner(private val context: Context, private val session: CameraSession) {

    /** Progres unduh model (0..1) selama disiapkan, `null` bila tidak sedang mengunduh. */
    var modelProgress by mutableStateOf<Float?>(null)
        private set

    private var detector: FaceDetector? = null

    /** Siapkan pendeteksi (model diunduh sekali lalu disimpan) tanpa memasang efek. */
    suspend fun prepare(): FaceDetector {
        detector?.let { return it }
        try {
            modelProgress = 0f
            val model = ensureFaceModel(context, CameraConfig.faceModelUrl) { modelProgress = it }
            return withContext(Dispatchers.Default) {
                FaceDetector(context, model, CameraConfig.maxFaces.coerceIn(1, 3), session.faceEngine::onFaces)
            }.also { detector = it }
        } finally {
            modelProgress = null
        }
    }

    /** Pasang [effect] (`null` = lepas). Gagal (model/efek tidak bisa dimuat) = lempar exception. */
    suspend fun apply(effect: FaceEffect?) {
        if (effect == null) {
            clear()
            return
        }
        val active = prepare()
        val ready = FaceEffectLoader.load(context, effect)
        session.faceEngine.effect = ready
        active.enabled = true
        session.faceEngine.detector = active
    }

    fun clear() {
        detector?.enabled = false
        session.faceEngine.detector = null
        session.faceEngine.effect = null
    }

    fun close() {
        clear()
        detector?.close()
        detector = null
    }
}
