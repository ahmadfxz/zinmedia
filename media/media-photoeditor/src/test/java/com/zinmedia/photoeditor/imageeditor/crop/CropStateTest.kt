package com.zinmedia.photoeditor.imageeditor.crop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class CropStateTest {

    private val sample = Rect(0.1f, 0.2f, 0.5f, 0.9f)

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1e-5f)
        assertEquals(expected.top, actual.top, 1e-5f)
        assertEquals(expected.right, actual.right, 1e-5f)
        assertEquals(expected.bottom, actual.bottom, 1e-5f)
    }

    @Test
    fun rotateCcw_movesRightEdgeToTop() {
        // Sisi kanan gambar (x=1) menjadi sisi atas setelah diputar berlawanan jarum jam.
        assertRect(Rect(0f, 0f, 1f, 0.25f), Rect(0.75f, 0f, 1f, 1f).rotatedCcw())
    }

    @Test
    fun rotateCcw_fourTimes_isIdentity() {
        assertRect(sample, sample.rotatedCcw().rotatedCcw().rotatedCcw().rotatedCcw())
    }

    @Test
    fun flip_twice_isIdentity() {
        assertRect(Rect(0.5f, 0.2f, 0.9f, 0.9f), sample.flippedHorizontally())
        assertRect(sample, sample.flippedHorizontally().flippedHorizontally())
    }

    @Test
    fun largestRectWithRatio_fitsInsideBounds() {
        val square = largestRectWithRatio(Rect(0f, 0f, 200f, 100f), 1f, Offset(190f, 50f))
        assertRect(Rect(100f, 0f, 200f, 100f), square)

        val wide = largestRectWithRatio(Rect(0f, 0f, 100f, 100f), 2f, Offset(50f, 50f))
        assertRect(Rect(0f, 25f, 100f, 75f), wide)
    }

    @Test
    fun aspect_rotated_swapsOrientation() {
        assertEquals(CropAspect.Landscape4x3, CropAspect.Portrait3x4.rotated())
        assertEquals(CropAspect.Story, CropAspect.Wide.rotated())
        assertEquals(CropAspect.Square, CropAspect.Square.rotated())
        assertEquals(1.5f, CropAspect.Original.ratioFor(300f, 200f)!!, 1e-5f)
    }
}
