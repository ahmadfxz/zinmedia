package com.zinmedia.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeautyParamsTest {

    @Test
    fun values_clampedToFeatureRange() {
        val beauty = BeautyParams.of(BeautyFeature.Smooth to 2f, BeautyFeature.Chin to -3f, BeautyFeature.Nose to -1f)
        assertEquals(1f, beauty[BeautyFeature.Smooth])
        assertEquals(-1f, beauty[BeautyFeature.Chin])
        assertEquals(0f, beauty[BeautyFeature.Nose])
    }

    @Test
    fun enabled_andReshapes() {
        assertFalse(BeautyParams.None.enabled)
        assertTrue(BeautyParams.of(BeautyFeature.Lipstick to 0.1f).enabled)
        assertFalse(BeautyParams.of(BeautyFeature.Lipstick to 0.1f).reshapes)
        assertTrue(BeautyParams.of(BeautyFeature.EyeAngle to -0.1f).reshapes)
        assertFalse(BeautyParams.of(BeautyFeature.BrightenEyes to 0.5f).reshapes)
    }

    @Test
    fun map_roundTrip_andUnknownKeysIgnored() {
        val beauty = BeautyParams.of(BeautyFeature.Smooth to 0.4f, BeautyFeature.Chin to -0.2f)
        assertEquals(mapOf("smooth" to 0.4f, "chin" to -0.2f), beauty.toMap())
        assertEquals(beauty, BeautyParams.fromMap(beauty.toMap()))
        assertEquals(BeautyParams.of(BeautyFeature.Smooth to 0.5f), BeautyParams.fromMap(mapOf("smooth" to 0.5, "sparkle" to 1)))
    }

    @Test
    fun lipFinish_keptAcrossEdits_andCompared() {
        val gloss = BeautyParams.of(BeautyFeature.Lipstick to 0.4f).withLipFinish(LipFinish.Gloss)
        assertEquals(LipFinish.Satin, BeautyParams.None.lipFinish)
        assertEquals(LipFinish.Gloss, gloss.with(BeautyFeature.Smooth, 0.2f).withLipColor(0xFFE53935.toInt()).lipFinish)
        assertFalse(gloss == gloss.withLipFinish(LipFinish.Matte))
        assertEquals(gloss, BeautyParams.fromMap(gloss.toMap(), lipFinish = LipFinish.Gloss))
        assertEquals(LipFinish.Matte, LipFinish.fromId("matte"))
    }

    @Test
    fun catalog_idsUnique_andPresetsValid() {
        assertEquals(BeautyFeature.entries.size, BeautyFeature.entries.map { it.id }.toSet().size)
        assertTrue(DefaultBeautyPresets.all { it.params.enabled })
    }
}
