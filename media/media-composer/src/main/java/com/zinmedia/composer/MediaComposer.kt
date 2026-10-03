package com.zinmedia.composer

import com.zinmedia.photoeditor.PhotoEditorConfig
import com.zinmedia.photoeditor.PhotoFilterOption
import com.zinmedia.videoeditor.VideoEditorConfig
import com.zinmedia.videoeditor.VideoFilterOption

/**
 * Atur konten editor foto & video sekaligus. Panggil sekali saat aplikasi mulai
 * (mis. di `Application.onCreate()`).
 *
 * ```kotlin
 * MediaComposer.configure(
 *     stickers = listOf("https://cdn.contoh.com/stiker/1.png"),
 *     videoFilters = listOf(VideoFilterOption("Vintage", "https://…/a.cube", "https://…/a.jpg")),
 * )
 * ```
 */
public object MediaComposer {

    /**
     * @param stickers stiker untuk foto & video. Kosong = tab Stiker disembunyikan.
     * @param emojis emoji tray; `null` = pakai daftar bawaan.
     * @param photoFilters filter foto bawaan yang ditampilkan; `null` = semua.
     * @param videoFilters filter LUT video. Kosong = filter video disembunyikan.
     */
    public fun configure(
        stickers: List<String> = emptyList(),
        emojis: List<String>? = null,
        photoFilters: List<PhotoFilterOption>? = null,
        videoFilters: List<VideoFilterOption> = emptyList(),
    ) {
        PhotoEditorConfig.stickers = stickers
        VideoEditorConfig.stickers = stickers
        if (emojis != null) {
            PhotoEditorConfig.emojis = emojis
            VideoEditorConfig.emojis = emojis
        }
        if (photoFilters != null) PhotoEditorConfig.filters = photoFilters
        VideoEditorConfig.filters = videoFilters
    }
}
