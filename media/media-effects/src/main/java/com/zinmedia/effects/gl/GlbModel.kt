package com.zinmedia.effects.gl

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Satu bagian model dengan satu bahan: segitiga berindeks, transformasi node sudah diterapkan. */
internal class GlbPart(
    /** x, y, z per titik (ruang kepala standar, cm). */
    val positions: FloatArray,
    /** Normal satuan per titik. */
    val normals: FloatArray,
    val indices: ShortArray,
    /** u, v per titik (v ke bawah), atau `null` bila tanpa tekstur. */
    val texCoords: FloatArray?,
    /** Gambar tekstur warna (PNG/JPEG) dari berkas, atau `null`. */
    val textureBytes: ByteArray?,
    /** Warna dasar RGBA (0..1), dikalikan tekstur. */
    val color: FloatArray,
    val metallic: Float,
    val roughness: Float,
    /** Tembus pandang (alphaMode BLEND atau alfa < 1): digambar setelah bagian padat. */
    val blend: Boolean,
    /** alphaMode MASK: piksel beralfa < 0,5 tidak digambar. */
    val cutout: Boolean,
)

internal class GlbModel(val parts: List<GlbPart>)

/**
 * Baca glTF biner (.glb): mesh segitiga (POSITION, NORMAL atau dihitung, TEXCOORD_0), bahan
 * (baseColorFactor, baseColorTexture tertanam, metallic, roughness, alphaMode), dan hierarki node
 * (matrix/TRS). Tekstur lain (normal, ORM), kompresi (Draco/meshopt), skin, dan animasi diabaikan
 * / tidak didukung.
 */
internal fun parseGlb(bytes: ByteArray): GlbModel {
    val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(bytes.size >= 20 && data.getInt(0) == GLB_MAGIC) { "Bukan berkas .glb" }
    require(data.getInt(4) == 2) { "Hanya glTF 2.0 yang didukung" }
    var offset = 12
    var json: JSONObject? = null
    var bin: ByteBuffer? = null
    while (offset + 8 <= bytes.size) {
        val length = data.getInt(offset)
        val type = data.getInt(offset + 4)
        val start = offset + 8
        require(length >= 0 && start + length <= bytes.size) { "Berkas .glb terpotong" }
        when (type) {
            CHUNK_JSON -> json = JSONObject(String(bytes, start, length, Charsets.UTF_8))
            CHUNK_BIN -> bin = ByteBuffer.wrap(bytes, start, length).slice().order(ByteOrder.LITTLE_ENDIAN)
        }
        offset = start + length
    }
    val gltf = requireNotNull(json) { "Berkas .glb tanpa JSON" }
    gltf.optJSONArray("extensionsRequired")?.let { require(it.length() == 0) { "Ekstensi glTF belum didukung: $it" } }
    return GltfReader(gltf, bin).read()
}

private class GltfReader(private val gltf: JSONObject, private val bin: ByteBuffer?) {
    private val accessors = gltf.optJSONArray("accessors") ?: JSONArray()
    private val views = gltf.optJSONArray("bufferViews") ?: JSONArray()
    private val nodes = gltf.optJSONArray("nodes") ?: JSONArray()
    private val meshes = gltf.optJSONArray("meshes") ?: JSONArray()
    private val materials = gltf.optJSONArray("materials") ?: JSONArray()
    private val out = mutableListOf<GlbPart>()

    fun read(): GlbModel {
        val scenes = gltf.optJSONArray("scenes")
        val roots = if (scenes != null && scenes.length() > 0) {
            scenes.getJSONObject(gltf.optInt("scene", 0).coerceIn(0, scenes.length() - 1)).optJSONArray("nodes").ints()
        } else {
            val children = (0 until nodes.length()).flatMap { nodes.getJSONObject(it).optJSONArray("children").ints() }.toSet()
            (0 until nodes.length()).filter { it !in children }
        }
        roots.forEach { visit(it, IdentityMatrix4x4, 0) }
        require(out.isNotEmpty()) { "Model tidak berisi mesh segitiga" }
        return GlbModel(out)
    }

    private fun visit(index: Int, parent: FloatArray, depth: Int) {
        require(depth < 64) { "Hierarki node terlalu dalam" }
        val node = nodes.getJSONObject(index)
        val world = multiply4(parent, localMatrix(node))
        if (node.has("mesh")) {
            val primitives = meshes.getJSONObject(node.getInt("mesh")).getJSONArray("primitives")
            for (i in 0 until primitives.length()) readPrimitive(primitives.getJSONObject(i), world)
        }
        node.optJSONArray("children").ints().forEach { visit(it, world, depth + 1) }
    }

    private fun readPrimitive(primitive: JSONObject, world: FloatArray) {
        if (primitive.optInt("mode", TRIANGLES) != TRIANGLES) return
        val attributes = primitive.getJSONObject("attributes")
        val raw = readFloats(attributes.getInt("POSITION"), 3)
        val count = raw.size / 3
        require(count <= 65_535) { "Model terlalu besar (maks. 65.535 titik per bagian)" }
        val indices = if (primitive.has("indices")) readInts(primitive.getInt("indices")) else IntArray(count) { it }
        require(indices.all { it in 0 until count }) { "Indeks model di luar jangkauan" }
        val rawNormals = if (attributes.has("NORMAL")) readFloats(attributes.getInt("NORMAL"), 3) else computeNormals(raw, indices)
        val positions = FloatArray(raw.size)
        val normals = FloatArray(raw.size)
        for (i in 0 until count) {
            transformPoint(world, raw, i * 3, positions)
            transformDirection(world, rawNormals, i * 3, normals)
        }
        val material = if (primitive.has("material")) materials.getJSONObject(primitive.getInt("material")) else JSONObject()
        val pbr = material.optJSONObject("pbrMetallicRoughness") ?: JSONObject()
        val color = pbr.optJSONArray("baseColorFactor")?.let { a -> FloatArray(4) { a.getDouble(it).toFloat() } }
            ?: floatArrayOf(1f, 1f, 1f, 1f)
        val texture = pbr.optJSONObject("baseColorTexture")?.let { readTextureImage(it.getInt("index")) }
        val texCoords = if (texture != null && attributes.has("TEXCOORD_0")) readFloats(attributes.getInt("TEXCOORD_0"), 2) else null
        val alphaMode = material.optString("alphaMode", "OPAQUE")
        out += GlbPart(
            positions = positions,
            normals = normals,
            indices = ShortArray(indices.size) { indices[it].toShort() },
            texCoords = texCoords,
            textureBytes = texture.takeIf { texCoords != null },
            color = color,
            metallic = pbr.optDouble("metallicFactor", 1.0).toFloat(),
            roughness = pbr.optDouble("roughnessFactor", 1.0).toFloat(),
            blend = alphaMode == "BLEND" || color[3] < 0.999f,
            cutout = alphaMode == "MASK",
        )
    }

    /** Gambar tekstur tertanam (bufferView) untuk tekstur ke-[textureIndex], atau `null`. */
    private fun readTextureImage(textureIndex: Int): ByteArray? {
        val texture = gltf.optJSONArray("textures")?.optJSONObject(textureIndex) ?: return null
        val image = gltf.optJSONArray("images")?.optJSONObject(texture.optInt("source", -1)) ?: return null
        if (!image.has("bufferView")) return null
        val (buffer, start, length) = viewRange(image.getInt("bufferView"))
        return ByteArray(length).also { bytes -> for (i in 0 until length) bytes[i] = buffer.get(start + i) }
    }

    /** (buffer biner, awal, panjang) dari bufferView. */
    private fun viewRange(index: Int): Triple<ByteBuffer, Int, Int> {
        val view = views.getJSONObject(index)
        require(view.optInt("buffer", 0) == 0) { "Buffer eksternal belum didukung" }
        val buffer = requireNotNull(bin) { "Berkas .glb tanpa data biner" }
        val start = view.optInt("byteOffset", 0)
        val length = view.getInt("byteLength")
        require(start >= 0 && start + length <= buffer.limit()) { "bufferView di luar data" }
        return Triple(buffer, start, length)
    }

    private fun readFloats(accessorIndex: Int, components: Int): FloatArray {
        val accessor = accessors.getJSONObject(accessorIndex)
        require(!accessor.has("sparse")) { "Accessor sparse belum didukung" }
        val count = accessor.getInt("count")
        val result = FloatArray(count * components)
        if (!accessor.has("bufferView")) return result
        val type = accessor.getInt("componentType")
        val size = componentSize(type)
        val normalized = accessor.optBoolean("normalized", false)
        val (buffer, viewStart, viewLength) = viewRange(accessor.getInt("bufferView"))
        val stride = views.getJSONObject(accessor.getInt("bufferView")).optInt("byteStride", 0).takeIf { it > 0 } ?: (size * components)
        val base = viewStart + accessor.optInt("byteOffset", 0)
        require(count == 0 || base + (count - 1) * stride + size * components <= viewStart + viewLength) { "Accessor di luar data" }
        for (i in 0 until count) for (c in 0 until components) {
            val at = base + i * stride + c * size
            result[i * components + c] = when (type) {
                FLOAT -> buffer.getFloat(at)
                UNSIGNED_BYTE -> (buffer.get(at).toInt() and 0xFF).let { if (normalized) it / 255f else it.toFloat() }
                UNSIGNED_SHORT -> (buffer.getShort(at).toInt() and 0xFFFF).let { if (normalized) it / 65535f else it.toFloat() }
                BYTE -> buffer.get(at).toFloat().let { if (normalized) (it / 127f).coerceAtLeast(-1f) else it }
                SHORT -> buffer.getShort(at).toFloat().let { if (normalized) (it / 32767f).coerceAtLeast(-1f) else it }
                else -> error("Jenis komponen $type tidak didukung")
            }
        }
        return result
    }

    private fun readInts(accessorIndex: Int): IntArray {
        val accessor = accessors.getJSONObject(accessorIndex)
        val count = accessor.getInt("count")
        val type = accessor.getInt("componentType")
        val size = componentSize(type)
        val (buffer, viewStart, viewLength) = viewRange(accessor.getInt("bufferView"))
        val base = viewStart + accessor.optInt("byteOffset", 0)
        require(base + count * size <= viewStart + viewLength) { "Indeks di luar data" }
        return IntArray(count) { i ->
            val at = base + i * size
            when (type) {
                UNSIGNED_BYTE -> buffer.get(at).toInt() and 0xFF
                UNSIGNED_SHORT -> buffer.getShort(at).toInt() and 0xFFFF
                UNSIGNED_INT -> buffer.getInt(at)
                else -> error("Jenis indeks $type tidak didukung")
            }
        }
    }
}

/** Matriks lokal node (kolom-mayor): `matrix`, atau translasi × rotasi × skala. */
private fun localMatrix(node: JSONObject): FloatArray {
    node.optJSONArray("matrix")?.let { m -> return FloatArray(16) { m.getDouble(it).toFloat() } }
    fun vec(name: String, default: Float, size: Int) =
        node.optJSONArray(name)?.let { a -> FloatArray(size) { a.getDouble(it).toFloat() } } ?: FloatArray(size) { default }
    val t = vec("translation", 0f, 3)
    val s = vec("scale", 1f, 3)
    val r = node.optJSONArray("rotation")?.let { a -> FloatArray(4) { a.getDouble(it).toFloat() } } ?: floatArrayOf(0f, 0f, 0f, 1f)
    val (x, y, z) = Triple(r[0], r[1], r[2])
    val w = r[3]
    return floatArrayOf(
        (1 - 2 * (y * y + z * z)) * s[0], (2 * (x * y + z * w)) * s[0], (2 * (x * z - y * w)) * s[0], 0f,
        (2 * (x * y - z * w)) * s[1], (1 - 2 * (x * x + z * z)) * s[1], (2 * (y * z + x * w)) * s[1], 0f,
        (2 * (x * z + y * w)) * s[2], (2 * (y * z - x * w)) * s[2], (1 - 2 * (x * x + y * y)) * s[2], 0f,
        t[0], t[1], t[2], 1f,
    )
}

/** a × b, keduanya 4×4 kolom-mayor. */
internal fun multiply4(a: FloatArray, b: FloatArray): FloatArray = FloatArray(16) { i ->
    val col = i / 4
    val row = i % 4
    var sum = 0f
    for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
    sum
}

private fun transformPoint(m: FloatArray, src: FloatArray, at: Int, dst: FloatArray) {
    val x = src[at]
    val y = src[at + 1]
    val z = src[at + 2]
    for (row in 0 until 3) dst[at + row] = m[row] * x + m[4 + row] * y + m[8 + row] * z + m[12 + row]
}

/** Arah (normal) lewat bagian 3×3 matriks, lalu dinormalkan; tepat untuk skala seragam. */
private fun transformDirection(m: FloatArray, src: FloatArray, at: Int, dst: FloatArray) {
    val x = src[at]
    val y = src[at + 1]
    val z = src[at + 2]
    val nx = m[0] * x + m[4] * y + m[8] * z
    val ny = m[1] * x + m[5] * y + m[9] * z
    val nz = m[2] * x + m[6] * y + m[10] * z
    val n = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 1e-6f } ?: 1f
    dst[at] = nx / n
    dst[at + 1] = ny / n
    dst[at + 2] = nz / n
}

/** Normal halus per titik dari segitiga (untuk model tanpa NORMAL). */
private fun computeNormals(positions: FloatArray, indices: IntArray): FloatArray {
    val normals = FloatArray(positions.size)
    for (t in 0 until indices.size / 3) {
        val a = indices[t * 3] * 3
        val b = indices[t * 3 + 1] * 3
        val c = indices[t * 3 + 2] * 3
        val ux = positions[b] - positions[a]
        val uy = positions[b + 1] - positions[a + 1]
        val uz = positions[b + 2] - positions[a + 2]
        val vx = positions[c] - positions[a]
        val vy = positions[c + 1] - positions[a + 1]
        val vz = positions[c + 2] - positions[a + 2]
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        for (v in intArrayOf(a, b, c)) {
            normals[v] += nx
            normals[v + 1] += ny
            normals[v + 2] += nz
        }
    }
    for (at in 0 until normals.size step 3) {
        val n = sqrt(normals[at] * normals[at] + normals[at + 1] * normals[at + 1] + normals[at + 2] * normals[at + 2])
        if (n > 1e-9f) {
            normals[at] /= n
            normals[at + 1] /= n
            normals[at + 2] /= n
        } else {
            normals[at + 2] = 1f
        }
    }
    return normals
}

private fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { getInt(it) }

private fun componentSize(type: Int) = when (type) {
    BYTE, UNSIGNED_BYTE -> 1
    SHORT, UNSIGNED_SHORT -> 2
    UNSIGNED_INT, FLOAT -> 4
    else -> error("Jenis komponen $type tidak didukung")
}

private val IdentityMatrix4x4 = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

private const val GLB_MAGIC = 0x46546C67
private const val CHUNK_JSON = 0x4E4F534A
private const val CHUNK_BIN = 0x004E4942
private const val TRIANGLES = 4
private const val BYTE = 5120
private const val UNSIGNED_BYTE = 5121
private const val SHORT = 5122
private const val UNSIGNED_SHORT = 5123
private const val UNSIGNED_INT = 5125
private const val FLOAT = 5126
