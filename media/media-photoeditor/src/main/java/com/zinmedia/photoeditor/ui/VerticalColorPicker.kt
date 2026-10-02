package com.zinmedia.photoeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp


@Composable
fun VerticalColorPicker(
    onColorChange: (Color) -> Unit,
    colorThumb: Color,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color.Black,// tambahkan putih
        Color.Red,
        Color.Magenta,
        Color.Blue,
        Color.Cyan,
        Color.Green,
        Color.Yellow,
        Color.White,
    )

    var sliderPosition by remember { mutableStateOf(0f) } // 0 = bawah, 1 = atas
    val thumbSize = 20.dp
    val density = LocalDensity.current

    fun lerpColor(start: Color, end: Color, fraction: Float): Color {
        return Color(
            red = start.red + (end.red - start.red) * fraction,
            green = start.green + (end.green - start.green) * fraction,
            blue = start.blue + (end.blue - start.blue) * fraction,
            alpha = start.alpha + (end.alpha - start.alpha) * fraction
        )
    }


    Box(
        modifier = modifier
            .width(20.dp)
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Track vertikal
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush = Brush.verticalGradient(colors.reversed()))
        )

        var boxHeight by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(50.dp)
                .onGloballyPositioned { coords ->
                    boxHeight = coords.size.height.toFloat()
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (boxHeight > 0f) {
                            // drag ke atas = slider naik, drag ke bawah = slider turun
                            val newPos = (sliderPosition - dragAmount / boxHeight).coerceIn(0f, 1f)
                            sliderPosition = newPos

                            val scaled = newPos * (colors.size - 1)
                            val index = scaled.toInt().coerceIn(0, colors.size - 2)
                            val fraction = scaled - index
                            onColorChange(lerpColor(colors[index], colors[index + 1], fraction))
                        }
                    }
                }
        ) {
            if (boxHeight > 0f) {
                // posisi thumb dari atas
                val thumbY =
                    with(density) { ((1f - sliderPosition) * (boxHeight - thumbSize.toPx())).toDp() }
                Box(
                    modifier = Modifier
                        .offset(y = thumbY)
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(colorThumb)
                        .align(Alignment.TopCenter)
                )
            }
        }
    }
}