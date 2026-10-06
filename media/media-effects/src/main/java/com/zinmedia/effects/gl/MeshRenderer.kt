package com.zinmedia.effects.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import com.zinmedia.effects.face.HeadFit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Jaring wajah MediaPipe (dari `res/raw/zm_face_mesh.bin`). */
internal class FaceMeshData(
    /** u, v per titik (v ke bawah, seperti gambar). */
    val uv: FloatArray,
    /** Segitiga menghadap keluar. */
    val indices: ShortArray,
    /** Titik kepala standar (cm; +X kiri orangnya, +Y atas, +Z depan), ruang model efek 3D. */
    val positions: FloatArray,
)

/**
 * Baca `zm_face_mesh.bin` (little-endian): int n, int m, float uv[2n], short indeks[m],
 * float posisi[3n].
 */
internal fun parseFaceMesh(bytes: ByteArray): FaceMeshData {
    val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(bytes.size >= 8) { "Data jaring wajah rusak" }
    val count = data.getInt()
    val indexCount = data.getInt()
    require(count in 1..65_535 && indexCount % 3 == 0 && bytes.size == 8 + count * 20 + indexCount * 2) { "Data jaring wajah rusak" }
    val uv = FloatArray(count * 2) { data.getFloat() }
    val indices = ShortArray(indexCount) { data.getShort() }
    val positions = FloatArray(count * 3) { data.getFloat() }
    return FaceMeshData(uv, indices, positions)
}

/**
 * Penggambar efek wajah di thread GL, ke FBO sendiri (warna + kedalaman, sesuai ukuran keluaran)
 * lalu dicampur ke FBO tujuan:
 * - [ActiveFaceEffect.Paint]: gambar peta UV dibungkuskan ke tiap jaring wajah, sedikit di depan kulit.
 * - [ActiveFaceEffect.Model]: model 3D di ruang kepala standar, dipasang dengan pose kepala dari
 *   jaring wajah ([HeadFit]); bagian di belakang kepala tertutup.
 * Wajah asli (jaring tanpa geser) selalu lebih dulu mengisi kedalaman, sehingga hidung/wajah
 * menutupi bagian efek yang ada di baliknya saat menoleh.
 */
internal class MeshRenderer {
    /** FBO kerja per ukuran keluaran (preview, encoder, siaran bisa berbeda). */
    private class Target(val width: Int, val height: Int, val fbo: Int, val color: Int, val depth: Int)

    private val targets = mutableListOf<Target>()
    private var ready = false

    private var meshProgram = 0
    private var mPosition = 0
    private var mNormal = 0
    private var mTexCoord = 0
    private var mMvp = 0

    private var depthProgram = 0
    private var dPosition = 0
    private var dMvp = 0

    private var compositeProgram = 0
    private var cPosition = 0

    private var modelProgram = 0
    private var oPosition = 0
    private var oNormal = 0
    private var oMvp = 0
    private var oNormalMatrix = 0
    private var oColor = 0
    private var oMetallic = 0
    private var oRoughness = 0
    private var oTexCoord = 0
    private var oHasTexture = 0
    private var oCutout = 0
    private var oJoints = 0
    private var oWeights = 0
    private var oJointMatrices = 0
    private var oSkinned = 0
    private var uploadedModel: GlbModel? = null
    private var modelParts: List<ModelPart> = emptyList()
    private var animationStartedMs = 0L
    private val head = HeadShape()
    private val normalMatrix = FloatArray(9)

    private var buffers: MeshBuffers? = null
    private var textureId = 0
    private var textureSource: Bitmap? = null

    private val clip = FloatArray(16)
    private val quad: FloatBuffer = floatBuffer(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

    /** Siapkan program di konteks GL saat ini (konteks baru = panggil lagi). */
    fun init() {
        meshProgram = createProgram(MESH_VERTEX, MESH_FRAGMENT)
        mPosition = GLES20.glGetAttribLocation(meshProgram, "aPosition")
        mNormal = GLES20.glGetAttribLocation(meshProgram, "aNormal")
        mTexCoord = GLES20.glGetAttribLocation(meshProgram, "aTexCoord")
        mMvp = GLES20.glGetUniformLocation(meshProgram, "uMvp")
        GLES20.glUseProgram(meshProgram)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(meshProgram, "sTexture"), 0)

        depthProgram = createProgram(DEPTH_VERTEX, DEPTH_FRAGMENT)
        dPosition = GLES20.glGetAttribLocation(depthProgram, "aPosition")
        dMvp = GLES20.glGetUniformLocation(depthProgram, "uMvp")

        modelProgram = createProgram(MODEL_VERTEX, MODEL_FRAGMENT)
        oPosition = GLES20.glGetAttribLocation(modelProgram, "aPosition")
        oNormal = GLES20.glGetAttribLocation(modelProgram, "aNormal")
        oMvp = GLES20.glGetUniformLocation(modelProgram, "uMvp")
        oNormalMatrix = GLES20.glGetUniformLocation(modelProgram, "uNormalMatrix")
        oColor = GLES20.glGetUniformLocation(modelProgram, "uColor")
        oMetallic = GLES20.glGetUniformLocation(modelProgram, "uMetallic")
        oRoughness = GLES20.glGetUniformLocation(modelProgram, "uRoughness")
        oTexCoord = GLES20.glGetAttribLocation(modelProgram, "aTexCoord")
        oHasTexture = GLES20.glGetUniformLocation(modelProgram, "uHasTexture")
        oCutout = GLES20.glGetUniformLocation(modelProgram, "uCutout")
        oJoints = GLES20.glGetAttribLocation(modelProgram, "aJoints")
        oWeights = GLES20.glGetAttribLocation(modelProgram, "aWeights")
        oJointMatrices = GLES20.glGetUniformLocation(modelProgram, "uJoints")
        oSkinned = GLES20.glGetUniformLocation(modelProgram, "uSkinned")
        GLES20.glUseProgram(modelProgram)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(modelProgram, "sTexture"), 0)

        compositeProgram = createProgram(COMPOSITE_VERTEX, COMPOSITE_FRAGMENT)
        cPosition = GLES20.glGetAttribLocation(compositeProgram, "aPosition")
        GLES20.glUseProgram(compositeProgram)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "sTexture"), 0)

        // Konteks GL baru: id lama milik konteks lama.
        targets.clear()
        textureId = 0
        textureSource = null
        modelParts = emptyList()
        uploadedModel = null
        ready = true
    }

    /**
     * Gambar [effect] untuk tiap wajah di [faces] (titik wajah ternormalisasi frame deteksi, lihat
     * `FaceDetector`) lalu campur ke [targetFbo] berukuran [width]×[height]. [affine] memetakan
     * titik frame deteksi (u, v; v ke bawah) ke piksel keluaran (y ke bawah):
     * x = a·u + c·v + tx, y = b·u + d·v + ty, sebagai (a, b, c, d, tx, ty). Afin bercermin
     * (determinan negatif) didukung, mis. preview kamera depan.
     */
    fun draw(
        effect: ActiveFaceEffect,
        faces: List<FloatArray>,
        affine: FloatArray,
        width: Int,
        height: Int,
        targetFbo: Int,
        frameTimestampMs: Long,
    ) {
        if (!ready || faces.isEmpty() || width <= 0 || height <= 0) return
        val current = buffers?.takeIf { it.data === effect.mesh } ?: MeshBuffers(effect.mesh).also { buffers = it }
        when (effect) {
            is ActiveFaceEffect.Paint -> drawPaint(current, effect.texture, faces, affine, width, height, targetFbo)
            is ActiveFaceEffect.Model -> drawModel(current, effect, faces, affine, width, height, targetFbo, frameTimestampMs)
        }
    }

    private fun drawPaint(current: MeshBuffers, texture: Bitmap, faces: List<FloatArray>, affine: FloatArray, width: Int, height: Int, targetFbo: Int) {
        if (textureSource !== texture) uploadTexture(texture)
        val target = beginOffscreen(width, height)

        // 1) Wajah tak terlihat: hanya kedalaman.
        GLES20.glColorMask(false, false, false, false)
        GLES20.glUseProgram(depthProgram)
        GLES20.glUniformMatrix4fv(dMvp, 1, false, clip, 0)
        for (points in faces) {
            current.fill(points, affine, offset = false)
            current.drawDepth(dPosition)
        }
        GLES20.glColorMask(true, true, true, true)

        // 2) Efek, sedikit di depan kulit.
        GLES20.glUseProgram(meshProgram)
        GLES20.glUniformMatrix4fv(mMvp, 1, false, clip, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        for (points in faces) {
            current.fill(points, affine, offset = true)
            current.draw(mPosition, mNormal, mTexCoord)
        }
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(mPosition)
        GLES20.glDisableVertexAttribArray(mNormal)
        GLES20.glDisableVertexAttribArray(mTexCoord)
        compositeTo(target, targetFbo)
    }

    private fun drawModel(
        current: MeshBuffers,
        effect: ActiveFaceEffect.Model,
        faces: List<FloatArray>,
        affine: FloatArray,
        width: Int,
        height: Int,
        targetFbo: Int,
        frameTimestampMs: Long,
    ) {
        if (uploadedModel !== effect.model) {
            modelParts.forEach { it.release() }
            modelParts = effect.model.parts.mapIndexed { i, part -> ModelPart(part, effect.textures.getOrNull(i)) }
            uploadedModel = effect.model
            animationStartedMs = frameTimestampMs
        }
        val animationSeconds = (frameTimestampMs - animationStartedMs).coerceAtLeast(0L) / 1_000f
        val modelPose = effect.model.poseAt(animationSeconds)
        // Keluaran bercermin: pose dicocokkan pada wajah yang dibalik lagi (putaran sejati), lalu
        // model ikut dicerminkan.
        val mirrored = affine[0] * affine[3] - affine[1] * affine[2] < 0f
        val mirror = if (mirrored) MIRROR_X else IDENTITY
        // Pose tiap wajah: kepala standar -> ruang pandang (piksel).
        val poses = faces.mapNotNull { points ->
            current.fill(points, affine, offset = false)
            val target = if (mirrored) current.positions.copyOf().also { p -> for (i in p.indices step 3) p[i] = -p[i] } else current.positions
            FloatArray(16).takeIf { HeadFit.solve(effect.mesh.positions, target, it) }?.let { points to multiply4(mirror, it) }
        }
        if (poses.isEmpty()) return
        val target = beginOffscreen(width, height)

        // 1) Wajah asli & kepala tak terlihat: hanya kedalaman.
        GLES20.glColorMask(false, false, false, false)
        GLES20.glUseProgram(depthProgram)
        for ((points, headPose) in poses) {
            GLES20.glUniformMatrix4fv(dMvp, 1, false, clip, 0)
            current.fill(points, affine, offset = false)
            current.drawDepth(dPosition)
            GLES20.glUniformMatrix4fv(dMvp, 1, false, multiply4(clip, multiply4(headPose, HEAD_OCCLUDER)), 0)
            head.draw(dPosition)
        }
        GLES20.glColorMask(true, true, true, true)

        // 2) Bagian padat, lalu 3) bagian tembus pandang (tanpa menulis kedalaman).
        GLES20.glUseProgram(modelProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        for (blendPass in listOf(false, true)) {
            if (blendPass) {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glDepthMask(false)
            }
            for ((_, headPose) in poses) {
                for ((index, part) in modelParts.withIndex()) if (part.blend == blendPass) {
                    setModelMatrices(headPose, modelPose.partTransforms[index])
                    part.draw(modelPose.jointMatrices[index])
                }
            }
        }
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        GLES20.glDisableVertexAttribArray(oPosition)
        GLES20.glDisableVertexAttribArray(oNormal)
        GLES20.glDisableVertexAttribArray(oTexCoord)
        GLES20.glDisableVertexAttribArray(oJoints)
        GLES20.glDisableVertexAttribArray(oWeights)
        compositeTo(target, targetFbo)
    }

    /** Matriks posisi dan normal untuk pose kepala × transformasi animasi node. */
    private fun setModelMatrices(headPose: FloatArray, partTransform: FloatArray) {
        val model = multiply4(headPose, partTransform)
        GLES20.glUniformMatrix4fv(oMvp, 1, false, multiply4(clip, model), 0)
        // inverse-transpose bagian 3×3; benar untuk rotasi, mirror, dan skala tak seragam.
        val a00 = model[0]; val a01 = model[4]; val a02 = model[8]
        val a10 = model[1]; val a11 = model[5]; val a12 = model[9]
        val a20 = model[2]; val a21 = model[6]; val a22 = model[10]
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
        val inverse = if (abs(det) > 1e-9f) 1f / det else 1f
        // Cofactor matrix = inverse-transpose untuk susunan kolom-mayor.
        normalMatrix[0] = c00 * inverse; normalMatrix[1] = c01 * inverse; normalMatrix[2] = c02 * inverse
        normalMatrix[3] = c10 * inverse; normalMatrix[4] = c11 * inverse; normalMatrix[5] = c12 * inverse
        normalMatrix[6] = c20 * inverse; normalMatrix[7] = c21 * inverse; normalMatrix[8] = c22 * inverse
        GLES20.glUniformMatrix3fv(oNormalMatrix, 1, false, normalMatrix, 0)
    }

    /**
     * Mulai menggambar ke FBO kerja seukuran keluaran: isi [clip] (ruang pandang piksel -> clip;
     * titik wajah sudah terproyeksi, jadi ortografis; kedalaman dibagi 2 × lebar agar muat),
     * bersihkan, uji kedalaman menyala.
     */
    private fun beginOffscreen(width: Int, height: Int): Target {
        val target = targets.firstOrNull { it.width == width && it.height == height } ?: createTarget(width, height)
        val w = width.toFloat()
        val h = height.toFloat()
        floatArrayOf(
            2f / w, 0f, 0f, 0f,
            0f, 2f / h, 0f, 0f,
            0f, 0f, -1f / (2f * w), 0f,
            -1f, 1f, 0f, 1f,
        ).copyInto(clip)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, target.fbo)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClearDepthf(1f)
        GLES20.glDepthMask(true)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        return target
    }

    private fun createTarget(width: Int, height: Int): Target {
        // Paling banyak beberapa ukuran keluaran; yang tertua dilepas bila berganti-ganti.
        while (targets.size >= MAX_TARGETS) deleteTarget(targets.removeAt(0))
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val color = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, color)
        setTextureParams()
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glGenRenderbuffers(1, ids, 0)
        val depth = ids[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depth)
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height)
        GLES20.glGenFramebuffers(1, ids, 0)
        val fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, color, 0)
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depth)
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return Target(width, height, fbo, color, depth).also { targets += it }
    }

    private fun deleteTarget(target: Target) {
        GLES20.glDeleteTextures(1, intArrayOf(target.color), 0)
        GLES20.glDeleteRenderbuffers(1, intArrayOf(target.depth), 0)
        GLES20.glDeleteFramebuffers(1, intArrayOf(target.fbo), 0)
    }

    /** Selesai: campur isi FBO kerja ke [targetFbo] (warna premultiplied). */
    private fun compositeTo(target: Target, targetFbo: Int) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFbo)
        GLES20.glViewport(0, 0, target.width, target.height)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(compositeProgram)
        // Unit 0 = sampler komposit; filter sebelumnya bisa meninggalkan unit lain aktif.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, target.color)
        quad.position(0)
        GLES20.glVertexAttribPointer(cPosition, 2, GLES20.GL_FLOAT, false, 8, quad)
        GLES20.glEnableVertexAttribArray(cPosition)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(cPosition)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun release() {
        if (!ready) return
        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        modelParts.forEach { it.release() }
        targets.forEach(::deleteTarget)
        targets.clear()
        GLES20.glDeleteProgram(meshProgram)
        GLES20.glDeleteProgram(depthProgram)
        GLES20.glDeleteProgram(compositeProgram)
        GLES20.glDeleteProgram(modelProgram)
        uploadedModel = null
        modelParts = emptyList()
        animationStartedMs = 0L
        textureId = 0
        textureSource = null
        ready = false
    }

    private fun uploadTexture(texture: Bitmap) {
        if (textureId == 0) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textureId = ids[0]
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        setTextureParams()
        if (!texture.isRecycled) GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, texture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        textureSource = texture
    }

    /** Buffer jaring wajah: UV & segitiga tetap, posisi & normal diisi ulang tiap frame. */
    private class MeshBuffers(val data: FaceMeshData) {
        private val count = data.uv.size / 2
        /** Posisi titik wajah terakhir di [fill] (piksel ruang pandang). */
        val positions = FloatArray(count * 3)
        private val normals = FloatArray(count * 3)
        private val positionBuffer = floatBuffer(*positions)
        private val normalBuffer = floatBuffer(*normals)
        private val uvBuffer = floatBuffer(*data.uv)
        private val indexBuffer: ShortBuffer = ByteBuffer.allocateDirect(data.indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(data.indices); position(0) }

        /**
         * Isi posisi (piksel ruang pandang: x kanan, y atas, z ke kamera) & normal keluar dari
         * [points] (titik wajah ternormalisasi, lihat `FaceDetector`) lewat [affine] (lihat
         * [MeshRenderer.draw]); [offset] = geser keluar sepanjang normal sejauh [OFFSET] × jarak
         * sudut mata (efek sedikit di depan kulit).
         */
        fun fill(points: FloatArray, affine: FloatArray, offset: Boolean) {
            val (a, b, c) = Triple(affine[0], affine[1], affine[2])
            val (d, tx, ty) = Triple(affine[3], affine[4], affine[5])
            // Kedalaman berskala sama dengan u (lebar frame deteksi).
            val depthScale = sqrt(a * a + b * b)
            // Afin bercermin membalik arah putaran segitiga: normal dibalik agar tetap keluar.
            val flip = if (a * d - b * c < 0f) -1f else 1f
            for (i in 0 until count) {
                val u = points[i * 3]
                val v = points[i * 3 + 1]
                positions[i * 3] = a * u + c * v + tx
                positions[i * 3 + 1] = -(b * u + d * v + ty)
                positions[i * 3 + 2] = -points[i * 3 + 2] * depthScale
            }
            normals.fill(0f)
            val idx = data.indices
            for (t in 0 until idx.size / 3) {
                val a = idx[t * 3] * 3
                val b = idx[t * 3 + 1] * 3
                val c = idx[t * 3 + 2] * 3
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
            for (at in 0 until count * 3 step 3) {
                val n = sqrt(normals[at] * normals[at] + normals[at + 1] * normals[at + 1] + normals[at + 2] * normals[at + 2])
                if (n > 1e-6f) {
                    normals[at] /= n
                    normals[at + 1] /= n
                    normals[at + 2] /= n
                } else {
                    normals[at + 2] = 1f
                }
            }
            if (flip < 0f) for (i in normals.indices) normals[i] = -normals[i]
            if (offset) {
                val dx = positions[EYE_LEFT * 3] - positions[EYE_RIGHT * 3]
                val dy = positions[EYE_LEFT * 3 + 1] - positions[EYE_RIGHT * 3 + 1]
                val dz = positions[EYE_LEFT * 3 + 2] - positions[EYE_RIGHT * 3 + 2]
                val shift = OFFSET * sqrt(dx * dx + dy * dy + dz * dz)
                for (i in positions.indices) positions[i] += normals[i] * shift
            }
            positionBuffer.position(0)
            positionBuffer.put(positions)
            normalBuffer.position(0)
            normalBuffer.put(normals)
        }

        fun drawDepth(position: Int) {
            positionBuffer.position(0)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, positionBuffer)
            GLES20.glEnableVertexAttribArray(position)
            indexBuffer.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, data.indices.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
            GLES20.glDisableVertexAttribArray(position)
        }

        fun draw(position: Int, normal: Int, texCoord: Int) {
            positionBuffer.position(0)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, positionBuffer)
            GLES20.glEnableVertexAttribArray(position)
            normalBuffer.position(0)
            GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 12, normalBuffer)
            GLES20.glEnableVertexAttribArray(normal)
            uvBuffer.position(0)
            GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 8, uvBuffer)
            GLES20.glEnableVertexAttribArray(texCoord)
            indexBuffer.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, data.indices.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
        }
    }

    /** Satu bagian model di GPU (buffer di memori klien, digambar dengan [modelProgram]). */
    private inner class ModelPart(part: GlbPart, texture: Bitmap?) {
        val blend = part.blend
        private val cutout = part.cutout
        private val positions = floatBuffer(*part.positions)
        private val normals = floatBuffer(*part.normals)
        private val texCoords = part.texCoords?.let { floatBuffer(*it) }
        private val joints = part.joints?.let { floatBuffer(*it) }
        private val weights = part.weights?.let { floatBuffer(*it) }
        private val indices: ShortBuffer = ByteBuffer.allocateDirect(part.indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(part.indices); position(0) }
        private val count = part.indices.size
        private val color = part.color
        private val metallic = part.metallic
        private val roughness = part.roughness
        private var textureId = 0

        init {
            if (texture != null && !texture.isRecycled && texCoords != null) {
                val ids = IntArray(1)
                GLES20.glGenTextures(1, ids, 0)
                textureId = ids[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, texture, 0)
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            }
        }

        fun draw(jointMatrices: FloatArray?) {
            positions.position(0)
            GLES20.glVertexAttribPointer(oPosition, 3, GLES20.GL_FLOAT, false, 12, positions)
            GLES20.glEnableVertexAttribArray(oPosition)
            normals.position(0)
            GLES20.glVertexAttribPointer(oNormal, 3, GLES20.GL_FLOAT, false, 12, normals)
            GLES20.glEnableVertexAttribArray(oNormal)
            if (jointMatrices != null && joints != null && weights != null) {
                joints.position(0)
                GLES20.glVertexAttribPointer(oJoints, 4, GLES20.GL_FLOAT, false, 16, joints)
                GLES20.glEnableVertexAttribArray(oJoints)
                weights.position(0)
                GLES20.glVertexAttribPointer(oWeights, 4, GLES20.GL_FLOAT, false, 16, weights)
                GLES20.glEnableVertexAttribArray(oWeights)
                GLES20.glUniformMatrix4fv(oJointMatrices, jointMatrices.size / 16, false, jointMatrices, 0)
                GLES20.glUniform1f(oSkinned, 1f)
            } else {
                GLES20.glDisableVertexAttribArray(oJoints)
                GLES20.glDisableVertexAttribArray(oWeights)
                GLES20.glVertexAttrib4f(oJoints, 0f, 0f, 0f, 0f)
                GLES20.glVertexAttrib4f(oWeights, 1f, 0f, 0f, 0f)
                GLES20.glUniform1f(oSkinned, 0f)
            }
            val uv = texCoords
            if (textureId != 0 && uv != null) {
                uv.position(0)
                GLES20.glVertexAttribPointer(oTexCoord, 2, GLES20.GL_FLOAT, false, 8, uv)
                GLES20.glEnableVertexAttribArray(oTexCoord)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLES20.glUniform1f(oHasTexture, 1f)
            } else {
                GLES20.glDisableVertexAttribArray(oTexCoord)
                GLES20.glVertexAttrib2f(oTexCoord, 0f, 0f)
                GLES20.glUniform1f(oHasTexture, 0f)
            }
            GLES20.glUniform1f(oCutout, if (cutout) 1f else 0f)
            GLES20.glUniform4fv(oColor, 1, color, 0)
            GLES20.glUniform1f(oMetallic, metallic)
            GLES20.glUniform1f(oRoughness, roughness)
            indices.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, indices)
        }

        fun release() {
            if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
    }

    /**
     * Kepala tak terlihat satuan: bulat dari atas ke bawah, potongan mendatarnya superelips
     * (|x|⁴ + |z|⁴ = 1) seperti bentuk kepala. Diskalakan/digeser dengan [HEAD_OCCLUDER].
     */
    private class HeadShape {
        private val positions: FloatBuffer
        private val indices: ShortBuffer
        private val count: Int

        init {
            val rings = 16
            val segments = 48
            val pos = FloatArray((rings + 1) * (segments + 1) * 3)
            var p = 0
            for (r in 0..rings) {
                val phi = Math.PI * r / rings
                for (k in 0..segments) {
                    val theta = 2 * Math.PI * k / segments
                    pos[p++] = (sin(phi) * boxy(cos(theta))).toFloat()
                    pos[p++] = cos(phi).toFloat()
                    pos[p++] = (sin(phi) * boxy(sin(theta))).toFloat()
                }
            }
            val idx = ShortArray(rings * segments * 6)
            var i = 0
            for (r in 0 until rings) for (k in 0 until segments) {
                val a = (r * (segments + 1) + k).toShort()
                val b = (a + segments + 1).toShort()
                idx[i++] = a; idx[i++] = b; idx[i++] = (a + 1).toShort()
                idx[i++] = (a + 1).toShort(); idx[i++] = b; idx[i++] = (b + 1).toShort()
            }
            positions = floatBuffer(*pos)
            indices = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(idx); position(0) }
            count = idx.size
        }

        private fun boxy(v: Double) = Math.signum(v) * sqrt(abs(v))

        fun draw(position: Int) {
            positions.position(0)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, positions)
            GLES20.glEnableVertexAttribArray(position)
            indices.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, indices)
            GLES20.glDisableVertexAttribArray(position)
        }
    }

    private companion object {
        /** FBO kerja berbeda ukuran yang disimpan sekaligus. */
        const val MAX_TARGETS = 3

        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

        /** Cermin sumbu x ruang pandang (kolom-mayor). */
        val MIRROR_X = floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

        /**
         * Kepala tak terlihat di ruang kepala standar (cm): pusat (0; 1; −3,6), jari-jari
         * (7,2; 11; 9,2). Sama dengan `tools/fit_face_props.py` (HEAD_CENTER, HEAD_RADIUS).
         */
        val HEAD_OCCLUDER = floatArrayOf(
            7.2f, 0f, 0f, 0f,
            0f, 11f, 0f, 0f,
            0f, 0f, 9.2f, 0f,
            0f, 1f, -3.6f, 1f,
        )

        const val MODEL_VERTEX = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;
            attribute vec4 aJoints;
            attribute vec4 aWeights;
            uniform mat4 uMvp;
            uniform mat3 uNormalMatrix;
            uniform mat4 uJoints[24];
            uniform float uSkinned;
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            void main() {
                vec4 position = vec4(aPosition, 1.0);
                vec3 normal = aNormal;
                if (uSkinned > 0.5) {
                    mat4 skin = aWeights.x * uJoints[int(aJoints.x + 0.5)]
                        + aWeights.y * uJoints[int(aJoints.y + 0.5)]
                        + aWeights.z * uJoints[int(aJoints.z + 0.5)]
                        + aWeights.w * uJoints[int(aJoints.w + 0.5)];
                    position = skin * position;
                    normal = mat3(skin) * normal;
                }
                gl_Position = uMvp * position;
                vNormal = uNormalMatrix * normal;
                vTexCoord = aTexCoord;
            }
        """

        // Cahaya dari depan-atas, kilap Blinn-Phong, dan pantulan tepi; keluaran premultiplied.
        const val MODEL_FRAGMENT = """
            precision mediump float;
            uniform vec4 uColor;
            uniform float uMetallic;
            uniform float uRoughness;
            uniform float uHasTexture;
            uniform float uCutout;
            uniform sampler2D sTexture;
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            void main() {
                vec4 base = uColor;
                if (uHasTexture > 0.5) {
                    vec4 t = texture2D(sTexture, vTexCoord);
                    if (t.a > 0.0) t.rgb /= t.a;
                    base *= t;
                }
                if (uCutout > 0.5 && base.a < 0.5) discard;
                if (uCutout > 0.5) base.a = 1.0;
                vec3 n = normalize(vNormal);
                if (n.z < 0.0) n = -n;
                vec3 light = normalize(vec3(0.35, 0.6, 0.75));
                float diffuse = max(dot(n, light), 0.0);
                float spec = pow(max(dot(n, normalize(light + vec3(0.0, 0.0, 1.0))), 0.0), mix(160.0, 8.0, uRoughness));
                vec3 specColor = mix(vec3(0.6), base.rgb, uMetallic) * (1.0 - 0.7 * uRoughness);
                float fresnel = pow(1.0 - n.z, 3.0);
                vec3 rgb = base.rgb * (1.0 - 0.6 * uMetallic) * (0.4 + 0.65 * diffuse)
                    + specColor * spec + vec3(0.25) * fresnel * (1.0 - uRoughness);
                float a = clamp(base.a + spec * 0.6, 0.0, 1.0);
                gl_FragColor = vec4(rgb * a, a);
            }
        """

        /** Sudut luar mata kanan & kiri orangnya (MediaPipe). */
        const val EYE_RIGHT = 33
        const val EYE_LEFT = 263

        /** Efek di depan kulit: × jarak sudut mata (±2 mm). */
        const val OFFSET = 0.025f

        const val MESH_VERTEX = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;
            uniform mat4 uMvp;
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                vNormal = aNormal;
                vTexCoord = aTexCoord;
            }
        """

        // Cahaya dari depan-atas dan kilap halus mengikuti lekuk wajah. Tekstur dari Bitmap
        // ber-alfa premultiplied; keluaran premultiplied. Bagian kosong (alfa ~0) tidak digambar.
        const val MESH_FRAGMENT = """
            precision mediump float;
            uniform sampler2D sTexture;
            varying vec3 vNormal;
            varying vec2 vTexCoord;
            void main() {
                vec4 t = texture2D(sTexture, vTexCoord);
                if (t.a < 0.02) discard;
                vec3 base = t.rgb / t.a;
                vec3 n = normalize(vNormal);
                if (n.z < 0.0) n = -n;
                vec3 light = normalize(vec3(0.35, 0.6, 0.75));
                float diffuse = max(dot(n, light), 0.0);
                float spec = pow(max(dot(n, normalize(light + vec3(0.0, 0.0, 1.0))), 0.0), 48.0);
                vec3 rgb = base * (0.55 + 0.5 * diffuse) + vec3(0.35) * spec;
                gl_FragColor = vec4(rgb * t.a, t.a);
            }
        """

        const val DEPTH_VERTEX = """
            attribute vec3 aPosition;
            uniform mat4 uMvp;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        const val DEPTH_FRAGMENT = """
            precision mediump float;
            void main() {
                gl_FragColor = vec4(0.0);
            }
        """

        const val COMPOSITE_VERTEX = """
            attribute vec2 aPosition;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aPosition * 0.5 + 0.5;
            }
        """

        const val COMPOSITE_FRAGMENT = """
            precision mediump float;
            uniform sampler2D sTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
    }
}

private fun setTextureParams() {
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
}

private fun floatBuffer(vararg values: Float): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(values)
        position(0)
    }

private fun createProgram(vertex: String, fragment: String): Int {
    fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { "Shader gagal: ${GLES20.glGetShaderInfoLog(shader)}" }
    }
    return GLES20.glCreateProgram().also { program ->
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, vertex))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "Program gagal: ${GLES20.glGetProgramInfoLog(program)}" }
    }
}
