package com.zinmedia.composer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.zinmedia.photoeditor.PhotoEditorState
import java.util.UUID

internal enum class MediaType(val resultName: String) { Image("image"), Video("video") }

/** Satu media di editor gabungan. Foto membawa [photo] (state editornya); video memakai [id] sebagai kunci sesi. */
internal class ComposerItem(
    val uri: Uri,
    val type: MediaType,
    val photo: PhotoEditorState?,
    val id: String = UUID.randomUUID().toString(),
)

internal fun Context.mediaTypeOf(uri: Uri): MediaType? {
    val mime = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        contentResolver.getType(uri)
    } else {
        MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            ?.lowercase()
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    }
    return when {
        mime == null -> null
        mime.startsWith("video/") -> MediaType.Video
        mime.startsWith("image/") -> MediaType.Image
        else -> null
    }
}
