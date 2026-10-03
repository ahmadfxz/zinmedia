package com.zinmedia.photoeditor.engine

import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import com.zinmedia.photoeditor.R

/**
 * Lapisan teks yang sudah dirender menjadi gambar (lihat `renderTextLayer`), beserta datanya
 * ([layer]) agar bisa diedit ulang. Ketuk sekali untuk memilih & menggeser (seperti stiker),
 * ketuk dua kali untuk mengedit.
 */
internal class TextLayerGraphic(
    private val photoEditorView: PhotoEditorView,
    private val multiTouchListener: MultiTouchListener,
    private val viewState: PhotoEditorViewState,
    private val manager: GraphicManager,
) : Graphic(
    context = photoEditorView.context,
    graphicManager = manager,
    viewType = ViewType.TEXT,
    layoutId = R.layout.zm_view_photo_editor_image
) {
    private var imageView: ImageView? = null

    var layer: Any? = null
        private set

    fun buildView(image: Bitmap, layer: Any) {
        imageView?.setImageBitmap(image)
        this.layer = layer
        rootView.setTag(R.id.zm_tag_text_layer, layer)
    }

    override fun setupView(rootView: View) {
        imageView = rootView.findViewById(R.id.imgPhotoEditorImage)
    }

    init {
        val selection = buildGestureController(photoEditorView, viewState)
        multiTouchListener.setOnGestureControl(object : MultiTouchListener.OnGestureControl {
            override fun onClick() = selection.onClick()
            override fun onLongClick() = selection.onLongClick()
            override fun onDoubleClick() {
                manager.onPhotoEditorListener?.onTextLayerClick(rootView, layer)
            }
        })
        rootView.setOnTouchListener(multiTouchListener)
    }
}
