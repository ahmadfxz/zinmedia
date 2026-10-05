package com.zinmedia.effects

import com.zinmedia.effects.face.HeadFit
import com.zinmedia.effects.gl.parseFaceMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

class HeadFitTest {

    private val head = parseFaceMesh(File("src/main/res/raw/zm_face_mesh.bin").readBytes()).positions

    /** Kepala standar diputar (menoleh [yaw], mengangguk [pitch]), diskalakan, digeser. */
    private fun transformed(yaw: Double, pitch: Double, scale: Float, tx: Float, ty: Float, tz: Float): Pair<FloatArray, FloatArray> {
        val cy = cos(yaw).toFloat(); val sy = sin(yaw).toFloat()
        val cp = cos(pitch).toFloat(); val sp = sin(pitch).toFloat()
        // R = Ry · Rx (kolom-mayor 3×3 dalam matriks 4×4).
        val r = floatArrayOf(
            cy, 0f, -sy,
            sy * sp, cp, cy * sp,
            sy * cp, -sp, cy * cp,
        )
        val expected = FloatArray(16)
        for (col in 0 until 3) for (row in 0 until 3) expected[col * 4 + row] = r[col * 3 + row] * scale
        expected[12] = tx; expected[13] = ty; expected[14] = tz; expected[15] = 1f
        val out = FloatArray(head.size)
        for (i in 0 until head.size / 3) {
            val x = head[i * 3]; val y = head[i * 3 + 1]; val z = head[i * 3 + 2]
            for (row in 0 until 3) {
                out[i * 3 + row] = expected[row] * x + expected[4 + row] * y + expected[8 + row] * z + expected[12 + row]
            }
        }
        return out to expected
    }

    @Test
    fun recoversTurnedAndScaledHead() {
        val (target, expected) = transformed(yaw = 0.6, pitch = -0.25, scale = 14f, tx = 300f, ty = -500f, tz = 20f)
        val m = FloatArray(16)
        assertTrue(HeadFit.solve(head, target, m))
        for (i in 0 until 16) assertEquals("m[$i]", expected[i], m[i], 1e-2f * maxOf(1f, kotlin.math.abs(expected[i])))
    }

    @Test
    fun ignoresMouthMovement() {
        val (target, expected) = transformed(yaw = 0.0, pitch = 0.0, scale = 10f, tx = 0f, ty = 0f, tz = 0f)
        // Mulut terbuka lebar: titik bibir bawah & dagu turun jauh.
        for (i in intArrayOf(14, 17, 152, 18, 200, 199)) target[i * 3 + 1] -= 40f
        val m = FloatArray(16)
        assertTrue(HeadFit.solve(head, target, m))
        assertEquals(expected[13], m[13], 1e-3f)
        assertEquals(10f, m[5], 1e-3f)
    }

    @Test
    fun degeneratePoints_fail() {
        assertFalse(HeadFit.solve(FloatArray(head.size), FloatArray(head.size), FloatArray(16)))
    }
}
