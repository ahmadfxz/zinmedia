package com.zinmedia.photoeditor.engine


/**
 * Created by Burhanuddin Rashid on 18/05/21.
 *
 * @author <https:></https:>//github.com/burhanrashid52>
 */
internal class BoxHelper(
    private val mPhotoEditorView: PhotoEditorView,
    private val mViewState: PhotoEditorViewState
) {
    fun clearHelperBox() {
        mViewState.clearCurrentSelectedView()
    }

    fun clearAllViews(drawingView: DrawingView?) {
        for (i in 0 until mViewState.addedViewsCount) {
            mPhotoEditorView.removeView(mViewState.getAddedView(i))
        }
        drawingView?.let {
            if (mViewState.containsAddedView(it)) {
                mPhotoEditorView.addView(it)
            }
        }

        mViewState.clearAddedViews()
        mViewState.clearRedoViews()
        drawingView?.clearAll()
    }
}