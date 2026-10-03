package com.zinmedia.photoeditor.engine

import android.graphics.Bitmap
import android.graphics.Rect
import android.opengl.GLSurfaceView
import java.nio.IntBuffer
import javax.microedition.khronos.opengles.GL10

/**
 *
 *
 * Bitmap utility class to perform different transformation on bitmap
 *
 *
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @version 0.1.2
 * @since 5/21/2018
 */
internal object BitmapUtil {
    /** Batas piksel yang tidak transparan pada [source]; `null` bila seluruhnya transparan. */
    fun opaqueBounds(source: Bitmap): Rect? {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (pixels[row + x] ushr 24 != 0) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        return if (right < 0) null else Rect(left, top, right + 1, bottom + 1)
    }

    /**
     * Save filter bitmap from [ImageFilterView]
     *
     * @param glSurfaceView surface view on which is image is drawn
     * @param gl            open gl source to read pixels from [GLSurfaceView]
     * @return save bitmap
     * @throws OutOfMemoryError error when system is out of memory to load and save bitmap
     */
    @Throws(OutOfMemoryError::class)
    fun createBitmapFromGLSurface(glSurfaceView: GLSurfaceView, gl: GL10): Bitmap {
        val x = 0
        val y = 0
        val w = glSurfaceView.width
        val h = glSurfaceView.height
        val bitmapBuffer = IntArray(w * h)
        val bitmapSource = IntArray(w * h)
        val intBuffer = IntBuffer.wrap(bitmapBuffer)
        intBuffer.position(0)

        gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer)
        var offset1: Int
        var offset2: Int
        for (i in 0 until h) {
            offset1 = i * w
            offset2 = (h - i - 1) * w
            for (j in 0 until w) {
                val texturePixel = bitmapBuffer[offset1 + j]
                val blue = texturePixel shr 16 and 0xff
                val red = texturePixel shl 16 and 0x00ff0000
                val pixel = texturePixel and -0xff0100 or red or blue
                bitmapSource[offset2 + j] = pixel
            }
        }

        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
    }

}