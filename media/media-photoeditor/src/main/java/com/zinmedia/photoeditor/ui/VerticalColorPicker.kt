package com.zinmedia.photoeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val PaletteTopToBottom = listOf(
    Color.White,
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
    Color.Black,
)

/**
 * Slider warna vertikal: bar pelangi, bisa diketuk atau digeser.
 * Posisi awal thumb mengikuti [colorThumb] bila warnanya ada di palet.
 */
@Composable
fun VerticalColorPicker(
    onColorChange: (Color) -> Unit,
    colorThumb: Color,
    modifier: Modifier = Modifier
) {
    val thumbSize = 26.dp
    val density = LocalDensity.current
    var trackHeight by remember { mutableIntStateOf(0) }
    // 0 = atas, 1 = bawah
    var position by remember {
        mutableFloatStateOf(
            PaletteTopToBottom.indexOf(colorThumb)
                .takeIf { it >= 0 }
                ?.let { it / (PaletteTopToBottom.size - 1f) }
                ?: 0f
        )
    }

    fun select(y: Float) {
        if (trackHeight <= 0) return
        position = (y / trackHeight).coerceIn(0f, 1f)
        val scaled = position * (PaletteTopToBottom.size - 1)
        val index = scaled.toInt().coerceIn(0, PaletteTopToBottom.size - 2)
        onColorChange(lerp(PaletteTopToBottom[index], PaletteTopToBottom[index + 1], scaled - index))
    }

    Box(
        modifier = modifier
            .width(44.dp)
            .height(220.dp)
            .onSizeChanged { trackHeight = it.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    select(down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            select(change.position.y)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(10.dp)
                .border(1.5.dp, Color.White, RoundedCornerShape(5.dp))
                .background(Brush.verticalGradient(PaletteTopToBottom), RoundedCornerShape(5.dp))
        )
        val thumbOffset = with(density) { (position * trackHeight).toDp() } - thumbSize / 2
        Box(
            modifier = Modifier
                .offset(y = thumbOffset.coerceAtLeast(-thumbSize / 2))
                .size(thumbSize)
                .shadow(4.dp, CircleShape)
                .background(colorThumb, CircleShape)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}
