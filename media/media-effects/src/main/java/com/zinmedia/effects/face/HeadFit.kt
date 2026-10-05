package com.zinmedia.effects.face

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pose kepala dari titik wajah: kepala standar MediaPipe dicocokkan (putar + skala seragam +
 * geser, metode Horn) ke titik wajah orang itu. Hanya titik yang tidak ikut berekspresi (dahi,
 * pangkal hidung, sudut mata, tulang pipi, pelipis) yang dipakai, agar efek 3D tidak bergoyang saat
 * bicara atau tersenyum.
 */
internal object HeadFit {
    /** Titik wajah yang kaku (tidak ikut gerak mulut, rahang, kelopak). */
    val STABLE_POINTS: IntArray = intArrayOf(
        10, 151, 9, 8, 168, 6, 197, 195, 5, 4,
        33, 133, 263, 362, 127, 356, 234, 454, 116, 345,
        50, 280, 70, 300, 105, 334, 107, 336, 98, 327,
    )

    /**
     * Matriks [out] (4×4 kolom-mayor) dari ruang kepala standar [model] (x, y, z per titik) ke
     * ruang [target] (x, y, z per titik, satuan & sumbu yang sama arah: y atas, z ke kamera).
     * Kolom 0..2 = sumbu putar × skala. `false` bila titik tak wajar (mis. semua di satu tempat).
     */
    fun solve(model: FloatArray, target: FloatArray, out: FloatArray): Boolean {
        val n = STABLE_POINTS.size
        var ax = 0.0; var ay = 0.0; var az = 0.0
        var bx = 0.0; var by = 0.0; var bz = 0.0
        for (i in STABLE_POINTS) {
            ax += model[i * 3]; ay += model[i * 3 + 1]; az += model[i * 3 + 2]
            bx += target[i * 3]; by += target[i * 3 + 1]; bz += target[i * 3 + 2]
        }
        ax /= n; ay /= n; az /= n
        bx /= n; by /= n; bz /= n
        // Kovarians S[i][j] = Σ a'_i · b'_j.
        val s = DoubleArray(9)
        var aa = 0.0
        for (i in STABLE_POINTS) {
            val pa = doubleArrayOf(model[i * 3] - ax, model[i * 3 + 1] - ay, model[i * 3 + 2] - az)
            val pb = doubleArrayOf(target[i * 3] - bx, target[i * 3 + 1] - by, target[i * 3 + 2] - bz)
            for (r in 0 until 3) for (c in 0 until 3) s[r * 3 + c] += pa[r] * pb[c]
            aa += pa[0] * pa[0] + pa[1] * pa[1] + pa[2] * pa[2]
        }
        if (aa < 1e-9) return false
        val (sxx, sxy, sxz) = Triple(s[0], s[1], s[2])
        val (syx, syy, syz) = Triple(s[3], s[4], s[5])
        val (szx, szy, szz) = Triple(s[6], s[7], s[8])
        val m = doubleArrayOf(
            sxx + syy + szz, syz - szy, szx - sxz, sxy - syx,
            syz - szy, sxx - syy - szz, sxy + syx, szx + sxz,
            szx - sxz, sxy + syx, -sxx + syy - szz, syz + szy,
            sxy - syx, szx + sxz, syz + szy, -sxx - syy + szz,
        )
        val q = largestEigenvector4(m) ?: return false
        val (w, x, y, z) = q
        val r = doubleArrayOf(
            1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
            2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
            2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y),
        )
        // Skala: kuadrat terkecil b' ≈ s · R a'.
        var dot = 0.0
        for (row in 0 until 3) for (col in 0 until 3) dot += r[row * 3 + col] * s[col * 3 + row]
        val scale = dot / aa
        if (scale <= 0.0) return false
        val tx = bx - scale * (r[0] * ax + r[1] * ay + r[2] * az)
        val ty = by - scale * (r[3] * ax + r[4] * ay + r[5] * az)
        val tz = bz - scale * (r[6] * ax + r[7] * ay + r[8] * az)
        for (col in 0 until 3) for (row in 0 until 3) out[col * 4 + row] = (r[row * 3 + col] * scale).toFloat()
        out[3] = 0f; out[7] = 0f; out[11] = 0f
        out[12] = tx.toFloat(); out[13] = ty.toFloat(); out[14] = tz.toFloat(); out[15] = 1f
        return true
    }

    /** Vektor eigen (satuan) dari nilai eigen terbesar matriks simetris 4×4 [m] (Jacobi). */
    private fun largestEigenvector4(m: DoubleArray): DoubleArray? {
        val a = m.copyOf()
        val v = DoubleArray(16).also { for (i in 0 until 4) it[i * 4 + i] = 1.0 }
        repeat(50) {
            var off = 0.0
            for (p in 0 until 4) for (q in p + 1 until 4) off += abs(a[p * 4 + q])
            if (off < 1e-12) return@repeat
            for (p in 0 until 4) for (q in p + 1 until 4) {
                val apq = a[p * 4 + q]
                if (abs(apq) < 1e-15) continue
                val theta = (a[q * 4 + q] - a[p * 4 + p]) / (2 * apq)
                val t = (if (theta >= 0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1))
                val c = 1 / sqrt(t * t + 1)
                val s = t * c
                for (k in 0 until 4) {
                    val akp = a[k * 4 + p]
                    val akq = a[k * 4 + q]
                    a[k * 4 + p] = c * akp - s * akq
                    a[k * 4 + q] = s * akp + c * akq
                }
                for (k in 0 until 4) {
                    val apk = a[p * 4 + k]
                    val aqk = a[q * 4 + k]
                    a[p * 4 + k] = c * apk - s * aqk
                    a[q * 4 + k] = s * apk + c * aqk
                }
                for (k in 0 until 4) {
                    val vkp = v[k * 4 + p]
                    val vkq = v[k * 4 + q]
                    v[k * 4 + p] = c * vkp - s * vkq
                    v[k * 4 + q] = s * vkp + c * vkq
                }
            }
        }
        var best = 0
        for (i in 1 until 4) if (a[i * 4 + i] > a[best * 4 + best]) best = i
        val e = DoubleArray(4) { v[it * 4 + best] }
        val n = sqrt(e.sumOf { it * it })
        if (n < 1e-12 || e.any { it.isNaN() }) return null
        return DoubleArray(4) { e[it] / n }
    }
}
