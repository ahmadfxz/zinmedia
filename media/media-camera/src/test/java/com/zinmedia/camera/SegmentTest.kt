package com.zinmedia.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentTest {

    @Test
    fun segment_outputDurationFollowsSpeed() {
        assertEquals(2000L, Segment(java.io.File("x"), speed = 2f, recordedMs = 4000).outputMs)
        assertEquals(10000L, Segment(java.io.File("x"), speed = 0.5f, recordedMs = 5000).outputMs)
    }
}
