package com.zinmedia.videoeditor

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * FileProvider khusus zinmedia videoeditor, terdaftar otomatis lewat manifest library
 * dengan authority `${applicationId}.zinmedia.videoeditor.fileprovider`, sehingga
 * tidak bergantung dan tidak bentrok dengan FileProvider milik aplikasi.
 */
class VideoEditorFileProvider : FileProvider(R.xml.zinmedia_videoeditor_file_paths) {

    companion object {
        /** Folder hasil edit; hanya folder ini yang dibuka lewat provider. */
        internal fun outputDir(context: Context): File =
            File(context.cacheDir, "zinmedia/videoeditor").apply { mkdirs() }

        internal fun uriFor(context: Context, file: File): Uri =
            getUriForFile(context, "${context.packageName}.zinmedia.videoeditor.fileprovider", file)
    }
}
