package com.zinmedia.photoeditor.textlayer

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextLayerTest {

    @Test
    fun noBackground_usesChosenColorForText() {
        val layer = TextLayer("a", color = Color.RED, background = TextLayerBackground.None)
        assertEquals(Color.RED, layer.textColor)
        assertNull(layer.backgroundColor)
    }

    @Test
    fun solidBackground_usesChosenColorForBox_andContrastingText() {
        val light = TextLayer("a", color = Color.WHITE, background = TextLayerBackground.Solid)
        assertEquals(Color.WHITE, light.backgroundColor)
        assertEquals(Color.BLACK, light.textColor)

        val dark = TextLayer("a", color = Color.BLUE, background = TextLayerBackground.Solid)
        assertEquals(Color.BLUE, dark.backgroundColor)
        assertEquals(Color.WHITE, dark.textColor)
    }

    @Test
    fun translucentBackground_isSemiTransparent_withWhiteText() {
        val layer = TextLayer("a", color = Color.RED, background = TextLayerBackground.Translucent)
        assertEquals(Color.WHITE, layer.textColor)
        assertEquals(153, layer.backgroundColor!! ushr 24)
    }
}
