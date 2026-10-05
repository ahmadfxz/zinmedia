package com.zinmedia.effects.gl

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Hasil deteksi wajah untuk satu frame: titik wajah per wajah (lihat `FaceDetector`) dan apakah
 * frame deteksinya dibalik dari frame stream (cermin).
 */
internal class FrameResult(val frame: Long, val meshes: List<FloatArray>, val flipped: Boolean)

/**
 * Menyelaraskan efek dengan frame-nya sendiri, seperti TikTok: frame ditahan, lalu digambar dengan
 * posisi wajah milik frame itu. Efek tidak pernah tertinggal; harganya video sedikit terlambat.
 *
 * - Frame tidak pernah ditampilkan sebelum ada hasil deteksi sampai [window] frame sesudahnya
 *   (kecuali cincin frame penuh karena deteksi terlalu lambat).
 * - Frame yang tidak dideteksi memakai interpolasi hasil sebelum & sesudahnya, bukan tebakan.
 * - Getar diredam dengan rata-rata simetris (sebelum, saat, sesudah), yang tidak menimbulkan
 *   keterlambatan pada gerakan.
 * - Jeda dijaga stabil agar video tetap mulus; batas di atas tetap dijamin walau harus mengulang
 *   satu frame.
 *
 * [onFrame], [outputFrame], [resultFor], dan [reset] dipanggil di thread GL; [add] dari thread
 * hasil deteksi.
 */
internal class FrameSync(private val maxDelay: Int) {
    private val lock = Any()
    private val results = ArrayDeque<FrameResult>()

    @Volatile
    private var latestFrame = 0L

    // Dijaga lock.
    private var latencyAvg = 1f
    private var gapAvg = 1f
    private var newestResult = -1L

    /** Jumlah frame yang ditahan saat ini (target; frame bisa ditahan lebih lama bila perlu). */
    var delay = 1
        private set

    /** Jarak (frame) titik sebelum & sesudah untuk rata-rata peredam getar. */
    var window = 1
        private set
    private var framesSinceChange = 0
    private var startFrame = -1L
    private var lastOut = -1L

    /** Frame baru [frame] (bertambah 1 tiap frame) masuk; jeda disesuaikan perlahan. */
    fun onFrame(frame: Long) {
        latestFrame = frame
        if (startFrame < 0) startFrame = frame
        val (latency, gap) = synchronized(lock) { latencyAvg to gapAvg }
        window = gap.roundToInt().coerceIn(1, MAX_WINDOW)
        val target = ceil(latency + (gap - 1f) + window - TOLERANCE).toInt().coerceIn(1, maxDelay)
        framesSinceChange++
        // Ganti jeda = satu frame diulang/dilewati; dibatasi agar tidak sering.
        if (target != delay && framesSinceChange >= CHANGE_EVERY) {
            delay += if (target > delay) 1 else -1
            framesSinceChange = 0
        }
    }

    /**
     * Frame yang digambar saat frame [frame] masuk: tertahan [delay] frame, tetapi tidak pernah
     * melewati hasil deteksi terbaru dikurangi [window], tidak pernah mundur, dan selalu masih
     * tersimpan (≥ frame − maxDelay).
     */
    fun outputFrame(frame: Long): Long {
        val oldest = maxOf(startFrame, frame - maxDelay)
        val newest = synchronized(lock) { newestResult }
        val wanted = if (newest >= 0) minOf(frame - delay, newest - window) else oldest
        val out = maxOf(wanted, lastOut, oldest)
        lastOut = out
        return out
    }

    fun add(result: FrameResult) = synchronized(lock) {
        // Hasil baru bisa dipakai mulai frame berikutnya.
        val latency = (latestFrame - result.frame + 1).coerceAtLeast(1)
        latencyAvg += (latency - latencyAvg) * SMOOTHING
        if (newestResult >= 0 && result.frame > newestResult) {
            gapAvg += ((result.frame - newestResult) - gapAvg) * SMOOTHING
        }
        newestResult = maxOf(newestResult, result.frame)
        while (results.isNotEmpty() && results.last().frame >= result.frame) results.removeLast()
        results.addLast(result)
        while (results.size > KEEP) results.removeFirst()
    }

    /**
     * Hasil untuk [frame], diredam dengan rata-rata simetris 1:2:1 dari titik [frame] − [window],
     * [frame], dan [frame] + [window] (bila jumlah wajahnya sama). `null` = belum ada hasil.
     */
    fun resultFor(frame: Long): FrameResult? {
        val snapshot = synchronized(lock) { results.toList() }
        val center = interpolatedAt(snapshot, frame) ?: return null
        val before = interpolatedAt(snapshot, frame - window)
        val after = interpolatedAt(snapshot, frame + window)
        if (before == null || after == null) return center
        return average(before, center, after) ?: center
    }

    /** Efek diganti atau dimatikan: buang hasil lama dan mulai dari frame berikutnya. */
    fun reset() {
        synchronized(lock) {
            results.clear()
            newestResult = -1L
        }
        startFrame = -1L
        lastOut = -1L
    }

    private companion object {
        const val SMOOTHING = 0.1f
        /** Rata-rata yang hanya sedikit di atas bilangan bulat tidak menambah satu frame. */
        const val TOLERANCE = 0.05f
        const val CHANGE_EVERY = 15
        const val MAX_WINDOW = 4
        const val KEEP = 24
    }
}

/** Hasil di [frame] dari daftar [results] (urut): miliknya, interpolasi, atau yang terdekat di tepi. */
private fun interpolatedAt(results: List<FrameResult>, frame: Long): FrameResult? {
    val before = results.lastOrNull { it.frame <= frame }
    val after = results.firstOrNull { it.frame >= frame }
    return when {
        before == null -> after
        after == null || after.frame == before.frame -> before
        else -> interpolate(before, after, (frame - before.frame).toFloat() / (after.frame - before.frame))
    }
}

/** Campuran [a] dan [b] (t = 0..1); jumlah wajah berbeda = pakai yang terdekat. */
internal fun interpolate(a: FrameResult, b: FrameResult, t: Float): FrameResult {
    val nearest = if (t < 0.5f) a else b
    val meshes = if (sameShape(a.meshes, b.meshes)) a.meshes.zip(b.meshes) { p, q -> lerp(p, q, t) } else nearest.meshes
    return FrameResult(a.frame, meshes, nearest.flipped)
}

/** Rata-rata 1:2:1; `null` bila jumlah wajah tidak sama. */
private fun average(a: FrameResult, b: FrameResult, c: FrameResult): FrameResult? {
    if (!sameShape(a.meshes, b.meshes) || !sameShape(b.meshes, c.meshes)) return null
    fun mix(p: FloatArray, q: FloatArray, r: FloatArray) = FloatArray(q.size) { (p[it] + 2f * q[it] + r[it]) / 4f }
    return FrameResult(b.frame, b.meshes.indices.map { mix(a.meshes[it], b.meshes[it], c.meshes[it]) }, b.flipped)
}

private fun sameShape(a: List<FloatArray>, b: List<FloatArray>) = a.size == b.size && a.indices.all { a[it].size == b[it].size }

private fun lerp(p: FloatArray, q: FloatArray, t: Float) = FloatArray(p.size) { p[it] + (q[it] - p[it]) * t }
