package com.zinmedia.photoeditor.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.TextUtils
import android.view.GestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IntRange
import com.zinmedia.photoeditor.R
import com.zinmedia.photoeditor.engine.PhotoEditorImageViewListener.OnSingleTapUpCallback
import com.zinmedia.photoeditor.engine.shape.ShapeBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *
 *
 * This class in initialize by [PhotoEditor.Builder] using a builder pattern with multiple
 * editing attributes
 *
 *
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @version 0.1.1
 * @since 18/01/2017
 */
internal class PhotoEditorImpl @SuppressLint("ClickableViewAccessibility") constructor(
    builder: PhotoEditor.Builder
) : PhotoEditor {
    private val photoEditorView: PhotoEditorView = builder.photoEditorView
    private val viewState: PhotoEditorViewState = PhotoEditorViewState()
    private val drawerViewState: DrawerEditorViewState = DrawerEditorViewState()
    private val imageView: ImageView = builder.imageView
    private val deleteView: View? = builder.deleteView
    private val drawingView: DrawingView = builder.drawingView
    private val mBrushDrawingStateListener: BrushDrawingStateListener =
        BrushDrawingStateListener(builder.photoEditorView, drawerViewState)
    private val mBoxHelper: BoxHelper = BoxHelper(builder.photoEditorView, viewState)
    private var mOnPhotoEditorListener: OnPhotoEditorListener? = null
    private val isTextPinchScalable: Boolean = builder.isTextPinchScalable
    private val mDefaultTextTypeface: Typeface? = builder.textTypeface
    private val mDefaultEmojiTypeface: Typeface? = builder.emojiTypeface
    private val mGraphicManager: GraphicManager = GraphicManager(builder.photoEditorView, viewState, drawerViewState)
    private val context: Context = builder.context

    override fun addImage(desiredImage: Bitmap) {
        val multiTouchListener = getMultiTouchListener(true)
        val sticker = Sticker(photoEditorView, multiTouchListener, viewState, mGraphicManager)
        sticker.buildView(desiredImage)
        addToEditor(sticker)
    }

    override fun addTextLayer(image: Bitmap, layer: Any) {
        drawingView.enableDrawing(false)
        val graphic = TextLayerGraphic(photoEditorView, getMultiTouchListener(isTextPinchScalable), viewState, mGraphicManager)
        graphic.buildView(image, layer)
        addToEditor(graphic)
    }

    override fun editTextLayer(view: View, image: Bitmap, layer: Any) {
        if (!viewState.containsAddedView(view)) return
        view.findViewById<ImageView>(R.id.imgPhotoEditorImage)?.setImageBitmap(image)
        view.setTag(R.id.zm_tag_text_layer, layer)
        mGraphicManager.updateView(view)
    }

    override fun removeLayer(view: View) {
        mGraphicManager.removeView(view)
    }

    override fun addEmoji(emojiName: String) {
        addEmoji(null, emojiName)
    }

    override fun addEmoji(emojiTypeface: Typeface?, emojiName: String) {
        drawingView.enableDrawing(false)
        val multiTouchListener = getMultiTouchListener(true)
        val emoji = Emoji(
            photoEditorView,
            multiTouchListener,
            viewState,
            mGraphicManager,
            mDefaultEmojiTypeface
        )
        emoji.buildView(emojiTypeface, emojiName)
        addToEditor(emoji)
    }

    private fun addToEditor(graphic: Graphic) {
        clearHelperBox()
        mGraphicManager.addView(graphic)
        // Change the in-focus view
        viewState.currentSelectedView = graphic.rootView
    }

    /**
     * Create a new instance and scalable touchview
     *
     * @param isPinchScalable true if make pinch-scalable, false otherwise.
     * @return scalable multitouch listener
     */
    private fun getMultiTouchListener(isPinchScalable: Boolean): MultiTouchListener {
        return MultiTouchListener(
            deleteView,
            photoEditorView,
            imageView,
            isPinchScalable,
            mOnPhotoEditorListener,
            viewState
        )
    }

    override fun setBrushDrawingMode(brushDrawingMode: Boolean) {
        drawingView.enableDrawing(brushDrawingMode)
    }

    override val brushDrawableMode: Boolean
        get() = drawingView != null && drawingView.isDrawingEnabled

    override fun setOpacity(@IntRange(from = 0, to = 100) opacity: Int) {
        var opacityValue = opacity
        opacityValue = (opacityValue / 100.0 * 255.0).toInt()
        drawingView.currentShapeBuilder.withShapeOpacity(opacityValue)
    }

    override var brushSize: Float
        get() = drawingView.currentShapeBuilder.shapeSize
        set(size) {
            drawingView.currentShapeBuilder.withShapeSize(size)
        }
    override var brushColor: Int
        get() = drawingView.currentShapeBuilder.shapeColor
        set(color) {
            drawingView.currentShapeBuilder.withShapeColor(color)
        }

    override fun setBrushEraserSize(brushEraserSize: Float) {
        drawingView.eraserSize = brushEraserSize
    }

    override val eraserSize: Float
        get() = drawingView.eraserSize

    override fun brushEraser() {
        drawingView.brushEraser()
    }

    override fun undo(): Boolean {
        return mGraphicManager.undoView()
    }

    override val isUndoAvailable get() = drawerViewState.addedViewsCount > 0

    override fun redo(): Boolean {
        return mGraphicManager.redoView()
    }

    override val isRedoAvailable get() = mGraphicManager.redoStackCount > 0

    override fun clearAllViews() {
        mBoxHelper.clearAllViews(drawingView)
    }

    override fun clearHelperBox() {
        mBoxHelper.clearHelperBox()
    }

    override fun setFilterEffect(customEffect: CustomEffect?) {
        photoEditorView.setFilterEffect(customEffect)
    }

    override fun setFilterEffect(filterType: PhotoFilter) {
        photoEditorView.setFilterEffect(filterType)
    }

    override suspend fun saveAsBitmap(
        saveSettings: SaveSettings
    ): Bitmap = withContext(Dispatchers.Main) {
        photoEditorView.saveFilter()
        val photoSaverTask = PhotoSaverTask(photoEditorView, mBoxHelper, saveSettings)
        return@withContext photoSaverTask.saveImageAsBitmap()
    }

    override fun saveAsBitmap(saveSettings: SaveSettings, onSaveBitmap: OnSaveBitmap) {
        GlobalScope.launch(Dispatchers.Main) {
            val bitmap = saveAsBitmap(saveSettings)
            onSaveBitmap.onBitmapReady(bitmap)
        }
    }

    override fun saveAsBitmap(onSaveBitmap: OnSaveBitmap) {
        saveAsBitmap(SaveSettings.Builder().build(), onSaveBitmap)
    }

    override fun setOnPhotoEditorListener(onPhotoEditorListener: OnPhotoEditorListener) {
        mOnPhotoEditorListener = onPhotoEditorListener
        mGraphicManager.onPhotoEditorListener = mOnPhotoEditorListener
        mBrushDrawingStateListener.setOnPhotoEditorListener(mOnPhotoEditorListener)
    }

    override val isCacheEmpty: Boolean
        // Teks/stiker/emoji (viewState) maupun coretan (drawerViewState).
        get() = viewState.addedViewsCount == 0 && drawerViewState.addedViewsCount == 0

    // region Shape
    override fun setShape(shapeBuilder: ShapeBuilder) {
        drawingView.currentShapeBuilder = shapeBuilder
    } // endregion

    init {
        drawingView.setBrushViewChangeListener(mBrushDrawingStateListener)
        val mDetector = GestureDetector(
            context,
            PhotoEditorImageViewListener(
                viewState,
                object : OnSingleTapUpCallback {
                    override fun onSingleTapUp() {
                        clearHelperBox()
                    }
                }
            )
        )
        imageView.setOnTouchListener { _, event ->
            mOnPhotoEditorListener?.onTouchSourceImage(event)
            mDetector.onTouchEvent(event)
        }
        photoEditorView.setClipSourceImage(builder.clipSourceImage)
    }
}