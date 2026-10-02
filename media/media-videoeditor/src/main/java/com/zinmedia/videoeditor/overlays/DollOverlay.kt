package com.zinmedia.videoeditor.overlays

import android.graphics.*
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.sin

@UnstableApi
class DollOverlay(
    bitmap: Bitmap
) : CanvasOverlay(false) {

    private val dollBitmap = bitmap
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ukuran boneka
    private val widthPx = bitmap.width
    private val heightPx = bitmap.height

    init {
        setCanvasSize(widthPx, heightPx)
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val timeSec = presentationTimeUs / 1_000_000f

        // Animasi naik turun
        val floatOffset = (sin(timeSec * 2f) * 15f)

        // Animasi goyang kecil
        val rotate = (sin(timeSec * 3f) * 5f)

        canvas.save()

        // posisi boneka di dalam overlay canvas
        val centerX = widthPx / 2f
        val centerY = heightPx / 2f + floatOffset

        canvas.rotate(rotate, centerX, centerY)

        canvas.drawBitmap(dollBitmap, 0f, floatOffset, paint)

        canvas.restore()
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor( -1f, 1f )      // pojok kiri bawah
            .setBackgroundFrameAnchor( -0.1f, 0.1f ) // jarak dari tepi
            .build()
    }
}

suspend fun loadBitmapFromUrl(url: String): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            connection.connect()
            val input = connection.getInputStream()
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

