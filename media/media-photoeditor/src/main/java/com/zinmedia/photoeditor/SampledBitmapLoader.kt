package com.zinmedia.photoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build

/**
 * Memuat gambar dari [Uri] dengan resolusi yang dibatasi [maxSize] (sisi terpanjang).
 *
 * Hasil edit disimpan seukuran view (layar), jadi memuat foto kamera full-resolution
 * hanya membuang memori dan berisiko OutOfMemoryError. Harus dipanggil di luar main thread.
 */
internal object SampledBitmapLoader {

    private const val DEFAULT_MAX_SIZE = 2048

    fun load(context: Context, uri: Uri, maxSize: Int = DEFAULT_MAX_SIZE): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Bitmap software: engine editor menggambar ke Canvas & membaca pixel-nya.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height, maxSize))
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSize)
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /** Pangkat dua terbesar yang membuat sisi terpanjang tetap >= [maxSize]. */
    private fun sampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxSize) sample *= 2
        return sample
    }
}
