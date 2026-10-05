package com.zinmedia.effects

import com.zinmedia.effects.gl.parseCube
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectFilterTest {

    private fun apply(t: ColorTransform, r: Float, g: Float, b: Float): FloatArray {
        val m = t.matrix
        val o = t.offset
        return FloatArray(3) { row -> m[row * 3] * r + m[row * 3 + 1] * g + m[row * 3 + 2] * b + o[row] }
    }

    @Test
    fun saturationZero_givesGray() {
        val out = apply(ColorTransform().saturation(0f), 1f, 0f, 0f)
        assertEquals(out[0], out[1], 1e-5f)
        assertEquals(out[1], out[2], 1e-5f)
        assertEquals(0.2126f, out[0], 1e-4f)
    }

    @Test
    fun contrast_keepsMidGray() {
        val out = apply(ColorTransform().contrast(1.5f), 0.5f, 0.5f, 0.5f)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.5f), out, 1e-5f)
    }

    @Test
    fun transforms_areAppliedInOrder() {
        // Kontras dulu lalu terang: 0.25 -> 0 (kontras 2x) -> 0.1.
        val out = apply(ColorTransform().contrast(2f).brightness(0.1f), 0.25f, 0.25f, 0.25f)
        assertEquals(0.1f, out[0], 1e-5f)
    }

    @Test
    fun parseCube_laysOutBlueSlicesSideBySide() {
        // LUT 2x2x2 identitas: urutan data merah tercepat, lalu hijau, lalu biru.
        val text = buildString {
            appendLine("TITLE \"uji\"")
            appendLine("LUT_3D_SIZE 2")
            for (b in 0..1) for (g in 0..1) for (r in 0..1) appendLine("$r $g $b")
        }
        val lut = parseCube(text.byteInputStream())!!
        assertEquals(4, lut.width)
        assertEquals(2, lut.height)
        // Piksel (x = b*2 + r, y = g): ambil (r=1, g=0, b=1) -> x=3, y=0.
        val index = (0 * lut.width + 3) * 4
        assertEquals(255, lut.rgba.get(index).toInt() and 0xFF)
        assertEquals(0, lut.rgba.get(index + 1).toInt() and 0xFF)
        assertEquals(255, lut.rgba.get(index + 2).toInt() and 0xFF)
    }

    @Test
    fun parseCube_rejectsIncompleteData() {
        assertNull(parseCube("LUT_3D_SIZE 2\n0 0 0\n".byteInputStream()))
    }
}
