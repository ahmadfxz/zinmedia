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
public class VideoEditorFileProvider : FileProvider(R.xml.zm_videoeditor_file_paths) {

    public companion object {
        /** Folder hasil edit; hanya folder ini yang dibuka lewat provider. */
        public fun outputDir(context: Context): File =
            File(context.cacheDir, "zinmedia/videoeditor").apply { mkdirs() }

        /**
         * Hapus hasil edit lama (default: lebih dari 24 jam) agar cache tidak terus membesar.
         * Hasil terbaru tetap ada supaya aplikasi sempat membaca/mengunggahnya.
         */
        public fun deleteOldOutputs(context: Context, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Int =
            deleteFilesOlderThan(outputDir(context), maxAgeMs)

        private const val DEFAULT_MAX_AGE_MS: Long = 24 * 60 * 60 * 1000L

        public fun uriFor(context: Context, file: File): Uri =
            getUriForFile(context, "${context.packageName}.zinmedia.videoeditor.fileprovider", file)
    }
}

/** Hapus file di [dir] yang terakhir diubah lebih dari [maxAgeMs] sebelum [now]; mengembalikan jumlahnya. */
internal fun deleteFilesOlderThan(dir: File, maxAgeMs: Long, now: Long = System.currentTimeMillis()): Int =
    dir.listFiles().orEmpty().count { file ->
        file.isFile && now - file.lastModified() > maxAgeMs && file.delete()
    }
