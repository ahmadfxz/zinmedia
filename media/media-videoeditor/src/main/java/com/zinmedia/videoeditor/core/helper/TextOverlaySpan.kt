package com.zinmedia.videoeditor.core.helper

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import com.zinmedia.videoeditor.data.CustomTypefaceSpan
import com.zinmedia.videoeditor.data.LineSpacingSpan
import com.zinmedia.videoeditor.data.RoundedBackgroundSpan

//
//fun autoWrapText(
//    text: String,
//    paint: TextPaint,
//    maxWidthPx: Int
//): String {
//    val layout = StaticLayout.Builder
//        .obtain(text, 0, text.length, paint, maxWidthPx)
//        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
//        .build()
//
//    val sb = StringBuilder()
//    for (i in 0 until layout.lineCount) {
//        val start = layout.getLineStart(i)
//        val end = layout.getLineEnd(i)
//        sb.append(text.substring(start, end))
//
//        if (i != layout.lineCount - 1) sb.append("\n")
//    }
//    return sb.toString()
//}


private fun autoWrapText(
    text: String,
    paint: TextPaint,
    maxWidthPx: Int
): String {
    val layout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, maxWidthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .build()

    val sb = StringBuilder()

    for (i in 0 until layout.lineCount) {
        val start = layout.getLineStart(i)
        val end = layout.getLineEnd(i)
        val line = text.substring(start, end)

        sb.append(line)

        val endsWithNewline = line.endsWith("\n")

        // tambahkan newline hanya bila diperlukan
        if (!endsWithNewline && i != layout.lineCount - 1) {
            sb.append("\n")
        }
    }

    return sb.toString()
}

private fun newAutoWrapText(
    text: String,
    maxCharsPerLine: Int = 13
): String {

    val finalResult = StringBuilder()

    // Pisahkan berdasarkan ENTER asli
    val originalLines = text.split("\n")

    for ((i, originalLine) in originalLines.withIndex()) {

        val words = originalLine.split(" ")
        val currentLine = StringBuilder()

        for (word in words) {

            // Jika kata lebih panjang daripada batas → potong karakter
            if (word.length > maxCharsPerLine) {

                // Jika masih ada kata sebelumnya → flush dulu
                if (currentLine.isNotEmpty()) {
                    finalResult.append(currentLine.toString()).append("\n")
                    currentLine.clear()
                }

                // Potong kata panjang menjadi beberapa baris
                var temp = word
                while (temp.length > maxCharsPerLine) {
                    finalResult.append(temp.substring(0, maxCharsPerLine)).append("\n")
                    temp = temp.substring(maxCharsPerLine)
                }

                if (temp.isNotEmpty()) {
                    currentLine.append(temp)
                }

            } else {

                // Normal: wrap per kata
                if (currentLine.isEmpty()) {
                    currentLine.append(word)
                } else if (currentLine.length + 1 + word.length <= maxCharsPerLine) {
                    currentLine.append(" ").append(word)
                } else {
                    finalResult.append(currentLine.toString()).append("\n")
                    currentLine.clear()
                    currentLine.append(word)
                }
            }
        }

        // Tambahkan line terakhir jika ada
        if (currentLine.isNotEmpty()) {
            finalResult.append(currentLine.toString())
        }

        // Tambah ENTER jika bukan baris akhir
        if (i != originalLines.lastIndex) finalResult.append("\n")
    }

    return finalResult.toString()
}


fun createOverlaySpannable(
    text: String,
    textColor: Int,
    bgColor: Int,
    typeface: Typeface,
    maxCharsPerLine: Int = 20,
): SpannableString {
    val paint = TextPaint().apply {
        color = textColor
        textSize = 60f
        this.typeface = typeface
    }
  //  val wrappedText = autoWrapText(text, paint, 450)
    val wrappedText = newAutoWrapText(text, 13)

    // 2. Buat SpannableString
    return SpannableString(wrappedText).apply {

        setSpan(
            RoundedBackgroundSpan(bgColor, textColor, radius = 24f),
            0,
            wrappedText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Warna teks
//        setSpan(
//            ForegroundColorSpan(textColor),
//            0,
//            wrappedText.length,
//            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
//        )

        // Font custom
        setSpan(
            CustomTypefaceSpan(typeface),
            0,
            wrappedText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Background warna
//        setSpan(
//            BackgroundColorSpan(bgColor),
//            0,
//            wrappedText.length,
//            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
//        )

        setSpan(
            AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
            0,
            wrappedText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
//        setSpan(
//            AbsoluteSizeSpan(150, false),
//            0,
//            wrappedText.length,
//            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
//        )

//        setSpan(
//            LineSpacingSpan(20), // 20px ekstra spacing
//            0,
//            wrappedText.length,
//            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
//        )
    }
}