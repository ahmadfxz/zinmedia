package com.zinmedia.photoeditor.imageeditor

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.zinmedia.photoeditor.engine.PhotoEditorView

@Composable
internal fun PhotoEditorContainer(
    photoEditorView: PhotoEditorView,
    modifier: Modifier = Modifier
) {
    // Pager memakai ulang komposisi halaman lain; tanpa key, AndroidView tetap memegang view foto
    // sebelumnya (factory tidak dipanggil lagi) sehingga lapisan foto lain ikut tampil.
    key(photoEditorView) {
        AndroidView(
            factory = {
                // View yang sama dipasang ulang saat halaman dibuka lagi; lepas dari induk lama dulu.
                (photoEditorView.parent as? ViewGroup)?.removeView(photoEditorView)
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
}
