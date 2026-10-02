package com.zinmedia.videoeditor.data

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan


class RoundedBackgroundSpan(
    private val bgColor: Int,
    private val textColor: Int,
    private val radius: Float = 20f,
    private val paddingH: Float = 20f,   // horizontal padding
    private val paddingV: Float = 10f    // vertical padding
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val textWidth = paint.measureText(text, start, end)
        return (textWidth + paddingH * 2).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val textWidth = paint.measureText(text, start, end)
        val fm = paint.fontMetrics

        val rect = RectF(
            x - paddingH,                // FIX → background ikut padding kiri
            y + fm.top,
            x + textWidth + paddingH,    // kanan tetap normal
            y + fm.bottom
        )

        // Background
        val bg = Paint(paint)
        bg.color = bgColor
        canvas.drawRoundRect(rect, radius, radius, bg)

        // Text
        val txt = Paint(paint)
        txt.color = textColor
        canvas.drawText(text, start, end, x, y.toFloat(), txt)
    }
}