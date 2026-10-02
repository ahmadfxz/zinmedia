package com.zinmedia.photoeditor.imageeditor.crop

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

internal val FullRect = Rect(0f, 0f, 1f, 1f)

/** Pilihan rasio crop. [ratio] = lebar/tinggi; `null` = bebas, [Original] mengikuti gambar. */
internal enum class CropAspect(val label: String, val ratio: Float?) {
    Free("Bebas", null),
    Original("Asli", null),
    Square("1:1", 1f),
    Portrait4x5("4:5", 4f / 5f),
    Portrait3x4("3:4", 3f / 4f),
    Story("9:16", 9f / 16f),
    Landscape4x3("4:3", 4f / 3f),
    Wide("16:9", 16f / 9f);

    /** Rasio efektif untuk gambar berukuran [width]x[height]; `null` bila bebas. */
    fun ratioFor(width: Float, height: Float): Float? = if (this == Original) width / height else ratio

    /** Rasio kebalikan (dipakai setelah gambar diputar 90°). */
    fun rotated(): CropAspect = when (this) {
        Portrait3x4 -> Landscape4x3
        Landscape4x3 -> Portrait3x4
        Story -> Wide
        Wide -> Story
        Portrait4x5, Free, Original, Square -> this
    }
}

/**
 * Hasil crop yang bisa dibuka ulang: semua nilai relatif terhadap foto asli,
 * sehingga crop berikutnya selalu dimulai dari foto penuh.
 *
 * @param quarterTurns rotasi berlawanan jarum jam, kelipatan 90°.
 * @param flipped cermin horizontal, diterapkan setelah rotasi.
 * @param rect area crop ternormalisasi (0..1) terhadap gambar setelah rotasi & cermin.
 */
internal data class CropState(
    val quarterTurns: Int = 0,
    val flipped: Boolean = false,
    val rect: Rect = FullRect,
    val aspect: CropAspect = CropAspect.Free,
) {
    val isIdentity: Boolean get() = quarterTurns % 4 == 0 && !flipped && rect == FullRect
}

/** Posisi area crop setelah gambar diputar 90° berlawanan jarum jam. */
internal fun Rect.rotatedCcw(): Rect = Rect(top, 1f - right, bottom, 1f - left)

/** Posisi area crop setelah gambar dicerminkan horizontal. */
internal fun Rect.flippedHorizontally(): Rect = Rect(1f - right, top, 1f - left, bottom)

/**
 * Persegi panjang terbesar dengan rasio [ratio] di dalam [bounds], berpusat sedekat mungkin
 * dengan [center] tanpa keluar dari [bounds].
 */
internal fun largestRectWithRatio(bounds: Rect, ratio: Float, center: androidx.compose.ui.geometry.Offset): Rect {
    val width = minOf(bounds.width, bounds.height * ratio)
    val height = width / ratio
    val left = (center.x - width / 2f).coerceIn(bounds.left, bounds.right - width)
    val top = (center.y - height / 2f).coerceIn(bounds.top, bounds.bottom - height)
    return Rect(left, top, left + width, top + height)
}

/** Foto asli setelah rotasi & cermin (belum di-crop). */
internal fun Bitmap.transformed(quarterTurns: Int, flipped: Boolean): Bitmap {
    val turns = ((quarterTurns % 4) + 4) % 4
    if (turns == 0 && !flipped) return this
    val matrix = Matrix().apply {
        postRotate(-90f * turns)
        if (flipped) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/** Potong [this] (sudah ditransformasi) sesuai area ternormalisasi [rect]. */
internal fun Bitmap.cropNormalized(rect: Rect): Bitmap {
    if (rect == FullRect) return this
    val left = (rect.left * width).roundToInt().coerceIn(0, width - 1)
    val top = (rect.top * height).roundToInt().coerceIn(0, height - 1)
    val right = (rect.right * width).roundToInt().coerceIn(left + 1, width)
    val bottom = (rect.bottom * height).roundToInt().coerceIn(top + 1, height)
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
}
