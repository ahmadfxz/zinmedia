package com.zinmedia.videoeditor.data

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan


class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(ds: TextPaint) {
        ds.typeface = typeface
    }

    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = typeface
    }
}