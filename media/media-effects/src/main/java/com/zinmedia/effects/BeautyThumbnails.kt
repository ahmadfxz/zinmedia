package com.zinmedia.effects

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Thumbnail preset beauty tanpa aset: ilustrasi wajah yang digambar dari nilai [BeautyParams]
 * (bentuk wajah, besar mata, warna & ukuran bibir, perona, kontur, kulit cerah), sehingga tiap preset
 * langsung punya gambar yang mencerminkan isinya. Aplikasi tetap bisa memakai gambar sendiri lewat
 * [BeautyPreset.iconUrl].
 *
 * ```kotlin
 * val bitmap = BeautyThumbnails.draw(preset.params, sizePx = 160)   // Compose: bitmap.asImageBitmap()
 * ```
 */
public object BeautyThumbnails {
    /** Gambar thumbnail [sizePx]×[sizePx] (lingkaran berlatar, sudut transparan). */
    public fun draw(params: BeautyParams, sizePx: Int): Bitmap {
        val size = sizePx.coerceAtLeast(16)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(size.toFloat(), size.toFloat())
        drawFace(canvas, params)
        return bitmap
    }

    private fun drawFace(c: Canvas, p: BeautyParams) {
        fun v(feature: BeautyFeature) = p[feature]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val lipstick = v(BeautyFeature.Lipstick)

        // Latar: lingkaran pastel, sedikit diwarnai lipstik.
        val tint = if (lipstick > 0f) p.lipColor else ROSE
        paint.shader = RadialGradient(
            0.5f, 0.35f, 0.7f,
            mix(Color.WHITE, tint, 0.18f), mix(Color.WHITE, tint, 0.42f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(0.5f, 0.5f, 0.5f, paint)
        paint.shader = null
        c.save()
        c.clipPath(Path().apply { addCircle(0.5f, 0.5f, 0.5f, Path.Direction.CW) })
        // Wajah memenuhi lingkaran agar detail terbaca di ukuran ikon.
        c.scale(1.3f, 1.3f, 0.5f, 0.6f)

        // Bentuk wajah.
        val narrow = 1f - 0.14f * v(BeautyFeature.SlimFace) - 0.1f * v(BeautyFeature.NarrowFace) -
            0.1f * v(BeautyFeature.SmallFace) - 0.05f * v(BeautyFeature.Jaw)
        val halfWidth = 0.215f * narrow
        val chinY = 0.83f + 0.03f * v(BeautyFeature.Chin) - 0.03f * v(BeautyFeature.SmallFace)
        val vShape = v(BeautyFeature.VShape)
        val cx = 0.5f
        val top = 0.26f - 0.02f * v(BeautyFeature.Forehead)
        val face = Path().apply {
            moveTo(cx, top)
            cubicTo(cx + halfWidth * 1.1f, top, cx + halfWidth, 0.42f, cx + halfWidth, 0.52f)
            cubicTo(
                cx + halfWidth, 0.66f - 0.05f * vShape, cx + halfWidth * (0.55f - 0.25f * vShape), chinY,
                cx, chinY,
            )
            cubicTo(
                cx - halfWidth * (0.55f - 0.25f * vShape), chinY, cx - halfWidth, 0.66f - 0.05f * vShape,
                cx - halfWidth, 0.52f,
            )
            cubicTo(cx - halfWidth, 0.42f, cx - halfWidth * 1.1f, top, cx, top)
            close()
        }

        // Rambut di belakang, leher & bahu.
        paint.color = HAIR
        c.drawOval(RectF(cx - 0.29f, 0.12f, cx + 0.29f, 0.98f), paint)
        val skin = mix(mix(SKIN, SKIN_LIGHT, 0.25f + 0.75f * v(BeautyFeature.Brighten)), Color.rgb(250, 190, 190), 0.25f * v(BeautyFeature.Rosy))
        paint.color = mix(skin, Color.BLACK, 0.08f)
        c.drawRect(cx - 0.07f, 0.7f, cx + 0.07f, 0.95f, paint)
        paint.color = mix(tint, Color.WHITE, 0.35f)
        c.drawOval(RectF(cx - 0.33f, 0.88f, cx + 0.33f, 1.25f), paint)

        // Wajah dengan kulit (lebih halus = gradasi lebih lembut).
        paint.shader = LinearGradient(
            0f, top, 0f, chinY, skin, mix(skin, Color.rgb(214, 150, 120), 0.35f - 0.2f * v(BeautyFeature.Smooth)),
            Shader.TileMode.CLAMP,
        )
        c.drawPath(face, paint)
        paint.shader = null
        c.save()
        c.clipPath(face)
        val contour = v(BeautyFeature.Contour)
        if (contour > 0f) {
            paint.color = Color.argb((90 * contour).toInt(), 150, 90, 70)
            c.drawOval(RectF(cx - halfWidth - 0.05f, 0.5f, cx - halfWidth + 0.05f, 0.78f), paint)
            c.drawOval(RectF(cx + halfWidth - 0.05f, 0.5f, cx + halfWidth + 0.05f, 0.78f), paint)
        }
        val blush = (0.9f * v(BeautyFeature.Blush) + 0.5f * v(BeautyFeature.Rosy)).coerceAtMost(1f)
        if (blush > 0f) {
            paint.color = Color.argb((150 * blush).toInt(), 255, 120, 150)
            val bx = halfWidth * 0.6f
            c.drawOval(RectF(cx - bx - 0.05f, 0.58f, cx - bx + 0.05f, 0.635f), paint)
            c.drawOval(RectF(cx + bx - 0.05f, 0.58f, cx + bx + 0.05f, 0.635f), paint)
        }
        c.restore()

        // Poni.
        paint.color = HAIR
        c.drawPath(Path().apply {
            moveTo(cx - halfWidth * 1.15f, 0.47f)
            cubicTo(cx - halfWidth * 1.1f, 0.2f, cx + halfWidth * 0.4f, 0.14f, cx + halfWidth * 1.15f, 0.42f)
            cubicTo(cx + halfWidth * 0.5f, 0.28f, cx - halfWidth * 0.3f, 0.3f, cx - halfWidth * 1.15f, 0.47f)
            close()
        }, paint)

        // Alis & mata.
        val eyeScale = 1f + 0.45f * v(BeautyFeature.EnlargeEyes)
        val eyeGap = halfWidth * (0.46f + 0.08f * v(BeautyFeature.EyeDistance))
        val eyeY = 0.5f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 0.014f
        paint.color = HAIR
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * eyeGap
            c.drawPath(Path().apply {
                moveTo(ex - side * 0.035f, eyeY - 0.068f)
                quadTo(ex + side * 0.005f, eyeY - 0.09f, ex + side * 0.045f, eyeY - 0.066f)
            }, paint)
        }
        paint.style = Paint.Style.FILL
        val angle = 0.012f * v(BeautyFeature.EyeAngle)
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * eyeGap
            val rw = 0.036f * eyeScale
            val rh = 0.03f * eyeScale
            paint.color = Color.WHITE
            c.drawOval(RectF(ex - rw, eyeY - rh, ex + rw, eyeY + rh), paint)
            paint.color = mix(Color.rgb(70, 45, 35), Color.rgb(120, 80, 55), 0.6f * v(BeautyFeature.BrightenEyes))
            c.drawCircle(ex, eyeY, rh * 0.85f, paint)
            paint.color = Color.rgb(25, 18, 15)
            c.drawCircle(ex, eyeY, rh * 0.45f, paint)
            paint.color = Color.WHITE
            c.drawCircle(ex + rh * 0.3f, eyeY - rh * 0.35f, rh * 0.22f, paint)
            // Garis bulu mata atas, ujung luar naik/turun mengikuti sudut mata.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.011f
            paint.color = Color.rgb(30, 20, 18)
            c.drawPath(Path().apply {
                moveTo(ex - side * rw * 1.05f, eyeY - rh * 0.1f)
                quadTo(ex, eyeY - rh * 1.45f, ex + side * rw * 1.15f, eyeY - rh * 0.35f - angle)
            }, paint)
            paint.style = Paint.Style.FILL
        }

        // Hidung.
        val noseHalf = 0.018f * (1f - 0.4f * v(BeautyFeature.Nose))
        val noseY = 0.625f + 0.015f * v(BeautyFeature.NoseLength)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.009f
        paint.color = mix(skin, Color.rgb(150, 90, 70), 0.55f)
        c.drawPath(Path().apply {
            moveTo(cx - noseHalf, noseY)
            quadTo(cx, noseY + 0.014f, cx + noseHalf, noseY)
        }, paint)
        paint.style = Paint.Style.FILL

        // Bibir.
        val mouth = 1f + 0.25f * v(BeautyFeature.MouthSize)
        val smile = v(BeautyFeature.Smile)
        val lw = 0.052f * mouth * narrow.coerceAtLeast(0.8f)
        val lh = 0.02f * mouth
        val my = 0.72f
        paint.color = mix(Color.rgb(214, 128, 128), p.lipColor, (lipstick * 1.3f).coerceAtMost(1f))
        val lift = 0.012f * smile
        c.drawPath(Path().apply {
            moveTo(cx - lw, my - lift)
            quadTo(cx - lw * 0.5f, my - lh * 1.4f, cx, my - lh * 0.7f)
            quadTo(cx + lw * 0.5f, my - lh * 1.4f, cx + lw, my - lift)
            quadTo(cx, my + lh * 1.9f - lift, cx - lw, my - lift)
            close()
        }, paint)
        // Kilau bibir sesuai finish lipstik.
        val shine = if (lipstick <= 0f) 70 else when (p.lipFinish) {
            LipFinish.Matte -> 20
            LipFinish.Satin -> 70
            LipFinish.Gloss -> 150
        }
        paint.color = Color.argb(shine, 255, 255, 255)
        c.drawOval(RectF(cx - lw * 0.3f, my + lh * 0.25f, cx + lw * 0.1f, my + lh * 0.65f), paint)

        // Kilau: kulit halus / cerah.
        val glow = (v(BeautyFeature.Smooth) + v(BeautyFeature.Brighten)).coerceAtMost(1f)
        if (glow > 0.2f) {
            paint.color = Color.WHITE
            sparkle(c, paint, 0.79f, 0.24f, 0.05f)
            if (glow > 0.55f) sparkle(c, paint, 0.2f, 0.33f, 0.032f)
        }
        c.restore()
    }

    private fun sparkle(c: Canvas, paint: Paint, x: Float, y: Float, r: Float) {
        c.drawPath(Path().apply {
            moveTo(x, y - r)
            quadTo(x, y, x + r, y)
            quadTo(x, y, x, y + r)
            quadTo(x, y, x - r, y)
            quadTo(x, y, x, y - r)
            close()
        }, paint)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(x: Int, y: Int) = (x + (y - x) * k).toInt()
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    private val ROSE = Color.rgb(240, 120, 150)
    private val HAIR = Color.rgb(58, 38, 32)
    private val SKIN = Color.rgb(236, 192, 160)
    private val SKIN_LIGHT = Color.rgb(250, 222, 200)
}
