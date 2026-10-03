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
        MediaComposer.configure(
            emojis = DefaultEmojis,
            photoFilters = PhotoFilterOption.Defaults,
            accentColor = PhotoEditorConfig.DEFAULT_ACCENT_COLOR,
        )
    }

    @Test
    fun configure_accentColor_appliesToPhotoAndVideo() {
        MediaComposer.configure(accentColor = 0xFF1E88E5.toInt())
        assertEquals(0xFF1E88E5.toInt(), PhotoEditorConfig.accentColor)
        assertEquals(0xFF1E88E5.toInt(), VideoEditorConfig.accentColor)

        // null = tidak diubah.
        MediaComposer.configure(stickers = listOf("s"))
        assertEquals(0xFF1E88E5.toInt(), PhotoEditorConfig.accentColor)
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

    @Test
    fun intent_carriesMediaAndClampedLimit() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val uris = listOf(Uri.fromFile(File("/tmp/a.jpg")), Uri.fromFile(File("/tmp/b.mp4")))

        val single = MediaComposerActivity.intent(context, uris.take(1), maxItems = 1, recipientLabel = "Status (Kontak)")
        assertEquals(1, single.getIntExtra(MediaComposerActivity.EXTRA_MAX_ITEMS, -1))
        assertEquals("Status (Kontak)", single.getStringExtra(MediaComposerActivity.EXTRA_RECIPIENT_LABEL))

        val list = MediaComposerActivity.intent(context, uris, maxItems = 99)
        assertEquals(MediaComposerActivity.MAX_ITEMS, list.getIntExtra(MediaComposerActivity.EXTRA_MAX_ITEMS, -1))
        @Suppress("DEPRECATION")
        assertEquals(uris, list.getParcelableArrayListExtra<Uri>(MediaComposerActivity.EXTRA_MEDIA_URIS))
        assertNull(list.getStringExtra(MediaComposerActivity.EXTRA_RECIPIENT_LABEL))
        assertEquals(true, list.getBooleanExtra(MediaComposerActivity.EXTRA_SHOW_CAPTION, false))

        val noCaption = MediaComposerActivity.intent(context, uris, showCaption = false)
        assertEquals(false, noCaption.getBooleanExtra(MediaComposerActivity.EXTRA_SHOW_CAPTION, true))
    }

    @Test
    fun allowedMedia_defaultsToAllAndFiltersTypes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val uris = listOf(Uri.fromFile(File("/tmp/a.jpg")))
        assertEquals(AllowedMedia.All, AllowedMedia.from(MediaComposerActivity.intent(context, uris)))
        assertEquals(AllowedMedia.All, AllowedMedia.from(android.content.Intent()))
        assertEquals(
            AllowedMedia.Video,
            AllowedMedia.from(MediaComposerActivity.intent(context, uris, allowedMedia = AllowedMedia.Video)),
        )

        assertEquals(true, AllowedMedia.Video.accepts(MediaType.Video))
        assertEquals(false, AllowedMedia.Video.accepts(MediaType.Image))
        assertEquals(false, AllowedMedia.Image.accepts(MediaType.Video))
        assertEquals(true, AllowedMedia.All.accepts(MediaType.Image))
    }
}
