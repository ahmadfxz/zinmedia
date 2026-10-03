package com.zinmedia.composer

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/** Jenis media yang boleh dipakai di editor gabungan & kamera. */
public enum class AllowedMedia {
    /** Foto dan video (default). */
    All,

    /** Foto saja. */
    Image,

    /** Video saja. */
    Video;

    internal fun accepts(type: MediaType): Boolean = when (this) {
        All -> true
        Image -> type == MediaType.Image
        Video -> type == MediaType.Video
    }

    /** Jenis untuk Photo Picker. */
    internal val pickerType: ActivityResultContracts.PickVisualMedia.VisualMediaType
        get() = when (this) {
            All -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
            Image -> ActivityResultContracts.PickVisualMedia.ImageOnly
            Video -> ActivityResultContracts.PickVisualMedia.VideoOnly
        }

    public companion object {
        /** Baca dari extra [MediaComposerActivity.EXTRA_ALLOWED_MEDIA]; tidak diisi = [All]. */
        @JvmStatic
        public fun from(intent: Intent): AllowedMedia =
            intent.getStringExtra(MediaComposerActivity.EXTRA_ALLOWED_MEDIA)
                ?.let { name -> entries.firstOrNull { it.name == name } }
                ?: All
    }
}
