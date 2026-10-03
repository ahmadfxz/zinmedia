package com.zinmedia.videoeditor.data.repository

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Persegi dalam "satuan lebar video": frame video = [0, 1] × [0, heightRatio], sumbu y ke bawah.
 */
internal data class CanvasRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun union(other: CanvasRect) = CanvasRect(
        min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom),
    )

    fun intersect(other: CanvasRect) = CanvasRect(
        max(left, other.left), max(top, other.top), min(right, other.right), min(bottom, other.bottom),
    )

    /** Posisi titik ([x], [y]) dalam NDC kanvas ini (x kanan, y atas, [-1, 1]). */
    fun toNdc(x: Float, y: Float): Pair<Float, Float> =
        (x - centerX) / (width / 2f) to -(y - centerY) / (height / 2f)
}

/** Posisi & ukuran satu overlay relatif terhadap frame video. */
internal data class OverlayBox(
    /** Pusat overlay dalam NDC frame video (bisa di luar [-1, 1] bila overlay keluar frame). */
    val anchorX: Float,
    val anchorY: Float,
    /** Lebar overlay / lebar frame video. */
    val widthFraction: Float,
    /** Tinggi / lebar gambar overlay. */
    val aspect: Float,
    val rotationDegrees: Float,
)

/**
 * Kanvas hasil ekspor: frame video diperluas agar memuat semua overlay yang keluar frame (area
 * tambahannya hitam), dibatasi [limit] (area editor di layar, berpusat di tengah video).
 *
 * @param heightRatio tinggi / lebar frame video.
 * @param limit ukuran maksimum kanvas (lebar, tinggi) dalam satuan lebar video; `null` = tanpa perluasan.
 */
internal fun exportCanvas(boxes: List<OverlayBox>, heightRatio: Float, limit: Pair<Float, Float>?): CanvasRect {
    val video = CanvasRect(0f, 0f, 1f, heightRatio)
    if (limit == null) return video
    var canvas = video
    for (box in boxes) {
        if (box.widthFraction <= 0f) continue
        val w = box.widthFraction
        val h = box.widthFraction * box.aspect
        val radians = Math.toRadians(box.rotationDegrees.toDouble())
        val c = abs(cos(radians)).toFloat()
        val s = abs(sin(radians)).toFloat()
        // Kotak pembatas setelah diputar.
        val halfW = (w * c + h * s) / 2f
        val halfH = (w * s + h * c) / 2f
        val cx = (box.anchorX + 1f) / 2f
        val cy = (1f - box.anchorY) / 2f * heightRatio
        canvas = canvas.union(CanvasRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH))
    }
    val (maxW, maxH) = limit
    val bounds = CanvasRect(
        0.5f - maxW / 2f, heightRatio / 2f - maxH / 2f,
        0.5f + maxW / 2f, heightRatio / 2f + maxH / 2f,
    ).union(video)
    return canvas.intersect(bounds)
}
