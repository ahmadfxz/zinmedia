package com.zinmedia.photoeditor

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * FileProvider khusus zinmedia photoeditor, terdaftar otomatis lewat manifest library
 * dengan authority `${applicationId}.zinmedia.photoeditor.fileprovider`, sehingga
 * tidak bergantung dan tidak bentrok dengan FileProvider milik aplikasi.
 */
class PhotoEditorFileProvider : FileProvider(R.xml.zinmedia_photoeditor_file_paths) {

    companion object {
        /** Folder hasil edit; hanya folder ini yang dibuka lewat provider. */
        internal fun outputDir(context: Context): File =
            File(context.cacheDir, "zinmedia/photoeditor").apply { mkdirs() }

        internal fun uriFor(context: Context, file: File): Uri =
            getUriForFile(context, "${context.packageName}.zinmedia.photoeditor.fileprovider", file)
    }
}
