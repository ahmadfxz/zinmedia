package com.jernih.videoeditor.data

import android.graphics.Paint
import android.text.style.LineHeightSpan


class LineSpacingSpan(private val extra: Int) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt
    ) {
        fm.descent += extra
        fm.bottom += extra
    }
}