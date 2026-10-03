package com.zinmedia.videoeditor.data

import android.content.Context
import android.graphics.Typeface

/**
 * Font untuk mode teks, dimuat dari aset aplikasi (lihat README). Dimuat sekali per proses lalu
 * dipakai bersama, karena membaca file font cukup mahal (mis. saat banyak foto dibuka sekaligus).
 */
internal class FontManager(context: Context) {

    internal val fonts: List<FontItem> = loadFonts(context.applicationContext)

    internal fun getFont(fontId: Int): Typeface =
        fonts.firstOrNull { it.id == fontId }?.typeface ?: Typeface.DEFAULT

    private companion object {
        @Volatile
        private var cached: List<FontItem>? = null

        fun loadFonts(context: Context): List<FontItem> =
            cached ?: synchronized(this) {
                cached ?: listOf(
                    FontItem(0, "Inter", context.loadFont("inter_18pt_bold.ttf") ?: Typeface.DEFAULT_BOLD),
                    FontItem(1, "Monofett", context.loadFont("monofett_regular.ttf") ?: Typeface.DEFAULT),
                    FontItem(2, "Caveat", context.loadFont("caveat_regular.ttf") ?: Typeface.SERIF),
                    FontItem(3, "Pacifico", context.loadFont("pacifico_regular.ttf") ?: Typeface.MONOSPACE),
                    FontItem(4, "Rampartone", context.loadFont("rampartone_regular.ttf") ?: Typeface.DEFAULT),
                    FontItem(5, "Karla", context.loadFont("karla_bold.ttf") ?: Typeface.DEFAULT),
                ).also { cached = it }
            }

        private fun Context.loadFont(fileName: String): Typeface? =
            try {
                Typeface.createFromAsset(assets, fileName)
            } catch (e: Exception) {
                null
            }
    }
}
