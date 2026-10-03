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
 * Kanvas hasil ekspor: seukuran frame video bila semua overlay ada di dalamnya; bila ada yang
 * keluar frame, kanvas memenuhi [limit] (area editor di layar, berpusat di tengah video) dengan
 * area tambahan hitam.
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
    // Semua overlay di dalam frame (toleransi pembulatan): hasil seukuran video.
    val overflows = canvas.left < -Tolerance || canvas.top < -Tolerance ||
        canvas.right > 1f + Tolerance || canvas.bottom > heightRatio + Tolerance
    if (!overflows) return video
    // Ada yang keluar frame: kanvas memenuhi batas maksimal (area editor), sisanya hitam.
    val (maxW, maxH) = limit
    return CanvasRect(
        0.5f - maxW / 2f, heightRatio / 2f - maxH / 2f,
        0.5f + maxW / 2f, heightRatio / 2f + maxH / 2f,
    ).union(video)
}

/** Toleransi (satuan lebar video) agar overlay yang menempel di tepi tidak dianggap keluar. */
private const val Tolerance = 0.002f

/** Titik tempel overlay untuk Media3: di latar (NDC kanvas) dan di overlay (NDC overlay). */
internal data class OverlayPlacement(
    val backgroundX: Float,
    val backgroundY: Float,
    val overlayX: Float = 0f,
    val overlayY: Float = 0f,
)

/**
 * Tempatkan overlay berpusat di ([centerX], [centerY]) (NDC kanvas, y ke atas). Media3 hanya
 * menerima titik tempel latar di [-1, 1]; bila pusat overlay di luar kanvas, titik tempel latar
 * dijepit ke tepi dan titik tempel overlay digeser sebaliknya (rotasi diperhitungkan), sehingga
 * posisi overlay tetap sama dan bagian yang masih di kanvas tetap tampil.
 *
 * @param halfWidthPx setengah lebar overlay di video hasil (px, setelah skala).
 * @return `null` bila overlay seluruhnya di luar kanvas (tidak perlu digambar).
 */
internal fun placeOverlay(
    centerX: Float,
    centerY: Float,
    halfWidthPx: Float,
    halfHeightPx: Float,
    rotationDegrees: Float,
    outputWidth: Int,
    outputHeight: Int,
): OverlayPlacement? {
    if (centerX in -1f..1f && centerY in -1f..1f) return OverlayPlacement(centerX, centerY)
    if (halfWidthPx <= 0f || halfHeightPx <= 0f) return null
    val backgroundX = centerX.coerceIn(-1f, 1f)
    val backgroundY = centerY.coerceIn(-1f, 1f)
    // Selisih pusat terhadap titik tempel, dalam px video hasil (y ke atas).
    val dx = (centerX - backgroundX) * outputWidth / 2f
    val dy = (centerY - backgroundY) * outputHeight / 2f
    // Ke sistem koordinat overlay (sebelum rotasi): putar balik.
    val radians = Math.toRadians(-rotationDegrees.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val localX = dx * c - dy * s
    val localY = dx * s + dy * c
    val overlayX = -localX / halfWidthPx
    val overlayY = -localY / halfHeightPx
    if (abs(overlayX) > 1f || abs(overlayY) > 1f) return null
    return OverlayPlacement(backgroundX, backgroundY, overlayX, overlayY)
}
