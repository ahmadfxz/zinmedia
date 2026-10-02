package com.zinmedia.photoeditor.imageeditor

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zinmedia.photoeditor.engine.PhotoEditorView

@Composable
fun PhotoEditorContainer(
    photoEditorView: PhotoEditorView,
    modifier: Modifier = Modifier
) {
    AndroidView(
            factory = { context ->
                photoEditorView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = modifier
        )
}