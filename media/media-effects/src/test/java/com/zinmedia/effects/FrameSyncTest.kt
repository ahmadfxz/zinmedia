package com.zinmedia.effects

import com.zinmedia.effects.gl.FrameResult
import com.zinmedia.effects.gl.FrameSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameSyncTest {

    private fun result(frame: Long, x: Float) = FrameResult(frame, listOf(floatArrayOf(x, 0f)), false)

    @Test
    fun ownResult_isUsedExactly() {
        val sync = FrameSync(5)
        sync.onFrame(10)
        sync.add(result(10, 0.3f))
        assertEquals(0.3f, sync.resultFor(10)!!.meshes[0][0], 0f)
    }

    @Test
    fun skippedFrame_isInterpolatedBetweenNeighbours() {
        val sync = FrameSync(5)
        sync.onFrame(12)
        sync.add(result(10, 0.2f))
        sync.add(result(12, 0.6f))
        assertEquals(0.4f, sync.resultFor(11)!!.meshes[0][0], 1e-6f)
    }

    @Test
    fun noLaterResult_holdsLastOne_andNothingBeforeFirst() {
        val sync = FrameSync(5)
        sync.onFrame(10)
        assertNull(sync.resultFor(10))
        sync.add(result(10, 0.5f))
        assertEquals(0.5f, sync.resultFor(13)!!.meshes[0][0], 0f)
    }

    @Test
    fun outputFrame_neverBeforeStartOrBeyondMaxDelay() {
        val sync = FrameSync(3)
        sync.onFrame(100)
        assertEquals(100L, sync.outputFrame(100))
        sync.onFrame(101)
        assertEquals(100L, sync.outputFrame(101))
    }

    @Test
    fun slowDetection_raisesDelay_gradually() {
        val sync = FrameSync(5)
        // Tiap frame dideteksi, hasil tiba 3 frame kemudian.
        for (frame in 1L..200L) {
            sync.onFrame(frame)
            if (frame > 3) sync.add(result(frame - 3, 0f))
        }
        // Latensi 4 + jendela peredam 1.
        assertEquals(5, sync.delay)
        assertEquals(195L, sync.outputFrame(200))
    }

    @Test
    fun outputNeverPassesNewestResult_andNeverGoesBack() {
        val sync = FrameSync(12)
        sync.onFrame(1)
        sync.add(result(1, 0f))
        var last = 0L
        // Deteksi macet setelah frame 1: video menunggu, tidak berjalan tanpa hasil.
        for (frame in 2L..10L) {
            sync.onFrame(frame)
            val out = sync.outputFrame(frame)
            assert(out <= 1L - sync.window || out == 1L) { "frame $out tampil tanpa hasil" }
            assert(out >= last)
            last = out
        }
    }

    @Test
    fun jitter_isAveragedSymmetrically_withoutLagOnSteadyMotion() {
        val sync = FrameSync(12)
        sync.onFrame(20)
        // Gerak lurus x = frame/100: rata-rata simetris tidak menggeser posisi.
        for (frame in 0L..20L) sync.add(result(frame, frame / 100f))
        assertEquals(0.10f, sync.resultFor(10)!!.meshes[0][0], 1e-6f)
    }

    @Test
    fun faceCountChange_usesNearestResult() {
        val sync = FrameSync(5)
        sync.onFrame(12)
        sync.add(result(10, 0.2f))
        sync.add(FrameResult(12, emptyList(), false))
        assertEquals(1, sync.resultFor(10)!!.meshes.size)
        assertEquals(0, sync.resultFor(12)!!.meshes.size)
        assertEquals(0, sync.resultFor(11)!!.meshes.size)
    }
}
