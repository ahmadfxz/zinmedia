package com.jernih.videoeditor.draw

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class DrawPath(
    val points: List<Offset>,
    val color: Color,
    val stroke: Float,
    val style: BrushStyle = BrushStyle.Pen,
)
