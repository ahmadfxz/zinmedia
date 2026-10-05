package com.zinmedia.effects

import com.zinmedia.effects.gl.parseGlb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

class GlbModelTest {

    /** Aset contoh yang sudah ditempatkan di ruang kepala standar (cm). */
    private fun asset(name: String) = parseGlb(File("../../sample/src/main/assets/face_3d/$name.glb").readBytes())

    private fun bounds(name: String): Pair<FloatArray, FloatArray> {
        val points = asset(name).parts.flatMap { p -> p.positions.toList() }
        val lo = FloatArray(3) { c -> points.filterIndexed { i, _ -> i % 3 == c }.min() }
        val hi = FloatArray(3) { c -> points.filterIndexed { i, _ -> i % 3 == c }.max() }
        return lo to hi
    }

    @Test
    fun glasses_sitAtEyeLevel_inFrontOfFace() {
        val (lo, hi) = bounds("kacamata_hitam")
        assertEquals(16.0f, hi[0] - lo[0], 0.05f)
        assertTrue("depan bingkai di depan mata", hi[2] in 6.5f..7.2f)
        assertTrue("setinggi mata", lo[1] < 2.66f && hi[1] > 2.66f)
    }

    @Test
    fun hat_sitsOnTopOfHead() {
        val (lo, hi) = bounds("topi_nelayan")
        // Pinggiran sekitar dahi atas (y 8,26 pada kepala standar), puncaknya di atas kepala.
        assertTrue(lo[1] in 4f..9f && hi[1] > 12f)
    }

    @Test
    fun texturedAsset_hasTextureAndUv() {
        val part = asset("kacamata_sport").parts.single()
        assertTrue(part.textureBytes != null && part.texCoords!!.size / 2 == part.positions.size / 3)
    }

    @Test
    fun allAssets_parseWithUnitNormals() {
        val names = File("../../sample/src/main/assets/face_3d").list()!!.filter { it.endsWith(".glb") }
        assertEquals(6, names.size)
        for (name in names) {
            val model = asset(name.removeSuffix(".glb"))
            for (part in model.parts) {
                assertEquals(part.positions.size, part.normals.size)
                for (i in 0 until part.normals.size / 3) {
                    val n = sqrt(part.normals[i * 3] * part.normals[i * 3] + part.normals[i * 3 + 1] * part.normals[i * 3 + 1] + part.normals[i * 3 + 2] * part.normals[i * 3 + 2])
                    assertEquals("$name normal", 1f, n, 1e-3f)
                }
            }
        }
    }

    @Test
    fun rejectsNonGlb() {
        assertThrows(IllegalArgumentException::class.java) { parseGlb("bukan model".toByteArray()) }
    }
}
