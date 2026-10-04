package com.zinmedia.camera.face

import com.zinmedia.camera.FaceAnchor
import com.zinmedia.camera.FaceEffect
import com.zinmedia.camera.FaceSide
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Titik wajah dalam piksel frame (x ke kanan, y ke bawah) dan kedalaman [z] (piksel, + = menjauh
 * dari kamera; skala sama dengan x). [z] nol semua = tanpa kedalaman (dianggap datar).
 */
internal class FacePoints(val x: FloatArray, val y: FloatArray, val z: FloatArray = FloatArray(x.size)) {
    fun px(i: Int) = x[i]
    fun py(i: Int) = y[i]
    fun pz(i: Int) = z[i]
    fun distance(a: Int, b: Int) = hypot(x[a] - x[b], y[a] - y[b])
    fun distance3(a: Int, b: Int): Float {
        val dx = x[a] - x[b]
        val dy = y[a] - y[b]
        val dz = z[a] - z[b]
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/**
 * Posisi efek di frame: pusat ternormalisasi (0..1), lebar sebagai pecahan lebar frame, tinggi
 * dalam satuan lebar frame (agar rasio gambar tetap pada frame berasio apa pun), dan sudut
 * (radian, searah jarum jam di layar).
 */
internal data class FacePlacement(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val angle: Float,
    /** Tempat tetap efek pada wajah (0 = kiri/tunggal, 1 = kanan), agar penghalus tidak tertukar. */
    val slot: Int = 0,
)

/** Indeks titik MediaPipe Face Mesh (478 titik, termasuk iris). Kiri/kanan = sisi orangnya. */
internal object Landmark {
    const val FOREHEAD_TOP = 10
    const val FOREHEAD_CENTER = 151
    const val CHIN = 152
    const val LOWER_LIP_BOTTOM = 17
    /** Tepi wajah sisi kanan orangnya (dekat telinga kanan). */
    const val FACE_RIGHT_EDGE = 234
    /** Tepi wajah sisi kiri orangnya (dekat telinga kiri). */
    const val FACE_LEFT_EDGE = 454
    const val EAR_RIGHT_UPPER = 127
    const val EAR_LEFT_UPPER = 356
    const val CHEEK_RIGHT = 50
    const val CHEEK_LEFT = 280
    const val NOSE_TIP = 4
    const val NOSE_BOTTOM = 2
    const val UPPER_LIP = 13
    const val MOUTH_RIGHT = 61
    const val MOUTH_LEFT = 291
    const val IRIS_A = 468
    const val IRIS_B = 473
    const val COUNT = 478

    // Nama lama dipakai rumus lebar wajah.
    const val FACE_LEFT = FACE_RIGHT_EDGE
    const val FACE_RIGHT = FACE_LEFT_EDGE
}

/** Toleransi menoleh: di atas ini, efek di sisi yang membelakangi kamera disembunyikan. */
private const val HIDDEN_SIDE_YAW = 0.35f

/**
 * Hitung posisi [effect] dari titik wajah [points] pada frame [frameWidth]×[frameHeight] piksel.
 * [imageAspect] = tinggi/lebar gambar efek. Menghasilkan satu posisi, atau dua untuk efek di kedua
 * sisi (telinga/pipi); sisi yang membelakangi kamera saat menoleh dilewati. Arah "atas" wajah
 * diambil dari dagu ke dahi, sehingga tetap benar walau frame di-mirror (kamera depan).
 */
internal fun placeFaceEffects(
    points: FacePoints,
    effect: FaceEffect,
    imageAspect: Float,
    frameWidth: Float,
    frameHeight: Float,
): List<FacePlacement> {
    val faceWidth = points.distance(Landmark.FACE_LEFT, Landmark.FACE_RIGHT)
    val faceHeight = points.distance(Landmark.FOREHEAD_TOP, Landmark.CHIN)
    val faceHeight3 = points.distance3(Landmark.FOREHEAD_TOP, Landmark.CHIN)
    if (faceWidth <= 1f || faceHeight <= 1f) return emptyList()
    // Vektor satuan "atas" wajah dalam 3D; proyeksinya memendek saat menunduk/mendongak.
    val up3X = (points.px(Landmark.FOREHEAD_TOP) - points.px(Landmark.CHIN)) / faceHeight3
    val up3Y = (points.py(Landmark.FOREHEAD_TOP) - points.py(Landmark.CHIN)) / faceHeight3
    val up3Z = (points.pz(Landmark.FOREHEAD_TOP) - points.pz(Landmark.CHIN)) / faceHeight3
    // Arah "belakang" kepala (normal wajah, menjauh dari kamera) = kanan × atas.
    val rx = points.px(Landmark.FACE_LEFT_EDGE) - points.px(Landmark.FACE_RIGHT_EDGE)
    val ry = points.py(Landmark.FACE_LEFT_EDGE) - points.py(Landmark.FACE_RIGHT_EDGE)
    val rz = points.pz(Landmark.FACE_LEFT_EDGE) - points.pz(Landmark.FACE_RIGHT_EDGE)
    var backX = ry * up3Z - rz * up3Y
    var backY = rz * up3X - rx * up3Z
    var backZ = rx * up3Y - ry * up3X
    val backLength = kotlin.math.sqrt(backX * backX + backY * backY + backZ * backZ).coerceAtLeast(1e-3f)
    val backSign = if (backZ < 0f) -1f else 1f
    backX = backX / backLength * backSign
    backY = backY / backLength * backSign
    backZ = backZ / backLength * backSign
    // Vektor satuan "atas" wajah (dari dagu ke dahi).
    val upX = (points.px(Landmark.FOREHEAD_TOP) - points.px(Landmark.CHIN)) / faceHeight
    val upY = (points.py(Landmark.FOREHEAD_TOP) - points.py(Landmark.CHIN)) / faceHeight
    // Sudut kemiringan kepala: 0 bila tegak (atas = (0, -1)).
    val angle = atan2(upX, -upY)
    // Menoleh: + = hidung lebih dekat ke tepi kiri orangnya (sisi kiri menjauh dari kamera).
    val yaw = (points.distance(Landmark.NOSE_TIP, Landmark.FACE_RIGHT_EDGE) -
        points.distance(Landmark.NOSE_TIP, Landmark.FACE_LEFT_EDGE)) / faceWidth
    val centerX = (points.px(Landmark.FACE_LEFT) + points.px(Landmark.FACE_RIGHT)) / 2f
    val centerY = (points.py(Landmark.FACE_LEFT) + points.py(Landmark.FACE_RIGHT)) / 2f

    /** Titik tempel dasar (piksel) dan lebar efek (piksel) sebelum [FaceEffect.scale]. */
    class Base(
        val x: Float,
        val y: Float,
        val width: Float,
        val lift: Float = 0f,
        val slot: Int = 0,
        /** Geser ke belakang kepala (satuan lebar wajah). */
        val back: Float = 0f,
        /** Tinggi gambar ikut memendek saat menunduk/mendongak (benda yang berdiri di kepala). */
        val foreshorten: Boolean = false,
    )

    fun mid(a: Int, b: Int) = (points.px(a) + points.px(b)) / 2f to (points.py(a) + points.py(b)) / 2f

    /** Telinga: antara tepi wajah dan pangkal atas telinga, digeser sedikit keluar dari wajah. */
    fun ear(edge: Int, upper: Int, slot: Int): Base {
        val (x, y) = mid(edge, upper)
        val outX = x - centerX
        val outY = y - centerY
        val length = hypot(outX, outY).coerceAtLeast(1f)
        val push = faceWidth * 0.06f
        return Base(x + outX / length * push, y + outY / length * push, faceWidth * 0.35f, slot = slot)
    }

    fun cheek(index: Int, slot: Int) = Base(points.px(index), points.py(index), faceWidth * 0.28f, slot = slot)

    // Sisi kiri orangnya terlihat kecuali menoleh jauh ke kanannya, dan sebaliknya.
    val leftVisible = yaw < HIDDEN_SIDE_YAW
    val rightVisible = yaw > -HIDDEN_SIDE_YAW

    /** Iris mata kiri/kanan orangnya: iris yang lebih dekat ke tepi wajah sisi itu. */
    val irisLeft = if (points.distance(Landmark.IRIS_A, Landmark.FACE_LEFT_EDGE) <
        points.distance(Landmark.IRIS_B, Landmark.FACE_LEFT_EDGE)
    ) Landmark.IRIS_A else Landmark.IRIS_B
    val irisRight = if (irisLeft == Landmark.IRIS_A) Landmark.IRIS_B else Landmark.IRIS_A
    fun eye(iris: Int, slot: Int) = Base(points.px(iris), points.py(iris), faceWidth * 0.25f, slot = slot)

    /** Efek di sisi kiri/kanan/keduanya sesuai [FaceEffect.side]; sisi yang membelakangi kamera dilewati. */
    fun sides(left: () -> Base, right: () -> Base): List<Base> = listOfNotNull(
        if (effect.side != FaceSide.Right && leftVisible) left() else null,
        if (effect.side != FaceSide.Left && rightVisible) right() else null,
    )

    val bases: List<Base> = when (effect.anchor) {
        FaceAnchor.Eyes -> {
            val (x, y) = mid(Landmark.IRIS_A, Landmark.IRIS_B)
            listOf(Base(x, y, faceWidth * 1.05f))
        }
        FaceAnchor.Head -> listOf(
            // Di atas & sedikit di belakang garis rambut, berdiri tegak di kepala.
            Base(
                points.px(Landmark.FOREHEAD_TOP), points.py(Landmark.FOREHEAD_TOP), faceWidth * 1.3f,
                lift = 0.4f, back = 0.25f, foreshorten = true,
            )
        )
        FaceAnchor.Forehead -> listOf(
            Base(points.px(Landmark.FOREHEAD_CENTER), points.py(Landmark.FOREHEAD_CENTER), faceWidth * 0.35f)
        )
        FaceAnchor.Nose -> listOf(Base(points.px(Landmark.NOSE_TIP), points.py(Landmark.NOSE_TIP), faceWidth * 0.32f))
        FaceAnchor.Mouth -> {
            val (x, y) = mid(Landmark.NOSE_BOTTOM, Landmark.UPPER_LIP)
            listOf(Base(x, y, points.distance(Landmark.MOUTH_LEFT, Landmark.MOUTH_RIGHT) * 1.45f))
        }
        FaceAnchor.Chin -> {
            // Janggut: dari bibir bawah ke dagu, sedikit menggantung ke bawah.
            val (x, y) = mid(Landmark.LOWER_LIP_BOTTOM, Landmark.CHIN)
            listOf(Base(x, y, faceWidth * 0.75f, lift = -0.2f))
        }
        FaceAnchor.Face -> {
            val (x, y) = mid(Landmark.FOREHEAD_TOP, Landmark.CHIN)
            listOf(Base(x, y, faceWidth * 1.15f))
        }
        FaceAnchor.Ear -> sides(
            { ear(Landmark.FACE_LEFT_EDGE, Landmark.EAR_LEFT_UPPER, 0) },
            { ear(Landmark.FACE_RIGHT_EDGE, Landmark.EAR_RIGHT_UPPER, 1) },
        )
        FaceAnchor.Cheek -> sides({ cheek(Landmark.CHEEK_LEFT, 0) }, { cheek(Landmark.CHEEK_RIGHT, 1) })
        FaceAnchor.Eye -> sides({ eye(irisLeft, 0) }, { eye(irisRight, 1) })
    }

    // Panjang proyeksi arah atas 3D di layar (1 = tegak menghadap kamera).
    val upProjected = hypot(up3X, up3Y).coerceIn(0.2f, 1f)
    return bases.map { base ->
        val width = base.width * effect.scale
        val fullHeight = width * imageAspect
        val height = if (base.foreshorten) fullHeight * upProjected else fullHeight
        // Geser sepanjang arah "atas" wajah dalam 3D (memendek saat menunduk/mendongak) dan ke
        // belakang kepala, lalu diproyeksikan ke layar.
        val lift = (base.lift + effect.offsetY) * fullHeight
        val back = base.back * faceWidth
        FacePlacement(
            centerX = (base.x + up3X * lift + backX * back) / frameWidth,
            centerY = (base.y + up3Y * lift + backY * back) / frameHeight,
            width = width / frameWidth,
            height = height / frameWidth,
            angle = angle,
            slot = base.slot,
        )
    }
}

/**
 * Penghalus gerak *One Euro filter*: halus saat diam, tetap responsif saat bergerak cepat.
 * [minCutoff] lebih kecil = lebih halus; [beta] lebih besar = lebih cepat mengejar gerakan.
 */
internal class OneEuroFilter(
    private val minCutoff: Float = 1.7f,
    private val beta: Float = 8f,
    /** Perkiraan ke depan (detik) untuk menutup jeda deteksi saat bergerak. */
    private val lead: Float = 0.03f,
) {
    private var lastValue = 0f
    private var lastDerivative = 0f
    private var lastTimeNanos = 0L
    private var initialized = false

    fun reset() {
        initialized = false
    }

    fun filter(value: Float, timeNanos: Long): Float {
        if (!initialized) {
            initialized = true
            lastValue = value
            lastDerivative = 0f
            lastTimeNanos = timeNanos
            return value
        }
        val dt = ((timeNanos - lastTimeNanos) / 1e9f).coerceAtLeast(1e-3f)
        lastTimeNanos = timeNanos
        val derivative = (value - lastValue) / dt
        lastDerivative += alpha(DERIVATIVE_CUTOFF, dt) * (derivative - lastDerivative)
        val cutoff = minCutoff + beta * kotlin.math.abs(lastDerivative)
        lastValue += alpha(cutoff, dt) * (value - lastValue)
        // Sedikit ke depan searah gerak, agar efek tidak tertinggal di belakang wajah.
        return lastValue + lastDerivative * lead
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * Math.PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }
}

/** Empat sudut efek (kiri-atas, kanan-atas, kiri-bawah, kanan-bawah) dalam koordinat sensor kamera. */
internal class FaceQuad(val sensorPoints: FloatArray)

/**
 * Sudut-sudut [placement] dalam piksel frame tegak [frameWidth]×[frameHeight] (y ke bawah),
 * berurutan kiri-atas, kanan-atas, kiri-bawah, kanan-bawah.
 */
internal fun placementCorners(placement: FacePlacement, frameWidth: Float, frameHeight: Float): FloatArray {
    val cx = placement.centerX * frameWidth
    val cy = placement.centerY * frameHeight
    val hw = placement.width * frameWidth / 2f
    val hh = placement.height * frameWidth / 2f
    val cos = kotlin.math.cos(placement.angle)
    val sin = kotlin.math.sin(placement.angle)
    val local = floatArrayOf(-hw, -hh, hw, -hh, -hw, hh, hw, hh)
    return FloatArray(8) { i ->
        val x = local[i - i % 2]
        val y = local[i - i % 2 + 1]
        if (i % 2 == 0) cx + x * cos - y * sin else cy + x * sin + y * cos
    }
}

/** Cutoff (Hz) penghalus kecepatan; lebih tinggi = lebih cepat menangkap awal gerakan. */
private const val DERIVATIVE_CUTOFF = 4f
