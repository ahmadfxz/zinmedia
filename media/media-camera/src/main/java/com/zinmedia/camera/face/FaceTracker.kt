package com.zinmedia.camera.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.zinmedia.camera.FaceEffect
import com.zinmedia.effects.face.FaceDetector
import java.io.File

/** Empat sudut efek (kiri-atas, kanan-atas, kiri-bawah, kanan-bawah) dalam koordinat sensor kamera. */
internal class FaceQuad(val sensorPoints: FloatArray)

/**
 * Pelacak wajah untuk [ImageAnalysis]: frame dipotong sesuai bingkai preview & ditegakkan, lalu
 * dideteksi [FaceDetector]; sudut-sudut efek dipetakan ke koordinat sensor dan dikirim ke [onQuads]
 * (daftar kosong = tidak ada wajah).
 */
internal class FaceTracker(
    context: Context,
    modelFile: File,
    maxFaces: Int = 1,
    private val onQuads: (List<FaceQuad>) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetector(context, modelFile, maxFaces) { corners, tag ->
        // tag = matriks frame tegak -> sensor milik frame yang dideteksi.
        val uprightToSensor = tag as Matrix
        onQuads(corners.map { points -> FaceQuad(points.also(uprightToSensor::mapPoints)) })
    }

    /** Efek aktif dan rasio gambarnya (tinggi/lebar). */
    var effect: Pair<FaceEffect, Float>?
        get() = detector.effect
        set(value) {
            detector.effect = value
        }

    override fun analyze(image: ImageProxy) {
        image.use { proxy ->
            if (!detector.wantsFrame()) return
            val (bitmap, uprightToSensor) = try {
                uprightFrame(proxy)
            } catch (e: Exception) {
                Log.w(TAG, "Frame tidak bisa dibaca", e)
                return
            }
            detector.submit(bitmap, uprightToSensor)
        }
    }

    /**
     * Potong sesuai crop rect dan putar ke posisi tegak (untuk deteksi). Juga mengembalikan matriks
     * frame tegak -> koordinat sensor, agar posisi efek bisa dipetakan ke tiap output kamera.
     */
    private fun uprightFrame(proxy: ImageProxy): Pair<Bitmap, Matrix> {
        val full = proxy.toBitmap()
        val crop = proxy.cropRect
        val rotation = Matrix().apply { postRotate(proxy.imageInfo.rotationDegrees.toFloat()) }
        val upright = Bitmap.createBitmap(full, crop.left, crop.top, crop.width(), crop.height(), rotation, false)
        // buffer -> tegak: geser ke crop, putar, lalu geser agar mulai dari (0, 0).
        val bounds = android.graphics.RectF(0f, 0f, crop.width().toFloat(), crop.height().toFloat())
        rotation.mapRect(bounds)
        val bufferToUpright = Matrix().apply {
            postTranslate(-crop.left.toFloat(), -crop.top.toFloat())
            postConcat(rotation)
            postTranslate(-bounds.left, -bounds.top)
        }
        val uprightToBuffer = Matrix().also { bufferToUpright.invert(it) }
        val bufferToSensor = Matrix().also { proxy.imageInfo.sensorToBufferTransformMatrix.invert(it) }
        return upright to Matrix(uprightToBuffer).apply { postConcat(bufferToSensor) }
    }

    fun close() {
        detector.close()
    }

    private companion object {
        const val TAG = "FaceTracker"
    }
}
