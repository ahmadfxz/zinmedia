package com.zinmedia.videoeditor

import com.zinmedia.videoeditor.data.repository.CanvasRect
import com.zinmedia.videoeditor.data.repository.OverlayBox
import com.zinmedia.videoeditor.data.repository.exportCanvas
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportCanvasTest {

    private val portrait = 16f / 9f
    private val screen = 1.2f to 2.4f

    @Test
    fun overlayInsideFrame_keepsFrame() {
        val box = OverlayBox(anchorX = 0f, anchorY = 0f, widthFraction = 0.5f, aspect = 0.5f, rotationDegrees = 0f)
        assertEquals(CanvasRect(0f, 0f, 1f, portrait), exportCanvas(listOf(box), portrait, screen))
    }

    @Test
    fun overlayAboveFrame_extendsTopOnly() {
        // Pusat tepat di tepi atas, tinggi 0.2 -> menonjol 0.1 di atas frame.
        val box = OverlayBox(anchorX = 0f, anchorY = 1f, widthFraction = 0.4f, aspect = 0.5f, rotationDegrees = 0f)
        val canvas = exportCanvas(listOf(box), portrait, screen)
        assertEquals(-0.1f, canvas.top, 1e-4f)
        assertEquals(portrait, canvas.bottom, 1e-4f)
        assertEquals(0f, canvas.left, 1e-4f)
        assertEquals(1f, canvas.right, 1e-4f)
    }

    @Test
    fun rotatedOverlay_usesRotatedBounds() {
        // Kotak 0.4 x 0.2 diputar 90° -> 0.2 x 0.4; pusat di tepi kanan.
        val box = OverlayBox(anchorX = 1f, anchorY = 0f, widthFraction = 0.4f, aspect = 0.5f, rotationDegrees = 90f)
        assertEquals(1.1f, exportCanvas(listOf(box), portrait, screen).right, 1e-4f)
    }

    @Test
    fun farAwayOverlay_isLimitedToEditorArea() {
        val box = OverlayBox(anchorX = 10f, anchorY = 0f, widthFraction = 0.4f, aspect = 0.5f, rotationDegrees = 0f)
        assertEquals(1.1f, exportCanvas(listOf(box), portrait, screen).right, 1e-4f)
    }

    @Test
    fun noLimit_keepsFrame() {
        val box = OverlayBox(anchorX = 0f, anchorY = 1f, widthFraction = 0.4f, aspect = 0.5f, rotationDegrees = 0f)
        assertEquals(CanvasRect(0f, 0f, 1f, portrait), exportCanvas(listOf(box), portrait, null))
    }

    @Test
    fun toNdc_mapsCanvasEdges() {
        val canvas = CanvasRect(0f, -0.5f, 1f, 1.5f)
        assertEquals(-1f to 1f, canvas.toNdc(0f, -0.5f))
        assertEquals(1f to -1f, canvas.toNdc(1f, 1.5f))
    }
}
