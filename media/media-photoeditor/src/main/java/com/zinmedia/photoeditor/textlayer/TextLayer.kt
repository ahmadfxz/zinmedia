package com.zinmedia.photoeditor.textlayer

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Perataan teks. */
internal enum class TextLayerAlign { Left, Center, Right }

/** Gaya latar teks: polos, latar penuh, atau latar transparan. */
internal enum class TextLayerBackground { None, Solid, Translucent }

/**
 * Satu lapisan teks. Sumber tunggal untuk tampilan di mode teks dan hasil akhirnya,
 * sehingga keduanya selalu sama.
 *
 * @param color warna pilihan slider: warna teks bila tanpa latar, warna latar bila berlatar.
 */
internal data class TextLayer(
    val text: String,
    val color: Int = AndroidColor.WHITE,
    val background: TextLayerBackground = TextLayerBackground.None,
    val align: TextLayerAlign = TextLayerAlign.Center,
    val fontIndex: Int = 0,
) {
    /** Warna huruf: kontras otomatis bila berlatar penuh, putih bila latar transparan. */
    val textColor: Int
        get() = when (background) {
            TextLayerBackground.None -> color
            TextLayerBackground.Solid -> if (Color(color).luminance() > 0.5f) AndroidColor.BLACK else AndroidColor.WHITE
            TextLayerBackground.Translucent -> AndroidColor.WHITE
        }

    /** Warna latar per baris, atau `null` bila tanpa latar. */
    val backgroundColor: Int?
        get() = when (background) {
            TextLayerBackground.None -> null
            TextLayerBackground.Solid -> color
            TextLayerBackground.Translucent -> Color(color).copy(alpha = 0.6f).toArgb()
        }
}

/** Ukuran & jarak teks; dipakai bersama oleh mode teks dan renderer hasil. */
internal object TextLayerMetrics {
    val FontSize = 30.sp
    val PaddingHorizontal = 10.dp
    /** Padding atas (dari puncak huruf) & bawah (dari dasar ekor huruf). */
    val PaddingVertical = 4.dp
    /** Celah antar kotak latar baris. */
    val LineGap = 3.dp
    val CornerRadius = 8.dp
}
