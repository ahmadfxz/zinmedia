package com.zinmedia.photoeditor.engine

import android.view.MotionEvent
import android.view.View

/**
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @version 0.1.1
 * @since 18/01/2017
 *
 *
 * This are the callbacks when any changes happens while editing the photo to make and custimization
 * on client side
 *
 */
internal interface OnPhotoEditorListener {

    /** Lapisan teks diketuk dua kali; [layer] adalah data yang disimpan saat lapisan dibuat. */
    fun onTextLayerClick(rootView: View, layer: Any?)

    /** Lapisan [view] sedang diseret; posisi jari dalam koordinat layar. */
    fun onLayerDrag(view: View, rawX: Float, rawY: Float)

    /** Seret lapisan [view] selesai ([cancelled] bila gesture dibatalkan sistem). */
    fun onLayerDragEnd(view: View, cancelled: Boolean)

    /**
     * This is a callback when user adds any view on the [PhotoEditorView] it can be
     * brush,text or sticker i.e bitmap on parent view
     *
     * @param viewType           enum which define type of view is added
     * @param numberOfAddedViews number of views currently added
     * @see ViewType
     */
    fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int)

    /**
     * This is a callback when user remove any view on the [PhotoEditorView] it happens when usually
     * undo and redo happens or text is removed
     *
     * @param viewType           enum which define type of view is added
     * @param numberOfAddedViews number of views currently added
     */
    fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int)

    /**
     * A callback when user start dragging a view which can be
     * any of [ViewType]
     *
     * @param viewType enum which define type of view is added
     */
    fun onStartViewChangeListener(viewType: ViewType)

    /**
     * A callback when user stop/up touching a view which can be
     * any of [ViewType]
     *
     * @param viewType enum which define type of view is added
     */
    fun onStopViewChangeListener(viewType: ViewType)

    /**
     * A callback when the user touches the screen.
     *
     * @param event the MotionEvent associated to the touch.
     */
    fun onTouchSourceImage(event: MotionEvent)
}