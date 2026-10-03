package com.zinmedia.composer

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.zinmedia.photoeditor.DefaultEmojis
import com.zinmedia.photoeditor.PhotoEditorConfig
import com.zinmedia.photoeditor.PhotoFilterOption
import com.zinmedia.videoeditor.VideoEditorConfig
import com.zinmedia.videoeditor.VideoFilterOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MediaComposerTest {

    @After
    fun reset() {
        MediaComposer.configure(emojis = DefaultEmojis, photoFilters = PhotoFilterOption.Defaults)
    }

    @Test
    fun configure_setsPhotoAndVideoContent() {
        val filter = VideoFilterOption("Vintage", "https://contoh/a.cube", "https://contoh/a.jpg")
        MediaComposer.configure(stickers = listOf("s1", "s2"), emojis = listOf("😀"), videoFilters = listOf(filter))

        assertEquals(listOf("s1", "s2"), PhotoEditorConfig.stickers)
        assertEquals(listOf("s1", "s2"), VideoEditorConfig.stickers)
        assertEquals(listOf("😀"), PhotoEditorConfig.emojis)
        assertEquals(listOf("😀"), VideoEditorConfig.emojis)
        assertEquals(listOf(filter), VideoEditorConfig.filters)
    }

    @Test
    fun mediaTypeOf_detectsFileExtensions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(MediaType.Image, context.mediaTypeOf(Uri.fromFile(File("/tmp/a.jpg"))))
        assertEquals(MediaType.Video, context.mediaTypeOf(Uri.fromFile(File("/tmp/a.mp4"))))
        assertNull(context.mediaTypeOf(Uri.fromFile(File("/tmp/a.txt"))))
    }
}
