package com.jernih.videoeditor.draw

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.min
import android.graphics.Paint as AndroidPaint


@SuppressLint("RememberReturnType")
@Composable
fun DrawingCanvas(
    viewModel: DrawingViewModel,
) {
    val uiState = viewModel.uiState
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (uiState.isDrawingEnabled) {
                    Modifier.pointerInput(true) {
                        detectDragGestures(
                            onDragStart = { offset -> viewModel.startNewPath(offset) },
                            onDrag = { change, _ -> viewModel.addPoint(change.position) },
                            onDragEnd = { viewModel.endPath() }
                        )
                    }
                } else Modifier
            )
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            viewModel.setCanvasSize(size)
            val paths = uiState.paths
            // gambar path lama
            for (path in paths) drawPathStyled(path)

            // path yang sedang digambar (real-time)
            drawPathStyled(
                DrawPath(
                    points = uiState.currentPoints,
                    color = uiState.currentColor,
                    stroke = uiState.currentStroke,
                    style = uiState.brushStyle
                ),
            )
        }


    }

    // preview dialog
    previewBitmap?.let { bmp ->
        Dialog(onDismissRequest = { previewBitmap = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Preview Drawing",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                )
            }
        }
    }
}


fun renderPathsToBitmap(
    paths: List<DrawPath>,
    canvasWidth: Float,
    canvasHeight: Float,
    bitmapWidth: Int,
    bitmapHeight: Int
): Bitmap {

    val scale = min(bitmapWidth / canvasWidth, bitmapHeight / canvasHeight)

    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)

    for (dp in paths) {
        if (dp.points.size < 2) continue

        val paint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
            strokeWidth = dp.stroke * scale

            when (dp.style) {
                BrushStyle.Pen, BrushStyle.Neon -> color = dp.color.toArgb()
                BrushStyle.Eraser -> {
                    xfermode =
                        android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                }
            }

            if (dp.style == BrushStyle.Neon) {
                maskFilter = android.graphics.BlurMaskFilter(
                    dp.stroke * scale * 1.8f,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
        }

        val path = android.graphics.Path()
        val pts = dp.points.map { Offset(it.x * scale, it.y * scale) }

        path.moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val curr = pts[i]
            val mid = Offset((prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
            path.quadTo(prev.x, prev.y, mid.x, mid.y)
        }

        // Glow untuk Neon
        if (dp.style == BrushStyle.Neon) {
            val glowPaint = android.graphics.Paint(paint).apply {
                strokeWidth = dp.stroke * scale * 2.2f
                color = dp.color.copy(alpha = 0.18f).toArgb()
            }
            canvas.drawPath(path, glowPaint)
        }

        // Core / Eraser
        canvas.drawPath(path, paint)
    }

    return bitmap
}


fun DrawScope.drawPathStyled(dp: DrawPath) {
    if (dp.points.size < 2) return

    when (dp.style) {
        BrushStyle.Pen -> drawPen(dp)
        BrushStyle.Neon -> drawNeon(dp)
        BrushStyle.Eraser -> drawEraser(dp)
    }
}

fun DrawScope.drawPen(dp: DrawPath) {
    if (dp.points.size > 1) {
        for (i in 0 until dp.points.lastIndex) {
            drawLine(
                color = dp.color,
                start = dp.points[i],
                end = dp.points[i + 1],
                strokeWidth = dp.stroke,
                cap = StrokeCap.Round
            )
        }
    }
}


fun DrawScope.drawNeon(dp: DrawPath) {
    if (dp.points.size < 2) return

    val path = Path().apply {
        dp.points.forEachIndexed { index, point ->
            if (index == 0) moveTo(point.x, point.y)
            else lineTo(point.x, point.y)
        }
    }

    // GLOW
    val glowPaint = AndroidPaint().apply {
        color = dp.color.copy(alpha = 0.6f).toArgb()
        strokeWidth = dp.stroke * 2.8f
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND         // ujung bulat
        strokeJoin = AndroidPaint.Join.ROUND        // BELIKAN bulat (FIX PATAH)
        isAntiAlias = true
        maskFilter = android.graphics.BlurMaskFilter(
            dp.stroke * 1.8f,
            android.graphics.BlurMaskFilter.Blur.NORMAL
        )
    }


    drawIntoCanvas { canvas ->
        val c = canvas.nativeCanvas
        c.drawPath(path.asAndroidPath(), glowPaint)
    }
}


private fun DrawScope.drawEraser(dp: DrawPath) {
    drawStroke(
        points = dp.points,
        color = Color.Transparent,
        width = dp.stroke,
        blendMode = BlendMode.Clear
    )
}

private fun DrawScope.drawStroke(
    points: List<Offset>,
    color: Color,
    width: Float,
    blendMode: BlendMode = BlendMode.SrcOver
) {
    for (i in 0 until points.lastIndex) {
        drawLine(
            brush = SolidColor(color),
            start = points[i],
            end = points[i + 1],
            strokeWidth = width,
            cap = StrokeCap.Round,
            blendMode = blendMode
        )
    }
}