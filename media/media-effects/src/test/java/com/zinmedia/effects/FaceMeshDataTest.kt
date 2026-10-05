package com.zinmedia.effects

import com.zinmedia.effects.gl.parseFaceMesh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FaceMeshDataTest {

    @Test
    fun bundledMesh_hasAllLandmarksAndTriangles() {
        val mesh = parseFaceMesh(File("src/main/res/raw/zm_face_mesh.bin").readBytes())
        assertEquals(468 * 2, mesh.uv.size)
        assertEquals(898 * 3, mesh.indices.size)
        assertTrue(mesh.indices.all { it in 0 until 468 })
        assertTrue(mesh.uv.all { it in 0f..1f })
        // Mata kanan orangnya (33) di kiri peta, kiri orangnya (263) di kanan, setinggi sama.
        assertTrue(mesh.uv[33 * 2] < 0.5f && mesh.uv[263 * 2] > 0.5f)
        assertEquals(mesh.uv[33 * 2 + 1], mesh.uv[263 * 2 + 1], 1e-4f)
    }

    @Test
    fun rejectsTruncatedData() {
        assertThrows(IllegalArgumentException::class.java) { parseFaceMesh(ByteArray(12)) }
    }
}
