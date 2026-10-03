package com.zinmedia.camera

import com.zinmedia.videoeditor.VideoEditorConfig

/**
 * Filter warna kamera: transformasi warna affine (`out = matrix × rgb + offset`) dan/atau LUT `.cube`.
 * Diterapkan real-time di GPU, sama untuk preview, foto, dan video.
 */
internal data class CameraFilter(
    val name: String,
    /** Matriks 3×3 baris-mayor. */
    val matrix: FloatArray = IdentityMatrix,
    val offset: FloatArray = ZeroOffset,
    /** URL file `.cube`; `null` = tanpa LUT. */
    val lutUrl: String? = null,
    /** Warna contoh untuk tombol filter. */
    val swatch: Long = 0xFF808080,
) {
    override fun equals(other: Any?): Boolean = other is CameraFilter && other.name == name
    override fun hashCode(): Int = name.hashCode()
}

private val IdentityMatrix = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
private val ZeroOffset = floatArrayOf(0f, 0f, 0f)

/**
 * Penyusun transformasi warna: tiap langkah dikomposisikan setelah langkah sebelumnya.
 */
internal class ColorTransform {
    var matrix: FloatArray = IdentityMatrix.copyOf()
        private set
    var offset: FloatArray = ZeroOffset.copyOf()
        private set

    /** Terapkan `rgb' = m × rgb + o` setelah transformasi sekarang. */
    fun then(m: FloatArray, o: FloatArray = ZeroOffset): ColorTransform {
        val newMatrix = FloatArray(9)
        val newOffset = FloatArray(3)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                var sum = 0f
                for (k in 0 until 3) sum += m[row * 3 + k] * matrix[k * 3 + col]
                newMatrix[row * 3 + col] = sum
            }
            var off = o[row]
            for (k in 0 until 3) off += m[row * 3 + k] * offset[k]
            newOffset[row] = off
        }
        matrix = newMatrix
        offset = newOffset
        return this
    }

    fun saturation(s: Float): ColorTransform {
        val r = 0.2126f * (1 - s)
        val g = 0.7152f * (1 - s)
        val b = 0.0722f * (1 - s)
        return then(floatArrayOf(r + s, g, b, r, g + s, b, r, g, b + s))
    }

    fun contrast(c: Float): ColorTransform {
        val o = 0.5f * (1 - c)
        return then(floatArrayOf(c, 0f, 0f, 0f, c, 0f, 0f, 0f, c), floatArrayOf(o, o, o))
    }

    fun brightness(b: Float): ColorTransform = then(IdentityMatrix, floatArrayOf(b, b, b))

    /** Positif = hangat (lebih merah/kuning), negatif = dingin (lebih biru). */
    fun warmth(w: Float): ColorTransform = then(IdentityMatrix, floatArrayOf(w, w * 0.3f, -w))

    fun sepia(): ColorTransform = then(
        floatArrayOf(
            0.393f, 0.769f, 0.189f,
            0.349f, 0.686f, 0.168f,
            0.272f, 0.534f, 0.131f,
        )
    )

    fun build(name: String, swatch: Long): CameraFilter = CameraFilter(name, matrix, offset, swatch = swatch)
}

/** Filter bawaan, lalu filter LUT milik aplikasi (dari [VideoEditorConfig.filters]). */
internal fun cameraFilters(): List<CameraFilter> = BuiltInFilters + VideoEditorConfig.filters.map {
    CameraFilter(name = it.name, lutUrl = it.cubeUrl, swatch = 0xFF6D6D6D)
}

internal val NormalFilter = CameraFilter("Normal", swatch = 0xFF9E9E9E)

private val BuiltInFilters: List<CameraFilter> = listOf(
    NormalFilter,
    ColorTransform().saturation(1.35f).contrast(1.1f).build("Cerah", 0xFFFF7043),
    ColorTransform().warmth(0.06f).saturation(1.1f).brightness(0.02f).build("Hangat", 0xFFFFB74D),
    ColorTransform().warmth(-0.06f).saturation(0.95f).build("Sejuk", 0xFF4FC3F7),
    ColorTransform().contrast(0.85f).saturation(0.75f).warmth(0.04f).brightness(0.04f).build("Vintage", 0xFFBCAAA4),
    ColorTransform().saturation(0f).contrast(1.15f).build("Hitam Putih", 0xFF616161),
    ColorTransform().sepia().build("Sepia", 0xFF8D6E63),
    ColorTransform().contrast(1.3f).saturation(1.15f).brightness(-0.03f).build("Drama", 0xFF5C6BC0),
    ColorTransform().contrast(0.9f).brightness(0.06f).saturation(0.9f).build("Lembut", 0xFFF8BBD0),
)
