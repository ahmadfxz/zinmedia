package com.zinmedia.videoeditor

import com.zinmedia.videoeditor.data.repository.fitExportSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportSizeTest {

    @Test
    fun portraitPhoneVideo_fitsIn720x1280() {
        // Video kamera HP 1080x1920 (setelah rotasi diperhitungkan).
        assertEquals(720 to 1280, fitExportSize(1080, 1920))
    }

    @Test
    fun landscapeVideo_keepsLandscapeOrientation() {
        assertEquals(1280 to 720, fitExportSize(1920, 1080))
    }

    @Test
    fun smallVideo_isNotUpscaled() {
        assertEquals(480 to 640, fitExportSize(480, 640))
    }

    @Test
    fun size_isRoundedDownToMultipleOf16() {
        // Kanvas yang diperluas: 720x1328 -> diperkecil ke tinggi 1280, lebar 693 -> 688.
        val (w, h) = fitExportSize(720, 1328)
        assertEquals(688 to 1280, w to h)
        assertEquals(0, fitExportSize(721, 1281).first % 16)
    }

    @Test
    fun unknownSize_fallsBackToPortraitDefault() {
        assertEquals(720 to 1280, fitExportSize(0, 0))
    }
}
