package com.zinmedia.videoeditor

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.media3.common.util.UnstableApi
import com.zinmedia.videoeditor.data.VideoEditorViewModel
import com.zinmedia.videoeditor.data.VideoEditorViewModelFactory
import com.zinmedia.videoeditor.data.repository.VideoRepository
import java.io.File

/**
 * Akses ke state editor video per [VideoEditorScreen] `sessionKey`, untuk layar yang mengedit
 * beberapa media sekaligus. State disimpan di [ViewModelStoreOwner] yang sama dengan layar editor.
 */
public object VideoEditorSessions {

    @OptIn(UnstableApi::class)
    private fun viewModel(owner: ViewModelStoreOwner, key: String): VideoEditorViewModel =
        ViewModelProvider(owner, VideoEditorViewModelFactory(VideoRepository()))[key, VideoEditorViewModel::class.java]

    /** Video dengan [key] punya teks/stiker/coretan, filter, atau trim. */
    @OptIn(UnstableApi::class)
    public fun hasEdits(owner: ViewModelStoreOwner, key: String): Boolean = viewModel(owner, key).hasEdits()

    /**
     * Ekspor video dengan [key] bila ada hasil edit.
     * @return URI hasil (FileProvider milik library), atau `null` bila tidak ada yang perlu diekspor.
     */
    @OptIn(UnstableApi::class)
    public suspend fun exportIfEdited(owner: ViewModelStoreOwner, key: String, context: Context): Uri? {
        val vm = viewModel(owner, key)
        if (!vm.hasEdits()) return null
        val output = File(VideoEditorFileProvider.outputDir(context), "video_${key}_${System.currentTimeMillis()}.mp4")
        val path = vm.exportToFile(context, output)
        return VideoEditorFileProvider.uriFor(context, File(path))
    }
}
