package com.zinmedia.photoeditor.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import androidx.annotation.IntRange
import androidx.annotation.UiThread
import com.zinmedia.photoeditor.engine.shape.ShapeBuilder

/**
 * Created by Burhanuddin Rashid on 14/05/21.
 *
 * @author <https:></https:>//github.com/burhanrashid52>
 */
internal interface PhotoEditor {
    /**
     * This will add image on [PhotoEditorView] which you drag,rotate and scale using pinch
     * if [PhotoEditor.Builder.setPinchTextScalable] enabled
     *
     * @param desiredImage bitmap image you want to add
     */
    fun addImage(desiredImage: Bitmap)

    /**
     * Tambah lapisan teks yang sudah dirender ([image]) beserta datanya ([layer]) untuk diedit ulang.
     * [pixelScale] = piksel [image] per piksel layar (ditampilkan seukuran layar, tajam saat ekspor).
     */
    fun addTextLayer(image: Bitmap, layer: Any, pixelScale: Float = 1f)

    /** Ganti gambar & data lapisan teks pada [view]. */
    fun editTextLayer(view: View, image: Bitmap, layer: Any, pixelScale: Float = 1f)

    /** Piksel foto per piksel layar (min. 1, maks. 4): resolusi render teks agar tajam saat ekspor. */
    val photoPixelScale: Float

    /** Hapus lapisan [view] (teks/stiker/emoji). */
    fun removeLayer(view: View)

    /**
     * Adds emoji to the [PhotoEditorView] which you drag,rotate and scale using pinch
     * if [PhotoEditorImpl.Builder.setPinchTextScalable] enabled
     *
     * @param emojiName unicode in form of string to display emoji
     */
    fun addEmoji(emojiName: String)

    /**
     * Adds emoji to the [PhotoEditorView] which you drag,rotate and scale using pinch
     * if [PhotoEditorImpl.Builder.setPinchTextScalable] enabled
     *
     * @param emojiTypeface typeface for custom font to show emoji unicode in specific font
     * @param emojiName     unicode in form of string to display emoji
     */
    fun addEmoji(emojiTypeface: Typeface?, emojiName: String)

    /**
     * Enable/Disable drawing mode to draw on [PhotoEditorView]
     *
     * @param brushDrawingMode true if mode is enabled
     */
    fun setBrushDrawingMode(brushDrawingMode: Boolean)

    /**
     * @return true is brush mode is enabled
     */
    val brushDrawableMode: Boolean?

    /**
     * set opacity/transparency of brush while painting on [DrawingView]
     * @param opacity opacity is in form of percentage
     */
    @Deprecated(
        """use {@code setShape} of a ShapeBuilder
     
      """
    )
    fun setOpacity(@IntRange(from = 0, to = 100) opacity: Int)

    /**
     * set the eraser size
     * **Note :** Eraser size is different from the normal brush size
     *
     * @param brushEraserSize size of eraser
     */
    fun setBrushEraserSize(brushEraserSize: Float)

    /**
     * @return provide the size of eraser
     * @see PhotoEditor.setBrushEraserSize
     */
    val eraserSize: Float
    /**
     * @return provide the size of eraser
     * @see PhotoEditor.setBrushSize
     */
    /**
     * Set the size of brush user want to paint on canvas i.e [DrawingView]
     * @param size size of brush
     */
    @set:Deprecated(
        """use {@code setShape} of a ShapeBuilder
     
      """
    )
    var brushSize: Float
    /**
     * @return provide the size of eraser
     * @see PhotoEditor.setBrushColor
     */
    /**
     * set brush color which user want to paint
     * @param color color value for paint
     */
    @set:Deprecated(
        """use {@code setShape} of a ShapeBuilder
     
      """
    )
    var brushColor: Int

    /**
     *
     *
     * Its enables eraser mode after that whenever user drags on screen this will erase the existing
     * paint
     * <br></br>
     * **Note** : This eraser will work on paint views only
     *
     *
     */
    fun brushEraser()

    /**
     * Undo the last operation perform on the [PhotoEditor]
     *
     * @return true if there nothing more to undo
     */
    fun undo(): Boolean

    /**
     * Returns whether any undo operation is available.
     *
     * @return `true` if no undo operations available, `false` otherwise
     */
    val isUndoAvailable: Boolean

    /**
     * Redo the last operation perform on the [PhotoEditor]
     *
     * @return true if there nothing more to redo
     */
    fun redo(): Boolean

    /**
     * Returns whether any redo operation is available.
     *
     * @return `true` if no redo operations available, `false` otherwise
     */
    val isRedoAvailable: Boolean

    /**
     * Removes all the edited operations performed [PhotoEditorView]
     * This will also clear the undo and redo stack
     */
    fun clearAllViews()

    /**
     * Remove all helper boxes from views
     */
    @UiThread
    fun clearHelperBox()

    /**
     * Setup of custom effect using effect type and set parameters values
     *
     * @param customEffect [CustomEffect.Builder.setParameter]
     */
    fun setFilterEffect(customEffect: CustomEffect?)

    /**
     * Set pre-define filter available
     *
     * @param filterType type of filter want to apply [PhotoEditorImpl]
     */
    fun setFilterEffect(filterType: PhotoFilter)

    /**
     * Save the edited image on given path
     *
     * @param imagePath      path on which image to be saved
     * @param saveSettings   builder for multiple save options [SaveSettings]
     */

    /**
     * Save the edited image as bitmap
     *
     * @param saveSettings builder for multiple save options [SaveSettings]
     */
    suspend fun saveAsBitmap(saveSettings: SaveSettings = SaveSettings.Builder().build()): Bitmap

    fun saveAsBitmap(saveSettings: SaveSettings, onSaveBitmap: OnSaveBitmap)

    fun saveAsBitmap(onSaveBitmap: OnSaveBitmap)

    /**
     * Callback on editing operation perform on [PhotoEditorView]
     *
     * @param onPhotoEditorListener [OnPhotoEditorListener]
     */
    fun setOnPhotoEditorListener(onPhotoEditorListener: OnPhotoEditorListener)

    /**
     * Check if any changes made need to save
     *
     * @return true if nothing is there to change
     */
    val isCacheEmpty: Boolean

    /**
     * Builder pattern to define [PhotoEditor] Instance
     */
    class Builder(internal var context: Context, internal var photoEditorView: PhotoEditorView) {

        @JvmField
        internal var imageView: ImageView = photoEditorView.source

        @JvmField
        internal var deleteView: View? = null

        @JvmField
        internal var drawingView: DrawingView = photoEditorView.drawingView

        @JvmField
        internal var textTypeface: Typeface? = null

        @JvmField
        internal var emojiTypeface: Typeface? = null

        // By default, pinch-to-scale is enabled for text
        @JvmField
        internal var isTextPinchScalable = true

        @JvmField
        internal var clipSourceImage = false
        internal fun setDeleteView(deleteView: View?): Builder {
            this.deleteView = deleteView
            return this
        }

        /**
         * set default text font to be added on image
         *
         * @param textTypeface typeface for custom font
         * @return [Builder] instant to build [PhotoEditor]
         */
        internal fun setDefaultTextTypeface(textTypeface: Typeface?): Builder {
            this.textTypeface = textTypeface
            return this
        }

        /**
         * set default font specific to add emojis
         *
         * @param emojiTypeface typeface for custom font
         * @return [Builder] instant to build [PhotoEditor]
         */
        internal fun setDefaultEmojiTypeface(emojiTypeface: Typeface?): Builder {
            this.emojiTypeface = emojiTypeface
            return this
        }

        /**
         * Set false to disable pinch-to-scale for text inserts.
         * Set to "true" by default.
         *
         * @param isTextPinchScalable flag to make pinch to zoom for text inserts.
         * @return [Builder] instant to build [PhotoEditor]
         */
        internal fun setPinchTextScalable(isTextPinchScalable: Boolean): Builder {
            this.isTextPinchScalable = isTextPinchScalable
            return this
        }

        /**
         * @return build PhotoEditor instance
         */
        internal fun build(): PhotoEditor {
            return PhotoEditorImpl(this)
        }

        /**
         * Set true true to clip the drawing brush to the source image.
         *
         * @param clip a boolean to indicate if brush drawing is clipped or not.
         */
        internal fun setClipSourceImage(clip: Boolean): Builder {
            clipSourceImage = clip
            return this
        }

    }

    /**
     * A callback to save the edited image asynchronously
     */
    interface OnSaveListener {
        /**
         * Call when edited image is saved successfully on given path
         *
         * @param imagePath path on which image is saved
         */
        fun onSuccess(imagePath: String)

        /**
         * Call when failed to saved image on given path
         *
         * @param exception exception thrown while saving image
         */
        fun onFailure(exception: Exception)
    }

    // region Shape
    /**
     * Update the current shape to be drawn,
     * through the use of a ShapeBuilder.
     */
    fun setShape(shapeBuilder: ShapeBuilder) // endregion
}