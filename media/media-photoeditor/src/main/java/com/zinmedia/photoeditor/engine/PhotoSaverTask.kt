package com.zinmedia.photoeditor.engine

import androidx.core.graphics.createBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.graphics.Rect
import android.graphics.RectF
import com.zinmedia.photoeditor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Created by Burhanuddin Rashid on 18/05/21.
 *
 * @author <https:></https:>//github.com/burhanrashid52>
 */
internal class PhotoSaverTask(
    private val photoEditorView: PhotoEditorView,
    private val boxHelper: BoxHelper,
    private var saveSettings: SaveSettings
) {

    private val drawingView: DrawingView = photoEditorView.drawingView

    private fun onBeforeSaveImage() {
        boxHelper.clearHelperBox()
        drawingView.destroyDrawingCache()
    }

    fun saveImageAsBitmap(): Bitmap {
        onBeforeSaveImage()
        val bitmap = buildBitmap()
        if (saveSettings.isClearViewsEnabled) {
            boxHelper.clearAllViews(drawingView)
        }
        return bitmap
    }

    suspend fun saveImageAsFile(imagePath: String): SaveFileResult {
        onBeforeSaveImage()
        val capturedBitmap = buildBitmap()

        val result = withContext(Dispatchers.IO) {
            val file = File(imagePath)
            try {
                FileOutputStream(file, false).use { outputStream ->
                    capturedBitmap.compress(
                        saveSettings.compressFormat,
                        saveSettings.compressQuality,
                        outputStream
                    )
                    outputStream.flush()
                }

                SaveFileResult.Success
            } catch (e: IOException) {
                SaveFileResult.Failure(e)
            }
        }

        if (result is SaveFileResult.Success) {
            // Clear all views if it's enabled in save settings
            if (saveSettings.isClearViewsEnabled) {
                boxHelper.clearAllViews(drawingView)
            }
        }

        return result
    }

    /**
     * Gambar hasil: foto beserta semua lapisan, di resolusi asli foto (tidak bergantung ukuran
     * layar), sisi terpanjang maksimal [MAX_OUTPUT_SIDE]. Area di luar foto transparan (diisi hitam
     * saat disimpan JPEG).
     */
    private fun buildBitmap(): Bitmap {
        val bounds = contentBounds()
        val scale = outputScale(bounds)
        val width = (bounds.width() * scale).roundToInt().coerceAtLeast(1)
        val height = (bounds.height() * scale).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        Canvas(bitmap).apply {
            scale(scale, scale)
            translate(-bounds.left, -bounds.top)
            photoEditorView.draw(this)
        }
        return bitmap
    }

    /**
     * Skala dari piksel layar ke piksel hasil: sebesar resolusi asli foto (piksel foto per piksel
     * tampilan), minimal 1, dan dibatasi agar sisi terpanjang hasil tidak melebihi [MAX_OUTPUT_SIDE].
     */
    private fun outputScale(bounds: RectF): Float {
        val image = photoEditorView.source
        val drawable = image.drawable ?: return 1f
        val shown = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        image.imageMatrix.mapRect(shown)
        val native = if (shown.width() > 0f) drawable.intrinsicWidth / shown.width() else 1f
        val longSide = maxOf(bounds.width(), bounds.height()).coerceAtLeast(1f)
        return minOf(native.coerceAtLeast(1f), MAX_OUTPUT_SIDE.toFloat() / longSide)
    }

    /**
     * Batas gambar hasil, dalam koordinat [photoEditorView]: area foto bila semua lapisan ada di
     * dalamnya; bila ada lapisan yang keluar dari foto, seluruh area editor (batas maksimal).
     */
    private fun contentBounds(): RectF {
        val root = photoEditorView
        val image = root.source
        val content = RectF(0f, 0f, image.width.toFloat(), image.height.toFloat())
        image.drawable?.let { drawable ->
            // Area gambar sebenarnya di dalam ImageView (tanpa sisa kosong di sampingnya).
            content.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
            image.imageMatrix.mapRect(content)
            content.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
        }
        mapToAncestor(image, root, content)
        val photo = RectF(content)

        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child !== drawingView && child.tag !is ViewType) continue
            val layer = if (child === drawingView) {
                // Coretan: hanya area yang benar-benar tergambar.
                drawnBounds(child) ?: continue
            } else {
                // Teks/stiker/emoji: isi lapisan saja (tanpa margin bingkai), termasuk geser/skala/putar.
                val inner = child.findViewById<View>(R.id.imgPhotoEditorImage)
                    ?: child.findViewById(R.id.tvPhotoEditorText)
                    ?: child
                RectF(0f, 0f, inner.width.toFloat(), inner.height.toFloat()).also { mapToAncestor(inner, root, it) }
            }
            content.union(layer)
        }
        // Ada lapisan keluar dari foto (toleransi 1 px): pakai seluruh area editor, sisanya hitam.
        val overflows = content.left < photo.left - 1f || content.top < photo.top - 1f ||
            content.right > photo.right + 1f || content.bottom > photo.bottom + 1f
        val result = if (overflows) RectF(0f, 0f, root.width.toFloat(), root.height.toFloat()) else photo
        // Tetap pecahan: dibulatkan setelah dikalikan skala, agar ukuran hasil = ukuran asli foto.
        return result
    }

    /** Petakan [rect] (koordinat [view]) ke koordinat [ancestor], termasuk transformasi view. */
    private fun mapToAncestor(view: View, ancestor: View, rect: RectF) {
        var current = view
        while (current !== ancestor) {
            current.matrix.mapRect(rect)
            rect.offset((current.left - current.scrollX).toFloat(), (current.top - current.scrollY).toFloat())
            current = current.parent as? View ?: return
        }
    }

    /** Batas piksel tak transparan dari [view] dalam koordinat [photoEditorView]; `null` bila kosong. */
    private fun drawnBounds(view: View): RectF? {
        if (view.width <= 0 || view.height <= 0) return null
        val bitmap = createBitmap(view.width, view.height)
        view.draw(Canvas(bitmap))
        val opaque = BitmapUtil.opaqueBounds(bitmap)
        bitmap.recycle()
        return opaque?.let { RectF(it).also { rect -> mapToAncestor(view, photoEditorView, rect) } }
    }


    private companion object {
        /** Sisi terpanjang maksimal foto hasil (sama dengan batas saat foto dimuat). */
        const val MAX_OUTPUT_SIDE = 2048
    }
}
