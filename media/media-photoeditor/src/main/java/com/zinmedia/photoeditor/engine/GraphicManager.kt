package com.zinmedia.photoeditor.engine

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout

/**
 * Created by Burhanuddin Rashid on 15/05/21.
 *
 * @author <https:></https:>//github.com/burhanrashid52>
 */
internal class GraphicManager(
    private val mPhotoEditorView: PhotoEditorView,
    private val mViewState: PhotoEditorViewState,
    private val mDrawerViewState: DrawerEditorViewState,
) {

    var onPhotoEditorListener: OnPhotoEditorListener? = null

    val redoStackCount
        get() = mViewState.redoViewsCount

    fun addView(graphic: Graphic) {
        val view = graphic.rootView
        val params = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
        mPhotoEditorView.addView(view, params)
        mViewState.addAddedView(view)

        if (redoStackCount > 0) {
            mViewState.clearRedoViews()
        }

        onPhotoEditorListener?.onAddViewListener(
            graphic.viewType,
            mViewState.addedViewsCount
        )
    }

    fun removeView(graphic: Graphic) = removeView(graphic.rootView)

    /** Hapus lapisan (teks/stiker/emoji); tipenya dibaca dari tag view. */
    fun removeView(view: View) {
        val viewType = view.tag as? ViewType ?: return
        if (mViewState.containsAddedView(view)) {
            mPhotoEditorView.removeView(view)
            mViewState.removeAddedView(view)
            mViewState.pushRedoView(view)
            onPhotoEditorListener?.onRemoveViewListener(
                viewType,
                mViewState.addedViewsCount
            )
        }
    }

    fun updateView(view: View) {
        mPhotoEditorView.updateViewLayout(view, view.layoutParams)
        mViewState.replaceAddedView(view)
    }

////                mViewState.removeAddedView(mViewState.addedViewsCount - 1)
////                mPhotoEditorView.removeView(removeView)
////                mViewState.pushRedoView(removeView)
    fun undoView(): Boolean {
        if (mDrawerViewState.addedViewsCount == 0) return false

        val lastView = mDrawerViewState.getAddedView(mDrawerViewState.addedViewsCount - 1)

        return if (lastView is DrawingView) {
            lastView.undo().also { success ->
            }
        } else {
            false
        }
    }

    fun redoView(): Boolean {
        if (redoStackCount > 0) {
            val redoView = mViewState.getRedoView(redoStackCount - 1)

            if (redoView is DrawingView) {
                val result = redoView.redo()
                return result || redoStackCount > 0
            } else {
                mViewState.popRedoView()
                mPhotoEditorView.addView(redoView)
                mViewState.addAddedView(redoView)
            }

            val viewTag = redoView.tag
            if (viewTag is ViewType) {
                onPhotoEditorListener?.onAddViewListener(viewTag, mViewState.addedViewsCount)
            }
        }

        return redoStackCount > 0
    }
}