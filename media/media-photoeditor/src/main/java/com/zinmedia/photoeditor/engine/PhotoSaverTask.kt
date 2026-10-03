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
     * Gambar hasil: foto beserta semua lapisan. Bila ada lapisan yang keluar dari foto, kanvas
     * diperluas sampai memuatnya (area di luar foto transparan, diisi hitam saat disimpan JPEG),
     * dibatasi area editor.
     */
    private fun buildBitmap(): Bitmap {
        val bounds = contentBounds()
        val bitmap = createBitmap(bounds.width().coerceAtLeast(1), bounds.height().coerceAtLeast(1))
        Canvas(bitmap).apply {
            translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            photoEditorView.draw(this)
        }
        return bitmap
    }

    /** Batas foto yang tampil ∪ batas tiap lapisan, dalam koordinat [photoEditorView]. */
    private fun contentBounds(): Rect {
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
        if (!content.intersect(0f, 0f, root.width.toFloat(), root.height.toFloat())) {
            content.set(0f, 0f, root.width.toFloat(), root.height.toFloat())
        }
        return Rect().also { content.roundOut(it) }
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

}