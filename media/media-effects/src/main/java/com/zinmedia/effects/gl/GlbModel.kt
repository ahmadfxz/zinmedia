package com.zinmedia.effects.gl

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.acos
import kotlin.math.sin
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
    /** Node pemilik mesh; transformasi animasinya diterapkan saat menggambar. */
    val nodeIndex: Int,
    /** Indeks palette tulang (4 per titik), atau `null` untuk mesh biasa. */
    val joints: FloatArray? = null,
    /** Bobot tulang ternormalisasi (4 per titik), atau `null` untuk mesh biasa. */
    val weights: FloatArray? = null,
    /** Skin glTF pemilik primitive dan pemetaan palette lokal ke joint skin. */
    val skinIndex: Int = -1,
    val jointPalette: IntArray? = null,
)

internal class GlbSkin(val joints: IntArray, val inverseBindMatrices: Array<FloatArray>)

internal class GlbPose(
    val partTransforms: List<FloatArray>,
    /** Matriks joint kolom-mayor yang sudah diratakan per bagian; `null` untuk mesh biasa. */
    val jointMatrices: List<FloatArray?>,
)

internal class GlbNode(
    val parent: Int,
    val translation: FloatArray,
    val rotation: FloatArray,
    val scale: FloatArray,
    val matrix: FloatArray?,
    val inverseBindWorld: FloatArray,
)

internal enum class AnimationPath { TRANSLATION, ROTATION, SCALE }

internal class AnimationChannel(
    val node: Int,
    val path: AnimationPath,
    val times: FloatArray,
    val values: FloatArray,
    val step: Boolean,
)

internal class GlbAnimation(val duration: Float, val channels: List<AnimationChannel>)

internal class GlbModel(
    val parts: List<GlbPart>,
    private val nodes: List<GlbNode>,
    private val skins: List<GlbSkin>,
    private val animation: GlbAnimation?,
) {
    val hasAnimation: Boolean get() = animation != null

    /**
     * Transformasi relatif dari pose awal untuk tiap bagian pada [seconds]. Clip pertama diputar
     * berulang. Posisi bagian sudah dipanggang pada pose awal agar model statis tetap identik.
     */
    fun poseAt(seconds: Float): GlbPose {
        val clip = animation
        val t = if (clip != null && clip.duration > 0f) ((seconds % clip.duration) + clip.duration) % clip.duration else 0f
        val translations = Array(nodes.size) { nodes[it].translation.copyOf() }
        val rotations = Array(nodes.size) { nodes[it].rotation.copyOf() }
        val scales = Array(nodes.size) { nodes[it].scale.copyOf() }
        val animated = BooleanArray(nodes.size)
        for (channel in clip?.channels.orEmpty()) {
            val target = when (channel.path) {
                AnimationPath.TRANSLATION -> translations[channel.node]
                AnimationPath.ROTATION -> rotations[channel.node]
                AnimationPath.SCALE -> scales[channel.node]
            }
            sample(channel, t, target)
            animated[channel.node] = true
        }
        val worlds = arrayOfNulls<FloatArray>(nodes.size)
        fun world(index: Int): FloatArray {
            worlds[index]?.let { return it }
            val node = nodes[index]
            val local = if (node.matrix != null && !animated[index]) node.matrix
                else trsMatrix(translations[index], rotations[index], scales[index])
            return (if (node.parent >= 0) multiply4(world(node.parent), local) else local).also { worlds[index] = it }
        }
        val transforms = ArrayList<FloatArray>(parts.size)
        val palettes = ArrayList<FloatArray?>(parts.size)
        for (part in parts) {
            val palette = part.jointPalette
            if (part.skinIndex >= 0 && palette != null) {
                transforms += IdentityMatrix4x4
                val skin = skins[part.skinIndex]
                palettes += FloatArray(palette.size * 16).also { result ->
                    palette.forEachIndexed { paletteIndex, skinJointIndex ->
                        multiply4(
                            world(skin.joints[skinJointIndex]),
                            skin.inverseBindMatrices[skinJointIndex],
                        ).copyInto(result, paletteIndex * 16)
                    }
                }
            } else {
                transforms += multiply4(world(part.nodeIndex), nodes[part.nodeIndex].inverseBindWorld)
                palettes += null
            }
        }
        return GlbPose(transforms, palettes)
    }

    fun partTransformsAt(seconds: Float): List<FloatArray> = poseAt(seconds).partTransforms
}

/**
 * Baca glTF biner (.glb): mesh segitiga (POSITION, NORMAL atau dihitung, TEXCOORD_0), bahan
 * (baseColorFactor, baseColorTexture tertanam, metallic, roughness, alphaMode), dan hierarki node
 * (matrix/TRS), serta clip animasi pertama (translation/rotation/scale; LINEAR atau STEP) yang
 * diputar otomatis. Skin `JOINTS_0`/`WEIGHTS_0` dan inverse bind matrices didukung sampai 24 joint
 * aktif per primitive. Tekstur lain (normal, ORM), kompresi (Draco/meshopt), morph target, dan
 * interpolasi CUBICSPLINE belum didukung.
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
    private val skinsJson = gltf.optJSONArray("skins") ?: JSONArray()
    private val out = mutableListOf<GlbPart>()
    private lateinit var parsedSkins: List<GlbSkin>
    private lateinit var parents: IntArray
    private lateinit var bindWorlds: Array<FloatArray>

    fun read(): GlbModel {
        val scenes = gltf.optJSONArray("scenes")
        val roots = if (scenes != null && scenes.length() > 0) {
            scenes.getJSONObject(gltf.optInt("scene", 0).coerceIn(0, scenes.length() - 1)).optJSONArray("nodes").ints()
        } else {
            val children = (0 until nodes.length()).flatMap { nodes.getJSONObject(it).optJSONArray("children").ints() }.toSet()
            (0 until nodes.length()).filter { it !in children }
        }
        parents = IntArray(nodes.length()) { -1 }
        for (i in 0 until nodes.length()) {
            nodes.getJSONObject(i).optJSONArray("children").ints().forEach { child -> parents[child] = i }
        }
        bindWorlds = Array(nodes.length()) { IdentityMatrix4x4 }
        parsedSkins = readSkins()
        roots.forEach { visit(it, IdentityMatrix4x4, 0) }
        require(out.isNotEmpty()) { "Model tidak berisi mesh segitiga" }
        val modelNodes = List(nodes.length()) { i ->
            val node = nodes.getJSONObject(i)
            GlbNode(
                parent = parents[i],
                translation = vector(node, "translation", floatArrayOf(0f, 0f, 0f)),
                rotation = vector(node, "rotation", floatArrayOf(0f, 0f, 0f, 1f)),
                scale = vector(node, "scale", floatArrayOf(1f, 1f, 1f)),
                matrix = node.optJSONArray("matrix")?.let { m -> FloatArray(16) { m.getDouble(it).toFloat() } },
                inverseBindWorld = invert4(bindWorlds[i]),
            )
        }
        return GlbModel(out, modelNodes, parsedSkins, readAnimation(modelNodes))
    }

    private fun visit(index: Int, parent: FloatArray, depth: Int) {
        require(depth < 64) { "Hierarki node terlalu dalam" }
        val node = nodes.getJSONObject(index)
        val world = multiply4(parent, localMatrix(node))
        bindWorlds[index] = world
        if (node.has("mesh")) {
            val primitives = meshes.getJSONObject(node.getInt("mesh")).getJSONArray("primitives")
            val skinIndex = node.optInt("skin", -1)
            for (i in 0 until primitives.length()) readPrimitive(primitives.getJSONObject(i), world, index, skinIndex)
        }
        node.optJSONArray("children").ints().forEach { visit(it, world, depth + 1) }
    }

    private fun readPrimitive(primitive: JSONObject, world: FloatArray, nodeIndex: Int, skinIndex: Int) {
        if (primitive.optInt("mode", TRIANGLES) != TRIANGLES) return
        val attributes = primitive.getJSONObject("attributes")
        val raw = readFloats(attributes.getInt("POSITION"), 3)
        val count = raw.size / 3
        require(count <= 65_535) { "Model terlalu besar (maks. 65.535 titik per bagian)" }
        val indices = if (primitive.has("indices")) readInts(primitive.getInt("indices")) else IntArray(count) { it }
        require(indices.all { it in 0 until count }) { "Indeks model di luar jangkauan" }
        val rawNormals = if (attributes.has("NORMAL")) readFloats(attributes.getInt("NORMAL"), 3) else computeNormals(raw, indices)
        val hasSkin = skinIndex in parsedSkins.indices && attributes.has("JOINTS_0") && attributes.has("WEIGHTS_0")
        val positions = if (hasSkin) raw else FloatArray(raw.size)
        val normals = if (hasSkin) rawNormals else FloatArray(raw.size)
        if (!hasSkin) for (i in 0 until count) {
            transformPoint(world, raw, i * 3, positions)
            transformDirection(world, rawNormals, i * 3, normals)
        }
        var joints: FloatArray? = null
        var weights: FloatArray? = null
        var jointPalette: IntArray? = null
        if (hasSkin) {
            val rawJoints = readFloats(attributes.getInt("JOINTS_0"), 4)
            val rawWeights = readFloats(attributes.getInt("WEIGHTS_0"), 4)
            require(rawJoints.size == count * 4 && rawWeights.size == count * 4) { "Data skin tidak sesuai jumlah titik" }
            val skin = parsedSkins[skinIndex]
            val used = linkedSetOf<Int>()
            for (i in rawJoints.indices) if (rawWeights[i] > 1e-8f) {
                val joint = rawJoints[i].toInt()
                require(joint in skin.joints.indices && rawJoints[i] == joint.toFloat()) { "Indeks joint GLB di luar jangkauan" }
                used += joint
            }
            if (used.isEmpty()) {
                require(skin.joints.isNotEmpty()) { "Skin GLB tidak memiliki joint" }
                used += 0
            }
            require(used.size <= MAX_SKIN_JOINTS) { "Terlalu banyak joint aktif per bagian (maks. $MAX_SKIN_JOINTS)" }
            jointPalette = used.toIntArray()
            val remap = jointPalette.withIndex().associate { it.value to it.index }
            joints = FloatArray(rawJoints.size)
            weights = FloatArray(rawWeights.size)
            for (vertex in 0 until count) {
                val at = vertex * 4
                var sum = 0f
                for (i in 0 until 4) sum += rawWeights[at + i].coerceAtLeast(0f)
                if (sum <= 1e-8f) {
                    joints[at] = 0f
                    weights[at] = 1f
                } else for (i in 0 until 4) {
                    val weight = rawWeights[at + i].coerceAtLeast(0f) / sum
                    weights[at + i] = weight
                    joints[at + i] = if (weight > 0f) requireNotNull(remap[rawJoints[at + i].toInt()]).toFloat() else 0f
                }
            }
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
            nodeIndex = nodeIndex,
            joints = joints,
            weights = weights,
            skinIndex = if (hasSkin) skinIndex else -1,
            jointPalette = jointPalette,
        )
    }

    private fun readSkins(): List<GlbSkin> = List(skinsJson.length()) { skinIndex ->
        val skin = skinsJson.getJSONObject(skinIndex)
        val joints = skin.getJSONArray("joints").ints().toIntArray()
        require(joints.isNotEmpty() && joints.all { it in 0 until nodes.length() }) { "Skin GLB memiliki joint tidak valid" }
        val inverse = if (skin.has("inverseBindMatrices")) {
            val values = readFloats(skin.getInt("inverseBindMatrices"), 16)
            require(values.size == joints.size * 16) { "Jumlah inverse bind matrix tidak sesuai joint" }
            Array(joints.size) { i -> values.copyOfRange(i * 16, (i + 1) * 16) }
        } else {
            Array(joints.size) { IdentityMatrix4x4.copyOf() }
        }
        GlbSkin(joints, inverse)
    }

    /** Clip pertama menjadi animasi default; channel yang belum didukung dilewati. */
    private fun readAnimation(modelNodes: List<GlbNode>): GlbAnimation? {
        val animations = gltf.optJSONArray("animations") ?: return null
        if (animations.length() == 0) return null
        val animation = animations.getJSONObject(0)
        val samplers = animation.optJSONArray("samplers") ?: return null
        val channelsJson = animation.optJSONArray("channels") ?: return null
        val channels = mutableListOf<AnimationChannel>()
        var duration = 0f
        for (i in 0 until channelsJson.length()) {
            val channel = channelsJson.getJSONObject(i)
            val target = channel.optJSONObject("target") ?: continue
            val nodeIndex = target.optInt("node", -1)
            if (nodeIndex !in modelNodes.indices || modelNodes[nodeIndex].matrix != null) continue
            val path = when (target.optString("path")) {
                "translation" -> AnimationPath.TRANSLATION
                "rotation" -> AnimationPath.ROTATION
                "scale" -> AnimationPath.SCALE
                else -> continue
            }
            val samplerIndex = channel.optInt("sampler", -1)
            if (samplerIndex !in 0 until samplers.length()) continue
            val sampler = samplers.getJSONObject(samplerIndex)
            val interpolation = sampler.optString("interpolation", "LINEAR")
            if (interpolation != "LINEAR" && interpolation != "STEP") continue
            val times = readFloats(sampler.getInt("input"), 1)
            val components = if (path == AnimationPath.ROTATION) 4 else 3
            val values = readFloats(sampler.getInt("output"), components)
            if (times.isEmpty() || values.size != times.size * components) continue
            require((1 until times.size).all { times[it] >= times[it - 1] }) { "Waktu animasi GLB tidak berurutan" }
            duration = maxOf(duration, times.last())
            channels += AnimationChannel(nodeIndex, path, times, values, interpolation == "STEP")
        }
        return channels.takeIf { it.isNotEmpty() }?.let { GlbAnimation(duration, it) }
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

private fun vector(node: JSONObject, name: String, fallback: FloatArray): FloatArray =
    node.optJSONArray(name)?.let { a -> FloatArray(fallback.size) { a.getDouble(it).toFloat() } } ?: fallback

private fun sample(channel: AnimationChannel, time: Float, out: FloatArray) {
    val times = channel.times
    val components = out.size
    if (time <= times.first() || times.size == 1) {
        channel.values.copyInto(out, 0, 0, components)
        return
    }
    if (time >= times.last()) {
        channel.values.copyInto(out, 0, (times.size - 1) * components, times.size * components)
        return
    }
    var hi = times.binarySearch(time)
    if (hi >= 0) {
        channel.values.copyInto(out, 0, hi * components, (hi + 1) * components)
        return
    }
    hi = -hi - 1
    val lo = hi - 1
    val alpha = if (channel.step) 0f else (time - times[lo]) / (times[hi] - times[lo])
    val a = lo * components
    val b = hi * components
    if (channel.path == AnimationPath.ROTATION) slerp(channel.values, a, b, alpha, out)
    else for (i in 0 until components) out[i] = channel.values[a + i] + (channel.values[b + i] - channel.values[a + i]) * alpha
}

/** Quaternion slerp dengan lintasan terpendek. */
private fun slerp(values: FloatArray, a: Int, b: Int, alpha: Float, out: FloatArray) {
    var dot = (0 until 4).sumOf { (values[a + it] * values[b + it]).toDouble() }.toFloat()
    val sign = if (dot < 0f) -1f else 1f
    dot = kotlin.math.abs(dot).coerceIn(0f, 1f)
    if (dot > 0.9995f) {
        for (i in 0 until 4) out[i] = values[a + i] + (values[b + i] * sign - values[a + i]) * alpha
    } else {
        val angle = acos(dot)
        val divisor = sin(angle)
        val wa = sin((1f - alpha) * angle) / divisor
        val wb = sin(alpha * angle) / divisor
        for (i in 0 until 4) out[i] = values[a + i] * wa + values[b + i] * sign * wb
    }
    val length = sqrt(out.sumOf { (it * it).toDouble() }).toFloat().takeIf { it > 1e-8f } ?: 1f
    for (i in out.indices) out[i] /= length
}

/** Matriks lokal node (kolom-mayor): `matrix`, atau translasi × rotasi × skala. */
private fun localMatrix(node: JSONObject): FloatArray {
    node.optJSONArray("matrix")?.let { m -> return FloatArray(16) { m.getDouble(it).toFloat() } }
    val t = vector(node, "translation", floatArrayOf(0f, 0f, 0f))
    val s = vector(node, "scale", floatArrayOf(1f, 1f, 1f))
    val r = vector(node, "rotation", floatArrayOf(0f, 0f, 0f, 1f))
    return trsMatrix(t, r, s)
}

/** Matriks translation × rotation × scale, kolom-mayor. */
private fun trsMatrix(t: FloatArray, r: FloatArray, s: FloatArray): FloatArray {
    val (x, y, z) = Triple(r[0], r[1], r[2])
    val w = r[3]
    return floatArrayOf(
        (1 - 2 * (y * y + z * z)) * s[0], (2 * (x * y + z * w)) * s[0], (2 * (x * z - y * w)) * s[0], 0f,
        (2 * (x * y - z * w)) * s[1], (1 - 2 * (x * x + z * z)) * s[1], (2 * (y * z + x * w)) * s[1], 0f,
        (2 * (x * z + y * w)) * s[2], (2 * (y * z - x * w)) * s[2], (1 - 2 * (x * x + y * y)) * s[2], 0f,
        t[0], t[1], t[2], 1f,
    )
}

/** Invers matriks afin 4×4. */
private fun invert4(m: FloatArray): FloatArray {
    val a00 = m[0]; val a01 = m[4]; val a02 = m[8]
    val a10 = m[1]; val a11 = m[5]; val a12 = m[9]
    val a20 = m[2]; val a21 = m[6]; val a22 = m[10]
    val c00 = a11 * a22 - a12 * a21
    val c01 = a02 * a21 - a01 * a22
    val c02 = a01 * a12 - a02 * a11
    val c10 = a12 * a20 - a10 * a22
    val c11 = a00 * a22 - a02 * a20
    val c12 = a02 * a10 - a00 * a12
    val c20 = a10 * a21 - a11 * a20
    val c21 = a01 * a20 - a00 * a21
    val c22 = a00 * a11 - a01 * a10
    val det = a00 * c00 + a01 * c10 + a02 * c20
    require(kotlin.math.abs(det) > 1e-9f) { "Transformasi node GLB tidak dapat dibalik" }
    val d = 1f / det
    val out = floatArrayOf(
        c00 * d, c10 * d, c20 * d, 0f,
        c01 * d, c11 * d, c21 * d, 0f,
        c02 * d, c12 * d, c22 * d, 0f,
        0f, 0f, 0f, 1f,
    )
    val x = m[12]; val y = m[13]; val z = m[14]
    out[12] = -(out[0] * x + out[4] * y + out[8] * z)
    out[13] = -(out[1] * x + out[5] * y + out[9] * z)
    out[14] = -(out[2] * x + out[6] * y + out[10] * z)
    return out
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
internal const val MAX_SKIN_JOINTS = 24
