package com.zinmedia.videoeditor.overlays

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.cos
import kotlin.math.sin

@OptIn(UnstableApi::class)
class ClockOverlay : CanvasOverlay(/* useInputFrameSize = */ false) {

    companion object {
        private const val CLOCK_COLOR = Color.WHITE

        private const val DIAL_SIZE = 200
        private const val DIAL_WIDTH = 3f
        private const val NEEDLE_WIDTH = 3f
        private const val NEEDLE_LENGTH = DIAL_SIZE / 2 - 20

        private const val CENTRE_X = DIAL_SIZE / 2
        private const val CENTRE_Y = DIAL_SIZE / 2

        private const val DIAL_INSET = 5
        private val DIAL_BOUND = RectF(
            DIAL_INSET.toFloat(),
            DIAL_INSET.toFloat(),
            (DIAL_SIZE - DIAL_INSET).toFloat(),
            (DIAL_SIZE - DIAL_INSET).toFloat()
        )

        private const val HUB_SIZE = 5f

        // Anchor for bottom-right placement
        private const val BOTTOM_RIGHT_ANCHOR_X = 1f
        private const val BOTTOM_RIGHT_ANCHOR_Y = -1f
        private const val ANCHOR_INSET_X = 0.1f
        private const val ANCHOR_INSET_Y = -0.1f
    }

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = DIAL_WIDTH
        color = CLOCK_COLOR
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = NEEDLE_WIDTH
        color = CLOCK_COLOR
    }

    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CLOCK_COLOR
    }

    init {
        setCanvasSize(DIAL_SIZE, DIAL_SIZE)
    }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        // Clear canvas
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Draw dial circle
        canvas.drawArc(
            DIAL_BOUND,
            0f,
            360f,
            false,
            dialPaint
        )

        // Compute needle angle (6 degrees per second)
        val angle = 6f * (presentationTimeUs / 1_000_000f) - 90f
        val radians = angle.toRadians()

        val startX = CENTRE_X - (10 * cos(radians)).toFloat()
        val startY = CENTRE_Y - (10 * sin(radians)).toFloat()
        val endX = CENTRE_X + (NEEDLE_LENGTH * cos(radians)).toFloat()
        val endY = CENTRE_Y + (NEEDLE_LENGTH * sin(radians)).toFloat()

        // Draw needle
        canvas.drawLine(startX, startY, endX, endY, needlePaint)

        // Draw hub
        canvas.drawCircle(CENTRE_X.toFloat(), CENTRE_Y.toFloat(), HUB_SIZE, hubPaint)
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(
                BOTTOM_RIGHT_ANCHOR_X - ANCHOR_INSET_X,
                BOTTOM_RIGHT_ANCHOR_Y - ANCHOR_INSET_Y
            )
            .setOverlayFrameAnchor(
                BOTTOM_RIGHT_ANCHOR_X,
                BOTTOM_RIGHT_ANCHOR_Y
            )
            .build()
    }

    private fun Float.toRadians(): Double = Math.toRadians(this.toDouble())
}
