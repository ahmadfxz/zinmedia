package com.zinmedia.videoeditor.draw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel

internal enum class BrushStyle {
    Pen,
    Neon,
    Eraser
}


internal data class DrawingUiState(
    val isDrawingEnabled: Boolean = false,
    val canvasSize: Size = Size.Zero,
    val brushStyle: BrushStyle = BrushStyle.Pen,
    val paths: List<DrawPath> = emptyList(),
    val currentPoints: List<Offset> = emptyList(),
    val currentColor: Color = Color.White,
    val currentStroke: Float = 25f
)

internal class DrawingViewModel : ViewModel() {

    internal var uiState by mutableStateOf(DrawingUiState())
        private set

    // Helper setState
    private fun update(transform: DrawingUiState.() -> DrawingUiState) {
        uiState = uiState.transform()
    }

    internal fun setDrawingEnabled(enabled: Boolean) = update {
        copy(isDrawingEnabled = enabled)
    }

    internal fun setCanvasSize(size: Size) = update {
        copy(canvasSize = size)
    }

    internal fun setBrushStyle(style: BrushStyle) = update {
        copy(brushStyle = style)
    }

    internal fun setColor(color: Color) = update {
        copy(currentColor = color)
    }

    internal fun setStroke(stroke: Float) = update {
        copy(currentStroke = stroke)
    }

    internal fun startNewPath(point: Offset) = update {
        copy(currentPoints = listOf(point))
    }

    internal fun addPoint(point: Offset) = update {
        copy(currentPoints = currentPoints + point)
    }

    internal fun endPath() = update {
        if (currentPoints.isEmpty()) return@update this

        val newPath = DrawPath(
            points = currentPoints,
            color = currentColor,
            stroke = currentStroke,
            style = brushStyle
        )
        copy(
            paths = paths + newPath,
            currentPoints = emptyList()
        )
    }

    internal fun undo() = update {
        if (paths.isEmpty()) this
        else copy(paths = paths.dropLast(1))
    }

    internal fun clear() = update {
        copy(paths = emptyList())
    }
}


