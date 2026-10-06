package com.zinmedia.effects

import com.zinmedia.effects.gl.parseGlb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
        assertEquals(8, names.size)
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

    @Test
    fun firstAnimation_autoplaysLoopsAndInterpolatesNodeTransforms() {
        val model = parseGlb(animatedTriangleGlb())
        assertTrue(model.hasAnimation)
        // Titik asal mesh sudah dipanggang pada bind pose x=1; pada tengah clip hasil akhirnya x=2.
        val middle = model.partTransformsAt(1f).single()
        val bakedOriginX = model.parts.single().positions[0]
        val finalX = middle[0] * bakedOriginX + middle[12]
        val finalY = middle[1] * bakedOriginX + middle[13]
        assertEquals(2f, finalX, 1e-4f)
        assertEquals(0f, finalY, 1e-4f)
        // Rotation 90° terhadap Z dan scale 2 pada tengah clip.
        assertEquals(0f, middle[0], 1e-4f)
        assertEquals(2f, middle[1], 1e-4f)
        assertEquals(-2f, middle[4], 1e-4f)
        assertEquals(0f, middle[5], 1e-4f)
        // Durasi dua detik: waktu dua detik kembali ke awal clip.
        assertEquals(0f, model.partTransformsAt(2f).single()[12], 1e-4f)
    }

    @Test
    fun skeletalAnimation_readsSkinWeightsAndMovesJointPalette() {
        val model = parseGlb(skinnedTriangleGlb())
        val part = model.parts.single()
        assertTrue(model.hasAnimation)
        assertEquals(12, part.joints!!.size)
        assertEquals(12, part.weights!!.size)
        assertEquals(1f, part.weights[0], 1e-6f)
        val pose = model.poseAt(1f)
        assertEquals(1f, pose.jointMatrices.single()!![12], 1e-4f)
        assertEquals(0f, pose.partTransforms.single()[12], 1e-4f)
        // Dua detik kembali ke bind pose karena clip diputar berulang.
        assertEquals(0f, model.poseAt(2f).jointMatrices.single()!![12], 1e-4f)
    }

    /** GLB minimal: satu segitiga pada node x=1, dianimasikan ke x=3 selama dua detik. */
    private fun animatedTriangleGlb(): ByteArray {
        val bin = ByteBuffer.allocate(168).order(ByteOrder.LITTLE_ENDIAN)
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f).forEach(bin::putFloat)
        repeat(3) { bin.putFloat(0f); bin.putFloat(0f); bin.putFloat(1f) }
        bin.putShort(0); bin.putShort(1); bin.putShort(2)
        bin.position(80)
        bin.putFloat(0f); bin.putFloat(2f)
        floatArrayOf(1f, 0f, 0f, 3f, 0f, 0f).forEach(bin::putFloat)
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f).forEach(bin::putFloat)
        floatArrayOf(1f, 1f, 1f, 3f, 3f, 3f).forEach(bin::putFloat)
        val json = """{
          "asset":{"version":"2.0"},
          "buffers":[{"byteLength":168}],
          "bufferViews":[
            {"buffer":0,"byteOffset":0,"byteLength":36},
            {"buffer":0,"byteOffset":36,"byteLength":36},
            {"buffer":0,"byteOffset":72,"byteLength":6},
            {"buffer":0,"byteOffset":80,"byteLength":8},
            {"buffer":0,"byteOffset":88,"byteLength":24},
            {"buffer":0,"byteOffset":112,"byteLength":32},
            {"buffer":0,"byteOffset":144,"byteLength":24}
          ],
          "accessors":[
            {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
            {"bufferView":1,"componentType":5126,"count":3,"type":"VEC3"},
            {"bufferView":2,"componentType":5123,"count":3,"type":"SCALAR"},
            {"bufferView":3,"componentType":5126,"count":2,"type":"SCALAR"},
            {"bufferView":4,"componentType":5126,"count":2,"type":"VEC3"},
            {"bufferView":5,"componentType":5126,"count":2,"type":"VEC4"},
            {"bufferView":6,"componentType":5126,"count":2,"type":"VEC3"}
          ],
          "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2}]}],
          "nodes":[{"mesh":0,"translation":[1,0,0]}],
          "scenes":[{"nodes":[0]}],"scene":0,
          "animations":[{
            "samplers":[
              {"input":3,"output":4,"interpolation":"LINEAR"},
              {"input":3,"output":5,"interpolation":"LINEAR"},
              {"input":3,"output":6,"interpolation":"LINEAR"}
            ],
            "channels":[
              {"sampler":0,"target":{"node":0,"path":"translation"}},
              {"sampler":1,"target":{"node":0,"path":"rotation"}},
              {"sampler":2,"target":{"node":0,"path":"scale"}}
            ]
          }]
        }""".trimIndent().toByteArray()
        val jsonSize = (json.size + 3) and -4
        val total = 12 + 8 + jsonSize + 8 + bin.capacity()
        return ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546C67); putInt(2); putInt(total)
            putInt(jsonSize); putInt(0x4E4F534A); put(json)
            repeat(jsonSize - json.size) { put(0x20) }
            putInt(bin.capacity()); putInt(0x004E4942); put(bin.array())
        }.array()
    }

    /** GLB minimal berskin: tiga titik dipengaruhi satu tulang yang bergerak x=0 ke x=2. */
    private fun skinnedTriangleGlb(): ByteArray {
        val bin = ByteBuffer.allocate(236).order(ByteOrder.LITTLE_ENDIAN)
        floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f).forEach(bin::putFloat)
        repeat(3) { bin.putFloat(0f); bin.putFloat(0f); bin.putFloat(1f) }
        bin.putShort(0); bin.putShort(1); bin.putShort(2)
        bin.position(80)
        repeat(12) { bin.put(0) }
        repeat(3) { bin.putFloat(1f); bin.putFloat(0f); bin.putFloat(0f); bin.putFloat(0f) }
        floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f).forEach(bin::putFloat)
        bin.putFloat(0f); bin.putFloat(2f)
        floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f).forEach(bin::putFloat)
        val json = """{
          "asset":{"version":"2.0"},
          "buffers":[{"byteLength":236}],
          "bufferViews":[
            {"buffer":0,"byteOffset":0,"byteLength":36},
            {"buffer":0,"byteOffset":36,"byteLength":36},
            {"buffer":0,"byteOffset":72,"byteLength":6},
            {"buffer":0,"byteOffset":80,"byteLength":12},
            {"buffer":0,"byteOffset":92,"byteLength":48},
            {"buffer":0,"byteOffset":140,"byteLength":64},
            {"buffer":0,"byteOffset":204,"byteLength":8},
            {"buffer":0,"byteOffset":212,"byteLength":24}
          ],
          "accessors":[
            {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
            {"bufferView":1,"componentType":5126,"count":3,"type":"VEC3"},
            {"bufferView":2,"componentType":5123,"count":3,"type":"SCALAR"},
            {"bufferView":3,"componentType":5121,"count":3,"type":"VEC4"},
            {"bufferView":4,"componentType":5126,"count":3,"type":"VEC4"},
            {"bufferView":5,"componentType":5126,"count":1,"type":"MAT4"},
            {"bufferView":6,"componentType":5126,"count":2,"type":"SCALAR"},
            {"bufferView":7,"componentType":5126,"count":2,"type":"VEC3"}
          ],
          "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"JOINTS_0":3,"WEIGHTS_0":4},"indices":2}]}],
          "nodes":[{"mesh":0,"skin":0},{"name":"Bone"}],
          "skins":[{"joints":[1],"inverseBindMatrices":5}],
          "scenes":[{"nodes":[0,1]}],"scene":0,
          "animations":[{
            "samplers":[{"input":6,"output":7,"interpolation":"LINEAR"}],
            "channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]
          }]
        }""".trimIndent().toByteArray()
        val jsonSize = (json.size + 3) and -4
        val total = 12 + 8 + jsonSize + 8 + bin.capacity()
        return ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546C67); putInt(2); putInt(total)
            putInt(jsonSize); putInt(0x4E4F534A); put(json)
            repeat(jsonSize - json.size) { put(0x20) }
            putInt(bin.capacity()); putInt(0x004E4942); put(bin.array())
        }.array()
    }
}
