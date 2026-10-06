package com.zinmedia.effects

import com.zinmedia.effects.face.FaceShaper
import com.zinmedia.effects.face.parseBeautyMesh
import com.zinmedia.effects.gl.parseFaceMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class FaceShaperTest {

    private val head = parseFaceMesh(File("src/main/res/raw/zm_face_mesh.bin").readBytes()).positions
    private val mesh = parseBeautyMesh(File("src/main/res/raw/zm_beauty_mesh.bin").readBytes())
    private val size = 1000f

    /** Kepala standar dari depan (miring [roll]), titik ternormalisasi 478 (iris = titik 0). */
    private fun face(roll: Double = 0.0): FloatArray {
        val c = cos(roll).toFloat()
        val s = sin(roll).toFloat()
        return FloatArray(FaceShaper.POINTS * 3) { i ->
            val p = (i / 3).takeIf { it < 468 } ?: 0
            val x = head[p * 3]
            val y = -head[p * 3 + 1]
            when (i % 3) {
                0 -> 0.5f + (x * c - y * s) / 50f
                1 -> 0.5f + (x * s + y * c) / 50f
                else -> 0f
            }
        }
    }

    private fun run(beauty: BeautyParams, roll: Double = 0.0): Pair<FloatArray, FloatArray> {
        val source = FloatArray(mesh.vertexCount * 2)
        val target = FloatArray(mesh.vertexCount * 2)
        FaceShaper.shape(face(roll), size, size, beauty, mesh.oval, source, target)
        return source to target
    }

    private fun distance(p: FloatArray, a: Int, b: Int) = hypot(p[a * 2] - p[b * 2], p[a * 2 + 1] - p[b * 2 + 1]) * size

    @Test
    fun noBeauty_noMovement_andRingOutsideFace() {
        val (source, target) = run(BeautyParams.None)
        assertTrue(source.contentEquals(target))
        // Cincin luar lebih jauh dari pusat daripada oval wajah.
        assertTrue(distance(source, 478 + 8, 478 + 28) > distance(source, 454, 234))
    }

    @Test
    fun slimFace_narrowsJaw_notEyes() {
        val (source, target) = run(BeautyParams.of(BeautyFeature.SlimFace to 1f))
        assertTrue("rahang menyempit", distance(target, 172, 397) < distance(source, 172, 397) - 5f)
        assertEquals(distance(source, 159, 386), distance(target, 159, 386), 3f)
    }

    @Test
    fun enlargeEyes_widensEyes_andRingNeverMoves() {
        val (source, target) = run(BeautyParams.of(BeautyFeature.EnlargeEyes to 1f, BeautyFeature.SmallFace to 1f))
        assertTrue(distance(target, 159, 145) > distance(source, 159, 145))
        for (i in 478 until mesh.vertexCount) {
            assertEquals(source[i * 2], target[i * 2], 0f)
            assertEquals(source[i * 2 + 1], target[i * 2 + 1], 0f)
        }
    }

    @Test
    fun chin_isBipolar() {
        val (source, longer) = run(BeautyParams.of(BeautyFeature.Chin to 1f))
        val (_, shorter) = run(BeautyParams.of(BeautyFeature.Chin to -1f))
        assertTrue(distance(longer, 10, 152) > distance(source, 10, 152))
        assertTrue(distance(shorter, 10, 152) < distance(source, 10, 152))
    }

    @Test
    fun tiltedHead_sameEffectStrength() {
        val beauty = BeautyParams.of(BeautyFeature.SlimFace to 1f, BeautyFeature.EnlargeEyes to 0.5f)
        val (s0, t0) = run(beauty)
        val (s1, t1) = run(beauty, Math.toRadians(30.0))
        assertEquals(distance(s0, 172, 397) - distance(t0, 172, 397), distance(s1, 172, 397) - distance(t1, 172, 397), 0.5f)
        assertEquals(distance(t0, 159, 145), distance(t1, 159, 145), 0.5f)
    }

    @Test
    fun shiftIsLimited() {
        val everything = BeautyFeature.entries.fold(BeautyParams.None) { acc, f -> acc.with(f, f.range.endInclusive) }
        val (source, target) = run(everything)
        val halfWidth = distance(source, 454, 234) / 2f
        for (i in 0 until 478) {
            val moved = hypot(target[i * 2] - source[i * 2], target[i * 2 + 1] - source[i * 2 + 1]) * size
            assertTrue("titik $i bergeser $moved", moved <= halfWidth * 0.25f + 0.5f)
        }
    }

    @Test
    fun beautyMesh_softWeights_andSolidRegions() {
        assertEquals(12, mesh.channels)
        // Kulit (kanal 0) nol di bibir atas tengah (titik 0), penuh di pipi (titik 50).
        assertEquals(0, mesh.weights[0 * 12 + 0].toInt() and 0xFF)
        assertEquals(255, mesh.weights[50 * 12 + 0].toInt() and 0xFF)
        // Bibir, mulut, mata = segitiga terpisah; bibir tidak berbagi segitiga dengan mulut.
        val (lips, mouth, eyes) = mesh.regions
        assertTrue(lips.size > mouth.size && mouth.isNotEmpty() && eyes.isNotEmpty())
        val lipTris = lips.toList().chunked(3).map { it.toSet() }.toSet()
        assertTrue(mouth.toList().chunked(3).none { it.toSet() in lipTris })
    }
}
