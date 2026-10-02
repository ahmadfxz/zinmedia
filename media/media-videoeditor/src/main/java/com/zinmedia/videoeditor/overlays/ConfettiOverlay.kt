package com.zinmedia.videoeditor.overlays

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import androidx.annotation.Nullable
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.effect.CanvasOverlay
import kotlin.math.sin
import kotlin.random.Random

@UnstableApi
class ConfettiOverlay : CanvasOverlay(/* useInputFrameSize= */ true) {

    companion object {
        private val CONFETTI_TEXTS = listOf("❊", "✿", "❊", "✦︎", "♥︎", "☕︎")
        private const val EMITTER_POSITION_Y = -50
        private const val CONFETTI_BASE_SIZE = 30
        private const val CONFETTI_SIZE_VARIATION = 10
    }

    private val confettiList = mutableListOf<Confetti>()
    private val random = Random(System.currentTimeMillis())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Util.getCurrentOrMainLooper())

    @Nullable
    private var runnable: Runnable? = null

    private var width: Int = 0
    private var height: Int = 0
    private var started = false

    override fun configure(videoSize: Size) {
        super.configure(videoSize)
        width = videoSize.width
        height = videoSize.height
    }

    @Synchronized
    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (!started) start()

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val iterator = confettiList.iterator()
        while (iterator.hasNext()) {
            val c = iterator.next()

            if (c.y > height / 2f || c.x <= 0 || c.x > width) {
                iterator.remove()
                continue
            }

            c.draw(canvas, paint)
            c.update()
        }
    }

    /** Starts emitting confetti */
    fun start() {
        runnable = Runnable { addConfetti() }
        handler.post(runnable!!)
        started = true
    }

    /** Stops confetti emission */
    fun stop() {
        val r = runnable ?: return
        handler.removeCallbacks(r)
        confettiList.clear()
        runnable = null
        started = false
    }

    override fun release() {
        super.release()
        handler.post { stop() }
    }

    @Synchronized
    private fun addConfetti() {
        repeat(5) {
            confettiList.add(
                Confetti(
                    text = CONFETTI_TEXTS[random.nextInt(CONFETTI_TEXTS.size)],
                    random = random,
                    x = width / 2f,
                    y = EMITTER_POSITION_Y.toFloat(),
                    size = CONFETTI_BASE_SIZE + random.nextInt(CONFETTI_SIZE_VARIATION),
                    color = Color.HSVToColor(
                        floatArrayOf(
                            random.nextInt(360).toFloat(), 0.6f, 0.8f
                        )
                    )
                )
            )
        }

        handler.postDelayed({ addConfetti() }, 100)
    }

    private class Confetti(
        private val text: String,
        random: Random,
        var x: Float,
        var y: Float,
        private val size: Int,
        private val color: Int
    ) {

        private val speedX = 4 * (random.nextFloat() * 2 - 1)
        private val speedY = 4 * random.nextFloat()

        fun draw(canvas: Canvas, paint: Paint) {
            paint.color = color
            paint.textSize = size.toFloat()
            canvas.drawText(text, x, y, paint)
        }

        fun update() {
            x += speedX
            y += speedY
        }
    }
}
