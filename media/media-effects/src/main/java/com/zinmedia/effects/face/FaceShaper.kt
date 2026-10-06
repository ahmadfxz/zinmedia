package com.zinmedia.effects.face

import com.zinmedia.effects.BeautyFeature
import com.zinmedia.effects.BeautyParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

/**
 * Jaring beauty (dari `res/raw/zm_beauty_mesh.bin`): segitiga warp/masker atas titik wajah
 * (0..477) + cincin luar (satu per titik [oval]), bobot masker per titik untuk area lembut
 * ([channels] kanal, 0..255), dan segitiga area bertepi tegas ([regions], digambar penuh ke kanal
 * [REGION_CHANNELS]). Urutan lihat `tools/make_beauty_mesh_bin.py`.
 */
internal class BeautyMesh(
    val vertexCount: Int,
    val indices: ShortArray,
    val oval: IntArray,
    val channels: Int,
    val weights: ByteArray,
    val regions: List<ShortArray>,
) {
    companion object {
        /** Kanal tiap area di [regions]: bibir, bukaan mulut (gigi), bukaan mata. */
        val REGION_CHANNELS = intArrayOf(3, 4, 5)
    }
}

internal fun parseBeautyMesh(bytes: ByteArray): BeautyMesh {
    val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(bytes.size >= 16) { "Data beauty rusak" }
    val vertices = data.getInt()
    val indexCount = data.getInt()
    val ovalCount = data.getInt()
    val channels = data.getInt()
    require(
        vertices in 1..65_535 && indexCount % 3 == 0 && channels in 1..16 &&
            bytes.size >= 16 + indexCount * 2 + ovalCount * 2 + vertices * channels,
    ) { "Data beauty rusak" }
    val indices = ShortArray(indexCount) { data.getShort() }
    val oval = IntArray(ovalCount) { data.getShort().toInt() }
    val weights = ByteArray(vertices * channels).also { data.get(it) }
    val regions = BeautyMesh.REGION_CHANNELS.map {
        require(data.remaining() >= 4) { "Data beauty rusak" }
        val count = data.getInt()
        require(count >= 0 && count % 3 == 0 && data.remaining() >= count * 2) { "Data beauty rusak" }
        ShortArray(count) { data.getShort() }
    }
    require(!data.hasRemaining()) { "Data beauty rusak" }
    require(vertices == FaceShaper.POINTS + ovalCount && (listOf(indices) + regions).all { r -> r.all { it in 0 until vertices } }) { "Data beauty rusak" }
    return BeautyMesh(vertices, indices, oval, channels, weights, regions)
}

/**
 * Bentuk wajah beauty: tiap fitur menggeser titik wajah di koordinat wajah (melintang = garis mata,
 * menuju dagu; dinormalkan ke setengah lebar/tinggi wajah), sehingga ikut miring, jarak, dan
 * ukuran wajah tiap orang. Gambar lalu digambar ulang lewat jaring yang titiknya digeser.
 */
internal object FaceShaper {
    const val POINTS = 478

    /** Cincin luar = oval wajah diperbesar dari pusatnya; tidak bergeser (tepi menyatu dengan latar). */
    private const val RING_SCALE = 1.35f

    /**
     * Isi [source] (posisi asli) dan [target] (posisi setelah beauty) untuk semua titik jaring
     * ([BeautyMesh.vertexCount] × (u, v), pecahan frame, v ke bawah) dari titik wajah [points]
     * (lihat `FaceDetector`) pada frame deteksi [width]×[height] piksel.
     */
    fun shape(
        points: FloatArray, width: Float, height: Float, beauty: BeautyParams, oval: IntArray,
        source: FloatArray, target: FloatArray, work: Workspace = Workspace(),
    ) {
        // Piksel frame (isotropik).
        val px = work.px
        val py = work.py
        for (i in 0 until POINTS) {
            px[i] = points[i * 3] * width
            py[i] = points[i * 3 + 1] * height
        }
        val frame = faceFrame(px, py)
        val dx = work.dx
        val dy = work.dy
        dx.fill(0f)
        dy.fill(0f)
        if (beauty.reshapes) displace(px, py, frame, beauty, dx, dy)

        for (i in 0 until POINTS) {
            source[i * 2] = px[i] / width
            source[i * 2 + 1] = py[i] / height
            target[i * 2] = (px[i] + dx[i]) / width
            target[i * 2 + 1] = (py[i] + dy[i]) / height
        }
        for ((k, i) in oval.withIndex()) {
            val rx = frame.cx + (px[i] - frame.cx) * RING_SCALE
            val ry = frame.cy + (py[i] - frame.cy) * RING_SCALE
            val at = (POINTS + k) * 2
            source[at] = rx / width
            source[at + 1] = ry / height
            target[at] = source[at]
            target[at + 1] = source[at + 1]
        }
    }

    /** Larik sementara [shape], dipakai ulang tiap frame agar tidak membuat sampah memori. */
    class Workspace {
        val px = FloatArray(POINTS)
        val py = FloatArray(POINTS)
        val dx = FloatArray(POINTS)
        val dy = FloatArray(POINTS)
    }

    /** Pusat, sumbu (lebar = garis mata, tinggi = menuju dagu), dan setengah ukuran wajah. */
    internal class Frame(
        val cx: Float, val cy: Float,
        val ex: Float, val ey: Float,
        val ux: Float, val uy: Float,
        val halfWidth: Float, val halfHeight: Float,
        val eyeDistance: Float,
    )

    internal fun faceFrame(px: FloatArray, py: FloatArray): Frame {
        val ax = (px[33] + px[133]) / 2f
        val ay = (py[33] + py[133]) / 2f
        val bx = (px[362] + px[263]) / 2f
        val by = (py[362] + py[263]) / 2f
        val eyeDistance = hypot(bx - ax, by - ay).coerceAtLeast(1f)
        val ex = (bx - ax) / eyeDistance
        val ey = (by - ay) / eyeDistance
        // Tegak lurus garis mata, mengarah ke dagu.
        var ux = -ey
        var uy = ex
        if (ux * (px[CHIN] - px[FOREHEAD]) + uy * (py[CHIN] - py[FOREHEAD]) < 0f) {
            ux = -ux
            uy = -uy
        }
        val halfWidth = abs((px[FACE_RIGHT] - px[FACE_LEFT]) * ex + (py[FACE_RIGHT] - py[FACE_LEFT]) * ey) / 2f
        val halfHeight = abs((px[CHIN] - px[FOREHEAD]) * ux + (py[CHIN] - py[FOREHEAD]) * uy) / 2f
        val along = ((px[FACE_LEFT] + px[FACE_RIGHT]) * ex + (py[FACE_LEFT] + py[FACE_RIGHT]) * ey) / 2f
        val down = ((px[FOREHEAD] + px[CHIN]) * ux + (py[FOREHEAD] + py[CHIN]) * uy) / 2f
        return Frame(
            ex * along + ux * down, ey * along + uy * down, ex, ey, ux, uy,
            halfWidth.coerceAtLeast(1f), halfHeight.coerceAtLeast(1f), eyeDistance,
        )
    }

    private fun displace(px: FloatArray, py: FloatArray, f: Frame, beauty: BeautyParams, dx: FloatArray, dy: FloatArray) {
        fun v(feature: BeautyFeature) = beauty[feature]
        val slim = v(BeautyFeature.SlimFace)
        val vShape = v(BeautyFeature.VShape)
        val narrow = v(BeautyFeature.NarrowFace)
        val small = v(BeautyFeature.SmallFace)
        val cheekbones = v(BeautyFeature.Cheekbones)
        val jaw = v(BeautyFeature.Jaw)
        val chin = v(BeautyFeature.Chin)
        val forehead = v(BeautyFeature.Forehead)
        val eyes = v(BeautyFeature.EnlargeEyes)
        val eyeDistance = v(BeautyFeature.EyeDistance)
        val eyeAngle = v(BeautyFeature.EyeAngle)
        val nose = v(BeautyFeature.Nose)
        val noseLength = v(BeautyFeature.NoseLength)
        val mouth = v(BeautyFeature.MouthSize)
        val smile = v(BeautyFeature.Smile)

        val eyeCenters = arrayOf(center(px, py, EYE_A), center(px, py, EYE_B))
        val eyeRadius = f.eyeDistance * 0.42f
        val noseCenter = floatArrayOf((px[4] + px[197]) / 2f, (py[4] + py[197]) / 2f)
        val mouthCenter = floatArrayOf((px[13] + px[14]) / 2f, (py[13] + py[14]) / 2f)
        val mouthHalf = hypot(px[291] - px[61], py[291] - py[61]) / 2f
        val corners = arrayOf(floatArrayOf(px[61], py[61]), floatArrayOf(px[291], py[291]))

        for (i in 0 until POINTS) {
            val rx = px[i] - f.cx
            val ry = py[i] - f.cy
            val lx = (rx * f.ex + ry * f.ey) / f.halfWidth
            val ly = (rx * f.ux + ry * f.uy) / f.halfHeight
            val side = sign(lx)
            var across = 0f // satuan setengah lebar, + = menjauh dari garis tengah
            var downward = 0f // satuan setengah tinggi, + = ke arah dagu
            var ox = 0f
            var oy = 0f

            // ---- wajah ----
            val cheekBand = smoothstep(-0.35f, 0f, ly) * (1f - smoothstep(0.95f, 1.2f, ly))
            across -= slim * 0.10f * smoothstep(0.35f, 0.95f, abs(lx)) * cheekBand
            across -= vShape * 0.12f * smoothstep(0.15f, 0.9f, abs(lx)) * smoothstep(0.3f, 1f, ly)
            across -= narrow * 0.08f * abs(lx)
            across -= cheekbones * 0.07f * smoothstep(0.5f, 0.95f, abs(lx)) * bell(ly, 0.05f, 0.3f)
            across -= jaw * 0.08f * smoothstep(0.45f, 0.95f, abs(lx)) * bell(ly, 0.65f, 0.25f)
            downward += chin * 0.10f * smoothstep(0.55f, 1f, ly) * (1f - smoothstep(0.4f, 0.8f, abs(lx)))
            downward -= forehead * 0.10f * (1f - smoothstep(-1f, -0.5f, ly))
            // Wajah kecil: semua titik ditarik ke pusat.
            ox -= rx * small * 0.07f
            oy -= ry * small * 0.07f

            // ---- mata ----
            for ((k, e) in eyeCenters.withIndex()) {
                val ex = px[i] - e[0]
                val ey = py[i] - e[1]
                val w = falloff(hypot(ex, ey) / eyeRadius)
                if (w <= 0f) continue
                ox += ex * eyes * 0.3f * w
                oy += ey * eyes * 0.3f * w
                // Mata pertama (k = 0) di sisi −sumbu lebar.
                val outward = if (k == 0) -1f else 1f
                ox += f.ex * outward * f.halfWidth * eyeDistance * 0.05f * w
                oy += f.ey * outward * f.halfWidth * eyeDistance * 0.05f * w
                // Sudut mata: ujung luar naik (+) / turun (−), simetris kiri-kanan.
                val angle = eyeAngle * 0.12f * w * outward
                val c = cos(angle)
                val s = sin(angle)
                ox += ex * c - ey * s - ex
                oy += ex * s + ey * c - ey
            }

            // ---- hidung ----
            run {
                val nx = px[i] - noseCenter[0]
                val ny = py[i] - noseCenter[1]
                val w = falloff(hypot(nx, ny) / (f.eyeDistance * 0.45f))
                val acrossNose = nx * f.ex + ny * f.ey
                ox -= f.ex * acrossNose * nose * 0.3f * w
                oy -= f.ey * acrossNose * nose * 0.3f * w
                val tip = falloff(hypot(px[i] - px[4], py[i] - py[4]) / (f.eyeDistance * 0.35f))
                ox += f.ux * f.halfHeight * noseLength * 0.05f * tip
                oy += f.uy * f.halfHeight * noseLength * 0.05f * tip
            }

            // ---- mulut ----
            run {
                val mx = px[i] - mouthCenter[0]
                val my = py[i] - mouthCenter[1]
                val w = falloff(hypot(mx, my) / (mouthHalf * 1.3f))
                ox += mx * mouth * 0.25f * w
                oy += my * mouth * 0.25f * w
                for ((k, corner) in corners.withIndex()) {
                    val cw = falloff(hypot(px[i] - corner[0], py[i] - corner[1]) / (mouthHalf * 0.7f))
                    val out = if (k == 0) -1f else 1f
                    // Senyum: sudut bibir naik & sedikit melebar.
                    ox += (-f.ux * f.halfHeight * 0.05f + f.ex * out * f.halfWidth * 0.02f) * smile * cw
                    oy += (-f.uy * f.halfHeight * 0.05f + f.ey * out * f.halfWidth * 0.02f) * smile * cw
                }
            }

            var tx = ox + f.ex * side * across * f.halfWidth + f.ux * downward * f.halfHeight
            var ty = oy + f.ey * side * across * f.halfWidth + f.uy * downward * f.halfHeight
            // Batas geser agar jaring tidak terlipat.
            val length = hypot(tx, ty)
            val limit = f.halfWidth * MAX_SHIFT
            if (length > limit) {
                tx *= limit / length
                ty *= limit / length
            }
            dx[i] = tx
            dy[i] = ty
        }
    }

    private fun center(px: FloatArray, py: FloatArray, ring: IntArray): FloatArray =
        floatArrayOf(ring.map { px[it] }.average().toFloat(), ring.map { py[it] }.average().toFloat())

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 1 di pusat, 0 di jarak [t] ≥ 1, halus. */
    private fun falloff(t: Float): Float = if (t >= 1f) 0f else (1f - t * t).let { it * it }

    /** Lonceng di [center] selebar [width]. */
    private fun bell(x: Float, center: Float, width: Float): Float = falloff(abs(x - center) / width)

    private const val MAX_SHIFT = 0.25f
    private const val FOREHEAD = 10
    private const val CHIN = 152
    private const val FACE_LEFT = 234
    private const val FACE_RIGHT = 454
    private val EYE_A = intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)
    private val EYE_B = intArrayOf(263, 249, 390, 373, 374, 380, 381, 382, 362, 398, 384, 385, 386, 387, 388, 466)
}
