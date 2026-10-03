package com.zinmedia.photoeditor

import androidx.core.graphics.createBitmap
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.zinmedia.photoeditor.data.manager.FontManager
import com.zinmedia.photoeditor.engine.OnPhotoEditorListener
import com.zinmedia.photoeditor.engine.PhotoEditor
import com.zinmedia.photoeditor.engine.PhotoEditorView
import com.zinmedia.photoeditor.engine.PhotoFilter
import com.zinmedia.photoeditor.engine.SaveSettings
import com.zinmedia.photoeditor.engine.ViewType
import com.zinmedia.photoeditor.engine.shape.ShapeBuilder
import com.zinmedia.photoeditor.imageeditor.crop.CropState
import com.zinmedia.photoeditor.imageeditor.filters.FilterListener
import com.zinmedia.photoeditor.imageeditor.tools.ToolType
import com.zinmedia.photoeditor.textlayer.TextLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * State satu foto yang sedang diedit: engine editor, hasil crop/filter, dan mode alat yang aktif.
 * Satu instance per foto; dipakai oleh [ImageEditorActivity] maupun editor yang memuat beberapa media.
 *
 * Menyimpan View, jadi buat dengan context Activity dan jangan disimpan melewati umur Activity tersebut.
 */
public class PhotoEditorState(
    context: Context,
    public val sourceUri: Uri,
    pinchTextScalable: Boolean = true,
) {

    private val appContext = context.applicationContext
    internal val fontManager = FontManager(context)

    internal val editorView: PhotoEditorView = PhotoEditorView(context).apply {
        id = View.generateViewId()
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }
    internal val editor: PhotoEditor = PhotoEditor.Builder(context, editorView)
        .setPinchTextScalable(pinchTextScalable)
        .build()
        .also {
            it.setFilterEffect(PhotoFilter.NONE)
        }
    internal var shapeBuilder = ShapeBuilder()

    internal var currentTool by mutableStateOf(EditorTool.Edit)
    internal var isUndoEnabled by mutableStateOf(false)
    internal var isFilterVisible by mutableStateOf(false)
    internal var isLoading by mutableStateOf(false)
    internal var snackbarMessage by mutableStateOf<String?>(null)
    internal var showDrawShape by mutableStateOf(false)
    internal var showTextEditor by mutableStateOf(false)
    /** Lapisan teks yang sedang dibuka di mode teks (baru atau yang diedit). */
    internal var textEditing by mutableStateOf(TextLayer(""))
    internal var editingTextView: View? = null
    internal var showStickerSheet by mutableStateOf(false)
    internal var selectedFilter by mutableStateOf(PhotoFilter.NONE)
    internal var originalBitmap by mutableStateOf<Bitmap?>(null)
    internal var cropState by mutableStateOf(CropState())
    internal var showCrop by mutableStateOf(false)
    /** Lapisan sedang diseret: chrome disembunyikan dan tempat sampah tampil. */
    internal var draggingLayer by mutableStateOf(false)
    internal var overTrash by mutableStateOf(false)
    /** Batas tempat sampah dalam koordinat window. */
    internal var trashBounds: Rect = Rect.Zero

    /** Foto sudah dimuat (atau gagal dimuat bila [loadFailed]). */
    public var isLoaded: Boolean by mutableStateOf(false)
        private set
    public var loadFailed: Boolean by mutableStateOf(false)
        private set

    /** Mode teks, gambar, atau crop sedang terbuka (layar penuh), atau lapisan sedang diseret. */
    public val isToolActive: Boolean get() = showDrawShape || showTextEditor || showCrop || draggingLayer

    /** Muat foto dari [sourceUri] (sekali saja). */
    public suspend fun load(): Boolean {
        if (isLoaded) return !loadFailed
        val bitmap = withContext(Dispatchers.IO) {
            try {
                SampledBitmapLoader.load(appContext, sourceUri)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memuat gambar: $sourceUri", e)
                null
            }
        }
        if (bitmap == null) {
            loadFailed = true
        } else {
            originalBitmap = bitmap
            editorView.source.setImageBitmap(bitmap)
        }
        isLoaded = true
        return bitmap != null
    }

    /** Ada teks/stiker/coretan, crop, atau filter. */
    public fun hasEdits(): Boolean =
        !editor.isCacheEmpty || !cropState.isIdentity || selectedFilter != PhotoFilter.NONE

    /**
     * Tangani tombol back untuk mode yang sedang terbuka.
     * @return `true` bila sudah ditangani (mis. keluar dari mode gambar atau menutup filter).
     */
    public fun handleBack(): Boolean = when {
        currentTool == EditorTool.Draw || currentTool == EditorTool.Eraser -> {
            finishDrawing()
            true
        }
        isFilterVisible -> {
            isFilterVisible = false
            currentTool = EditorTool.Edit
            true
        }
        else -> false
    }

    /** Simpan hasil edit sebagai PNG di folder milik library; mengembalikan URI FileProvider. */
    public suspend fun exportToUri(): Uri {
        // Teks/stiker tidak dihapus dari kanvas, agar hasil edit tetap utuh bila pengiriman diulang.
        val bitmap = editor.saveAsBitmap(
            SaveSettings.Builder()
                .setClearViewsEnabled(false)
                .build()
        )
        return withContext(Dispatchers.IO) {
            val dir = PhotoEditorFileProvider.outputDir(appContext)
            val file = File.createTempFile("edited_", ".jpg", dir)
            // JPEG jauh lebih kecil dari PNG untuk foto; area transparan (mis. stiker di luar foto) jadi hitam.
            val opaque = createBitmap(bitmap.width, bitmap.height).also {
                Canvas(it).apply {
                    drawColor(android.graphics.Color.BLACK)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
            }
            file.outputStream().use { opaque.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            opaque.recycle()
            PhotoEditorFileProvider.uriFor(appContext, file)
        }
    }

    internal fun finishDrawing() {
        showDrawShape = false
        editor.setBrushDrawingMode(false)
        currentTool = EditorTool.Edit
    }

    internal fun onToolSelected(toolType: ToolType) {
        when (toolType) {
            ToolType.SHAPE -> {
                isFilterVisible = false
                showTextEditor = false
                showStickerSheet = false
                editor.setBrushDrawingMode(true)
                shapeBuilder = ShapeBuilder()
                editor.setShape(shapeBuilder)
                currentTool = EditorTool.Draw
                showDrawShape = true
            }

            ToolType.TEXT -> {
                textEditing = TextLayer("")
                showTextEditor = true
                editingTextView = null
                currentTool = EditorTool.Text
            }

            ToolType.ERASER -> Unit

            ToolType.FILTER -> {
                currentTool = EditorTool.Filter
                isFilterVisible = !isFilterVisible
            }

            ToolType.EMOJI, ToolType.STICKER -> {
                currentTool = EditorTool.Sticker
                showStickerSheet = true
            }
        }
    }

    internal fun applyCrop(state: CropState, cropped: Bitmap) {
        cropState = state
        editorView.source.setImageBitmap(cropped)
        // Engine mereset filter saat gambar diganti; pasang lagi filter aktif.
        editor.setFilterEffect(selectedFilter)
    }

    internal val filterListener: FilterListener = FilterListener { photoFilter ->
        selectedFilter = photoFilter
        editor.setFilterEffect(photoFilter)
    }

    private val engineListener = object : OnPhotoEditorListener {
        override fun onTextLayerClick(rootView: View, layer: Any?) {
            if (currentTool != EditorTool.Draw && layer is TextLayer) {
                textEditing = layer
                editingTextView = rootView
                // Sembunyikan teks asli selama diedit.
                rootView.visibility = View.INVISIBLE
                showTextEditor = true
                currentTool = EditorTool.Text
            }
        }

        override fun onLayerDrag(view: View, rawX: Float, rawY: Float) {
            draggingLayer = true
            val over = trashBounds.contains(toWindow(rawX, rawY))
            if (over != overTrash) {
                overTrash = over
                // Lapisan mengecil & memudar saat siap dibuang.
                view.animate().alpha(if (over) 0.5f else 1f).setDuration(120).start()
            }
        }

        override fun onLayerDragEnd(view: View, cancelled: Boolean) {
            if (overTrash && !cancelled) {
                editor.removeLayer(view)
            } else {
                view.animate().cancel()
                view.alpha = 1f
            }
            overTrash = false
            draggingLayer = false
        }

        override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {
            isUndoEnabled = editor.isUndoAvailable
        }

        override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) {
            isUndoEnabled = editor.isUndoAvailable
        }

        override fun onStartViewChangeListener(viewType: ViewType) {}
        override fun onStopViewChangeListener(viewType: ViewType) {}
        override fun onTouchSourceImage(event: MotionEvent) {}
    }

    /** Koordinat layar (MotionEvent.raw*) ke koordinat window, sama dengan batas tempat sampah. */
    private fun toWindow(rawX: Float, rawY: Float): Offset {
        val onScreen = IntArray(2).also { editorView.getLocationOnScreen(it) }
        val inWindow = IntArray(2).also { editorView.getLocationInWindow(it) }
        return Offset(rawX - (onScreen[0] - inWindow[0]), rawY - (onScreen[1] - inWindow[1]))
    }

    init {
        editor.setOnPhotoEditorListener(engineListener)
    }

    private companion object {
        const val TAG = "PhotoEditorState"
        const val JPEG_QUALITY = 90
    }
}

/** Alat yang sedang aktif di editor foto. */
internal enum class EditorTool { Edit, Draw, Eraser, Text, Filter, Sticker, Emoji }
