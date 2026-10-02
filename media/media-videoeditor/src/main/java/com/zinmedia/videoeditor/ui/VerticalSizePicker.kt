package com.zinmedia.videoeditor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times


@Composable
fun VerticalSizePicker(
    onValueChange: (Float) -> Unit,
    currentStroke: Float,
    modifier: Modifier = Modifier,
    minStroke: Float = 20f,
    maxStroke: Float = 80f,
    minThumbDp: Dp = 15.dp,
    maxThumbDp: Dp = 35.dp
) {
    var sliderPosition by remember { mutableStateOf((currentStroke - minStroke) / (maxStroke - minStroke)) }

    val density = LocalDensity.current
    var trackMode by remember { mutableStateOf(TrackMode.NORMAL) }
    val trackOffsetX by animateDpAsState(
        targetValue = if (trackMode == TrackMode.TRAPEZOID) 10.dp else (-15).dp
    )

    Box(
        modifier = modifier
            .width(35.dp)
            .offset(x = trackOffsetX)
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        VerticalTrapezoidTrack(mode = trackMode, topWidth = 20.dp, bottomWidth = 8.dp)

        var boxHeight by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(35.dp)
                .onGloballyPositioned { coords ->
                    boxHeight = coords.size.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            // Sentuh → ubah mode
                            trackMode = TrackMode.TRAPEZOID
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (boxHeight > 0f) {
                                val newPos =
                                    (sliderPosition - dragAmount / boxHeight).coerceIn(0f, 1f)
                                sliderPosition = newPos
                                onValueChange(minStroke + sliderPosition * (maxStroke - minStroke))
                            }
                        },
                        onDragEnd = {
                            // Lepas → kembali normal
                            trackMode = TrackMode.NORMAL
                        },
                        onDragCancel = {
                            trackMode = TrackMode.NORMAL
                        }
                    )

                }
        ) {
            if (boxHeight > 0f) {
                // interpolasi ukuran thumb sesuai stroke
                val animatedThumbDp by animateDpAsState(
                    targetValue = if (trackMode == TrackMode.TRAPEZOID)
                        minThumbDp + ((currentStroke - minStroke) / (maxStroke - minStroke) * (maxThumbDp - minThumbDp))
                    else minThumbDp
                )

                val animatedThumbY by animateDpAsState(
                    targetValue = with(density) { ((1f - sliderPosition) * (boxHeight - animatedThumbDp.toPx())).toDp() }
                )

                Box(
                    modifier = Modifier
                        .offset(y = animatedThumbY)
                        .size(animatedThumbDp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f))
                        .align(Alignment.TopCenter)
                )

            }
        }
    }
}

enum class TrackMode {
    NORMAL,
    TRAPEZOID
}

@Composable
fun VerticalTrapezoidTrack(
    modifier: Modifier = Modifier,
    mode: TrackMode = TrackMode.NORMAL,
    topWidth: Dp = 20.dp,
    bottomWidth: Dp = 8.dp,
    height: Dp = 200.dp,
    cornerRadius: Dp = 6.dp, // radius lengkung pojok
    color: Color = Color.White.copy(alpha = 0.5f)
) {
    val animatedTopWidth by animateDpAsState(
        targetValue = if (mode == TrackMode.TRAPEZOID) topWidth else bottomWidth,
        animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing)
    )
    val animatedBottomWidth by animateDpAsState(
        targetValue = bottomWidth,
        animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing)
    )
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(durationMillis = 250)
    )

    Box(
        modifier = modifier
            .width(topWidth)
            .height(height)
            .drawBehind {
                val h = size.height
                val radius = cornerRadius.toPx()

                val topW = animatedTopWidth.toPx()
                val bottomW = animatedBottomWidth.toPx()

                val path = Path().apply {
                    if (mode == TrackMode.NORMAL) {
                        val w = bottomW
                        moveTo(size.width / 2 - w / 2 + radius, 0f)
                        lineTo(size.width / 2 + w / 2 - radius, 0f)
                        quadraticTo(size.width / 2 + w / 2, 0f, size.width / 2 + w / 2, radius)
                        lineTo(size.width / 2 + w / 2, h - radius)
                        quadraticTo(
                            size.width / 2 + w / 2,
                            h.toFloat(),
                            size.width / 2 + w / 2 - radius,
                            h
                        )
                        lineTo(size.width / 2 - w / 2 + radius, h)
                        quadraticTo(
                            size.width / 2 - w / 2,
                            h.toFloat(),
                            size.width / 2 - w / 2,
                            h - radius
                        )
                        lineTo(size.width / 2 - w / 2, radius)
                        quadraticTo(size.width / 2 - w / 2, 0f, size.width / 2 - w / 2 + radius, 0f)
                        close()
                    } else {
                        moveTo(size.width / 2 - topW / 2 + radius, 0f)
                        lineTo(size.width / 2 + topW / 2 - radius, 0f)
                        quadraticTo(
                            size.width / 2 + topW / 2,
                            0f,
                            size.width / 2 + topW / 2,
                            radius
                        )
                        lineTo(size.width / 2 + bottomW / 2, h - radius)
                        quadraticTo(
                            size.width / 2 + bottomW / 2,
                            h.toFloat(),
                            size.width / 2 + bottomW / 2 - radius,
                            h
                        )
                        lineTo(size.width / 2 - bottomW / 2 + radius, h)
                        quadraticTo(
                            size.width / 2 - bottomW / 2,
                            h.toFloat(),
                            size.width / 2 - bottomW / 2,
                            h - radius
                        )
                        lineTo(size.width / 2 - topW / 2, radius)
                        quadraticTo(
                            size.width / 2 - topW / 2,
                            0f,
                            size.width / 2 - topW / 2 + radius,
                            0f
                        )
                        close()
                    }
                }

                drawPath(path, animatedColor)
            }
    )
}