package com.zinmedia.camera

import com.zinmedia.camera.face.FacePoints
import com.zinmedia.camera.face.Landmark
import com.zinmedia.camera.face.OneEuroFilter
import com.zinmedia.camera.face.placeFaceEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FaceGeometryTest {

    /** Wajah tegak di tengah frame 360×640: lebar 200 px, tinggi (dahi–dagu) 260 px. */
    private fun face(angle: Float = 0f): FacePoints {
        val xs = FloatArray(Landmark.COUNT)
        val ys = FloatArray(Landmark.COUNT)
        fun put(i: Int, x: Float, y: Float) {
            // Diputar terhadap pusat wajah (180, 320), searah jarum jam di layar.
            val dx = x - 180f
            val dy = y - 320f
            xs[i] = 180f + dx * cos(angle) - dy * sin(angle)
            ys[i] = 320f + dx * sin(angle) + dy * cos(angle)
        }
        put(Landmark.FOREHEAD_TOP, 180f, 190f)
        put(Landmark.CHIN, 180f, 450f)
        put(Landmark.FACE_RIGHT_EDGE, 80f, 320f)
        put(Landmark.FACE_LEFT_EDGE, 280f, 320f)
        put(Landmark.IRIS_A, 140f, 290f)
        put(Landmark.IRIS_B, 220f, 290f)
        put(Landmark.NOSE_TIP, 180f, 340f)
        put(Landmark.NOSE_BOTTOM, 180f, 360f)
        put(Landmark.UPPER_LIP, 180f, 380f)
        put(Landmark.MOUTH_RIGHT, 150f, 395f)
        put(Landmark.MOUTH_LEFT, 210f, 395f)
        put(Landmark.FOREHEAD_CENTER, 180f, 230f)
        put(Landmark.LOWER_LIP_BOTTOM, 180f, 410f)
        put(Landmark.EAR_RIGHT_UPPER, 82f, 290f)
        put(Landmark.EAR_LEFT_UPPER, 278f, 290f)
        put(Landmark.CHEEK_RIGHT, 120f, 350f)
        put(Landmark.CHEEK_LEFT, 240f, 350f)
        return FacePoints(xs, ys)
    }

    @Test
    fun glasses_centeredOnEyes_faceWide() {
        val p = placeFaceEffects(face(), FaceEffect("k", "u", FaceAnchor.Eyes), 0.34f, 360f, 640f).single()
        assertEquals(180f / 360f, p.centerX, 1e-4f)
        assertEquals(290f / 640f, p.centerY, 1e-4f)
        assertEquals(200f * 1.05f / 360f, p.width, 1e-4f)
        assertEquals(p.width * 0.34f, p.height, 1e-4f)
        assertEquals(0f, p.angle, 1e-4f)
    }

    @Test
    fun hat_sitsAboveForehead() {
        val p = placeFaceEffects(face(), FaceEffect("t", "u", FaceAnchor.Head), 0.78f, 360f, 640f).single()
        assertTrue("topi di atas dahi", p.centerY * 640f < 190f)
    }

    @Test
    fun tiltedHead_rotatesEffect() {
        val angle = (15 * PI / 180).toFloat()
        val p = placeFaceEffects(face(angle), FaceEffect("k", "u", FaceAnchor.Eyes), 0.34f, 360f, 640f).single()
        assertEquals(angle, p.angle, 1e-3f)
    }

    @Test
    fun mirroredFace_keepsUprightAngle() {
        // Wajah di-mirror horizontal (kamera depan): sudut tetap 0, tidak terbalik.
        val f = face()
        val mirrored = FacePoints(FloatArray(f.x.size) { 360f - f.x[it] }, f.y)
        val p = placeFaceEffects(mirrored, FaceEffect("k", "u", FaceAnchor.Eyes), 0.34f, 360f, 640f).single()
        assertEquals(0f, p.angle, 1e-4f)
    }

    @Test
    fun scaleAndOffset_applied() {
        val base = placeFaceEffects(face(), FaceEffect("n", "u", FaceAnchor.Nose), 1f, 360f, 640f).single()
        val big = placeFaceEffects(face(), FaceEffect("n", "u", FaceAnchor.Nose, scale = 2f, offsetY = 0.5f), 1f, 360f, 640f).single()
        assertEquals(base.width * 2f, big.width, 1e-4f)
        assertTrue("digeser ke atas", big.centerY < base.centerY)
    }

    @Test
    fun oneEuro_smoothsJitterButFollowsMoves() {
        val filter = OneEuroFilter()
        var t = 0L
        filter.filter(0f, t)
        var out = 0f
        repeat(10) { i ->
            t += 33_000_000L
            out = filter.filter(if (i % 2 == 0) 0.01f else -0.01f, t)
        }
        assertTrue("getaran kecil diredam", kotlin.math.abs(out) < 0.01f)
        repeat(30) {
            t += 33_000_000L
            out = filter.filter(1f, t)
        }
        assertEquals(1f, out, 0.05f)
    }

    private fun place(anchor: FaceAnchor, points: FacePoints = face(), side: FaceSide = FaceSide.Both) =
        placeFaceEffects(points, FaceEffect("x", "u", anchor, side = side), 1f, 360f, 640f)

    @Test
    fun ears_areOutsideFaceEdges() {
        val left = place(FaceAnchor.Ear, side = FaceSide.Left).single()
        val right = place(FaceAnchor.Ear, side = FaceSide.Right).single()
        assertTrue("telinga kiri orangnya di luar tepi kiri wajah", left.centerX * 360f > 280f)
        assertTrue("telinga kanan orangnya di luar tepi kanan wajah", right.centerX * 360f < 80f)
    }

    @Test
    fun bothSides_giveTwoPlacements() {
        assertEquals(2, place(FaceAnchor.Ear).size)
        assertEquals(2, place(FaceAnchor.Cheek).size)
    }

    @Test
    fun turnedHead_hidesFarSide() {
        // Menoleh ke kiri orangnya: hidung mendekati tepi kiri wajah -> sisi kiri disembunyikan.
        val f = face()
        val nose = Landmark.NOSE_TIP
        f.x[nose] = 262f
        assertEquals(1, place(FaceAnchor.Ear, f).size)
        // Telinga yang tersisa tetap di slot kanannya (penghalus tidak tertukar -> tidak meluncur).
        assertEquals(1, place(FaceAnchor.Ear, f).single().slot)
        assertEquals(0, place(FaceAnchor.Ear).first().slot)
        assertEquals(0, place(FaceAnchor.Ear, f, FaceSide.Left).size)
        assertEquals(1, place(FaceAnchor.Ear, f, FaceSide.Right).size)
    }

    @Test
    fun chin_isBelowMouth_andFaceCoversFace() {
        val chin = place(FaceAnchor.Chin).single()
        assertTrue(chin.centerY * 640f > 400f)
        val mask = place(FaceAnchor.Face).single()
        assertTrue(mask.width * 360f > 200f)
    }

    @Test
    fun eye_singleSideAndBoth() {
        assertEquals(2, place(FaceAnchor.Eye).size)
        val left = place(FaceAnchor.Eye, side = FaceSide.Left).single()
        val right = place(FaceAnchor.Eye, side = FaceSide.Right).single()
        // Mata kiri orangnya lebih dekat ke tepi kiri wajah (x besar di data uji).
        assertTrue(left.centerX > right.centerX)
    }

    /** Wajah dengan kedalaman: [pitch] + = menunduk (dahi mendekat ke kamera). */
    private fun face3d(pitch: Float): FacePoints {
        val f = face()
        val z = FloatArray(f.x.size)
        val y = f.y.copyOf()
        for (i in z.indices) {
            // Putar terhadap sumbu X di pusat wajah (y=320): z = (y-320)·sin, y = 320 + (y-320)·cos.
            val dy = f.y[i] - 320f
            y[i] = 320f + dy * kotlin.math.cos(pitch)
            z[i] = dy * kotlin.math.sin(pitch)
        }
        return FacePoints(f.x, y, z)
    }

    @Test
    fun headband_followsNod() {
        val effect = FaceEffect("k", "u", FaceAnchor.Head)
        val level = placeFaceEffects(face3d(0f), effect, 0.57f, 360f, 640f).single()
        val down = placeFaceEffects(face3d(0.5f), effect, 0.57f, 360f, 640f).single()
        val up = placeFaceEffects(face3d(-0.5f), effect, 0.57f, 360f, 640f).single()
        // Relatif terhadap garis rambut (dahi atas) di masing-masing pose.
        fun gap(p: com.zinmedia.camera.face.FacePlacement, pts: FacePoints) = pts.py(Landmark.FOREHEAD_TOP) - p.centerY * 640f
        val gLevel = gap(level, face3d(0f))
        val gDown = gap(down, face3d(0.5f))
        val gUp = gap(up, face3d(-0.5f))
        assertTrue("menunduk: bando lebih tinggi di atas dahi ($gDown > $gLevel)", gDown > gLevel)
        assertTrue("mendongak: bando turun ke belakang dahi ($gUp < $gLevel)", gUp < gLevel)
        assertTrue("telinga memendek saat menunduk", down.height < level.height)
    }
}
