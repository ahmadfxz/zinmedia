package com.zinmedia.photoeditor.engine

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.zinmedia.photoeditor.R
import com.zinmedia.photoeditor.engine.MultiTouchListener.OnGestureControl

/**
 * Created by Burhanuddin Rashid on 14/05/21.
 *
 * @author <https:></https:>//github.com/burhanrashid52>
 */
internal abstract class Graphic(
    val context: Context,
    val layoutId: Int,
    val viewType: ViewType,
    val graphicManager: GraphicManager?) {

    val rootView: View

    open fun updateView(view: View) {
        //Optional for subclass to override
    }

    init {
        if (layoutId == 0) {
            throw UnsupportedOperationException("Layout id cannot be zero. Please define a layout")
        }
        rootView = LayoutInflater.from(context).inflate(layoutId, null)
        setupView(rootView)
        setupRemoveView(rootView)
    }

    private fun setupRemoveView(rootView: View) {
        //We are setting tag as ViewType to identify what type of the view it is
        //when we remove the view from stack i.e onRemoveViewListener(ViewType viewType, int numberOfAddedViews);
        rootView.tag = viewType
    }

    protected fun buildGestureController(
        photoEditorView: PhotoEditorView,
        viewState: PhotoEditorViewState
    ): OnGestureControl {
        val boxHelper = BoxHelper(photoEditorView, viewState)
        return object : OnGestureControl {
            override fun onClick() {
                // Tanpa bingkai/tombol X: lapisan dihapus dengan diseret ke tempat sampah.
                boxHelper.clearHelperBox()
                // Change the in-focus view
                viewState.currentSelectedView = rootView
            }

            override fun onLongClick() {
            }

            override fun onDoubleClick() {
               updateView(rootView)
            }
        }
    }

    open fun setupView(rootView: View) {}
}

