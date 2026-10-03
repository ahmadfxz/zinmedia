package com.zinmedia.videoeditor

import com.zinmedia.videoeditor.data.repository.CanvasRect
import com.zinmedia.videoeditor.data.repository.OverlayBox
import com.zinmedia.videoeditor.data.repository.exportCanvas
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportCanvasTest {

    private val portrait = 16f / 9f
    /** Area editor: 1.2 × 2.4 lebar video, berpusat di tengah video. */
    private val screen = 1.2f to 2.4f
    private val fullArea = CanvasRect(-0.1f, portrait / 2f - 1.2f, 1.1f, portrait / 2f + 1.2f)

    private fun assertRect(expected: CanvasRect, actual: CanvasRect) {
        assertEquals(expected.left, actual.left, 1e-4f)
        assertEquals(expected.top, actual.top, 1e-4f)
        assertEquals(expected.right, actual.right, 1e-4f)
        assertEquals(expected.bottom, actual.bottom, 1e-4f)
    }

    @Test
    fun overlayInsideFrame_keepsFrame() {
        val box = OverlayBox(anchorX = 0f, anchorY = 0f, widthFraction = 0.5f, aspect = 0.5f, rotationDegrees = 0f)
        assertRect(CanvasRect(0f, 0f, 1f, portrait), exportCanvas(listOf(box), portrait, screen))
    }

    @Test
    fun overlayTouchingEdge_keepsFrame() {
        // Tepat menempel tepi kanan (pembulatan float tidak dianggap keluar).
        val box = OverlayBox(anchorX = 0.6f, anchorY = 0f, widthFraction = 0.2f, aspect = 1f, rotationDegrees = 0f)
        assertRect(CanvasRect(0f, 0f, 1f, portrait), exportCanvas(listOf(box), portrait, screen))
    }

    @Test
    fun overlayOutsideFrame_fillsWholeEditorArea() {
        // Menonjol sedikit di atas frame -> kanvas penuh seluas area editor.
        val box = OverlayBox(anchorX = 0f, anchorY = 1f, widthFraction = 0.4f, aspect = 0.5f, rotationDegrees = 0f)
        assertRect(fullArea, exportCanvas(listOf(box), portrait, screen))
    }

    @Test
    fun rotatedOverlayOutside_fillsWholeEditorArea() {
        val box = OverlayBox(anchorX = 1f, anchorY = 0f, widthFraction = 0.4f, aspect = 0.5f, rotationDegrees = 90f)
        assertRect(fullArea, exportCanvas(listOf(box), portrait, screen))
    }

    @Test
    fun editorAreaSmallerThanFrame_neverCropsVideo() {
        val box = OverlayBox(anchorX = 0f, anchorY = 1f, widthFraction = 0.4f, aspect = 0.5f, rotationDegrees = 0f)
        assertRect(CanvasRect(0f, 0f, 1f, portrait), exportCanvas(listOf(box), portrait, 1f to portrait))
    }

    @Test
    fun noLimit_keepsFrame() {
        val box = OverlayBox(anchorX = 0f, anchorY = 1f, widthFraction = 0.4f, aspect = 0.5f, rotationDegrees = 0f)
        assertRect(CanvasRect(0f, 0f, 1f, portrait), exportCanvas(listOf(box), portrait, null))
    }

    @Test
    fun toNdc_mapsCanvasEdges() {
        val canvas = CanvasRect(0f, -0.5f, 1f, 1.5f)
        assertEquals(-1f to 1f, canvas.toNdc(0f, -0.5f))
        assertEquals(1f to -1f, canvas.toNdc(1f, 1.5f))
    }

    @Test
    fun placeOverlay_insideCanvas_usesCenter() {
        val p = com.zinmedia.videoeditor.data.repository.placeOverlay(0.2f, -0.5f, 50f, 20f, 0f, 720, 1280)!!
        assertEquals(0.2f, p.backgroundX, 1e-5f)
        assertEquals(-0.5f, p.backgroundY, 1e-5f)
        assertEquals(0f, p.overlayX, 1e-5f)
        assertEquals(0f, p.overlayY, 1e-5f)
    }

    @Test
    fun placeOverlay_centerBelowCanvas_clampsAndShiftsOverlayAnchor() {
        // Pusat 10 px di bawah tepi bawah (1280 px tinggi -> 1 NDC = 640 px); tinggi overlay 40 px.
        val p = com.zinmedia.videoeditor.data.repository.placeOverlay(0f, -1f - 10f / 640f, 100f, 20f, 0f, 720, 1280)!!
        assertEquals(-1f, p.backgroundY, 1e-5f)
        // Titik tempel overlay 10/20 = 0.5 ke atas dari pusat overlay.
        assertEquals(0.5f, p.overlayY, 1e-4f)
        assertEquals(0f, p.overlayX, 1e-4f)
    }

    @Test
    fun placeOverlay_rotated90_swapsAxes() {
        // Diputar 90°: geser vertikal di kanvas = geser horizontal di overlay.
        val p = com.zinmedia.videoeditor.data.repository.placeOverlay(0f, -1f - 10f / 640f, 100f, 20f, 90f, 720, 1280)!!
        assertEquals(-1f, p.backgroundY, 1e-5f)
        assertEquals(0.1f, kotlin.math.abs(p.overlayX), 1e-4f)
        assertEquals(0f, p.overlayY, 1e-4f)
    }

    @Test
    fun placeOverlay_fullyOutside_isSkipped() {
        assertEquals(null, com.zinmedia.videoeditor.data.repository.placeOverlay(0f, -1.5f, 50f, 20f, 0f, 720, 1280))
    }

    /** Area editor 9:16 (1080×1920 px) dengan video selebar/setinggi [videoWidthPx] di dalamnya. */
    private fun limitFor(videoWidthPx: Float) = 1080f / videoWidthPx to 1920f / videoWidthPx

    @Test
    fun tallVideo_withOverflow_becomes9by16() {
        // Video 360×1440 (1:4) tampil 480×1920 di area editor; teks keluar di kanan.
        val heightRatio = 4f
        val box = OverlayBox(anchorX = 1f, anchorY = 0f, widthFraction = 0.5f, aspect = 0.3f, rotationDegrees = 0f)
        val canvas = exportCanvas(listOf(box), heightRatio, limitFor(480f))
        assertEquals(9f / 16f, canvas.width / canvas.height, 1e-4f)
        // Video tetap utuh di dalam kanvas.
        assertEquals(true, canvas.left <= 0f && canvas.right >= 1f && canvas.top <= 0f && canvas.bottom >= heightRatio)
        val (w, h) = com.zinmedia.videoeditor.data.repository.fitExportSize(
            (canvas.width * 360).toInt(), (canvas.height * 360).toInt(),
        )
        assertEquals(720 to 1280, w to h)
    }

    @Test
    fun wideVideo_withOverflow_becomes9by16() {
        // Video 1440×360 (4:1) tampil 1080×270; teks keluar di atas.
        val heightRatio = 0.25f
        val box = OverlayBox(anchorX = 0f, anchorY = 1f, widthFraction = 0.3f, aspect = 0.5f, rotationDegrees = 0f)
        val canvas = exportCanvas(listOf(box), heightRatio, limitFor(1080f))
        assertEquals(9f / 16f, canvas.width / canvas.height, 1e-4f)
        assertEquals(true, canvas.left <= 0f && canvas.right >= 1f && canvas.top <= 0f && canvas.bottom >= heightRatio)
        val (w, h) = com.zinmedia.videoeditor.data.repository.fitExportSize(
            (canvas.width * 1440).toInt(), (canvas.height * 1440).toInt(),
        )
        assertEquals(720 to 1280, w to h)
    }

    @Test
    fun squareVideo_withoutOverflow_keepsSquare() {
        val box = OverlayBox(anchorX = 0f, anchorY = 0f, widthFraction = 0.3f, aspect = 0.5f, rotationDegrees = 45f)
        val canvas = exportCanvas(listOf(box), 1f, limitFor(1080f))
        assertRect(CanvasRect(0f, 0f, 1f, 1f), canvas)
    }

    @Test
    fun overlayFarOutside_isSkippedNotCrashing() {
        // Pusat jauh di luar kanvas 9:16 -> overlay dilewati (bukan error Media3).
        val placement = com.zinmedia.videoeditor.data.repository.placeOverlay(
            centerX = 3f, centerY = 0f, halfWidthPx = 40f, halfHeightPx = 20f,
            rotationDegrees = 0f, outputWidth = 720, outputHeight = 1280,
        )
        assertEquals(null, placement)
    }
}
