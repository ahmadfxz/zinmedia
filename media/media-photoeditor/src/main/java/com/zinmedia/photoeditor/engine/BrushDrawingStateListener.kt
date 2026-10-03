package com.zinmedia.photoeditor.engine

/**
 * Created by Burhanuddin Rashid on 17/05/21.
 *
 * @author <https:></https:>//github.com/burhanrashid52>
 */
internal class BrushDrawingStateListener internal constructor(
    private val mPhotoEditorView: PhotoEditorView,
    private val mViewState: DrawerEditorViewState
) : BrushViewChangeListener {
    private var mOnPhotoEditorListener: OnPhotoEditorListener? = null
    internal fun setOnPhotoEditorListener(onPhotoEditorListener: OnPhotoEditorListener?) {
        mOnPhotoEditorListener = onPhotoEditorListener
    }

    override fun onViewAdd(drawingView: DrawingView) {
        mViewState.addAddedView(drawingView)
        mOnPhotoEditorListener?.onAddViewListener(
            ViewType.BRUSH_DRAWING,
            mViewState.addedViewsCount
        )
    }

    override fun onViewRemoved(drawingView: DrawingView) {
        if (mViewState.addedViewsCount > 0) {
            val removeView = mViewState.removeAddedView(
                mViewState.addedViewsCount - 1
            )
            if (removeView !is DrawingView) {
                mPhotoEditorView.removeView(removeView)
            }
        }
        mOnPhotoEditorListener?.onRemoveViewListener(
            ViewType.BRUSH_DRAWING,
            mViewState.addedViewsCount
        )
    }

    override fun onStartDrawing() {
        mOnPhotoEditorListener?.onStartViewChangeListener(ViewType.BRUSH_DRAWING)

    }

    override fun onStopDrawing() {
        mOnPhotoEditorListener?.onStopViewChangeListener(ViewType.BRUSH_DRAWING)
    }
}