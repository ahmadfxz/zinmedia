package com.zinmedia.videoeditor.draw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel

enum class BrushStyle {
    Pen,
    Neon,
    Eraser
}


data class DrawingUiState(
    val isDrawingEnabled: Boolean = false,
    val canvasSize: Size = Size.Zero,
    val brushStyle: BrushStyle = BrushStyle.Pen,
    val paths: List<DrawPath> = emptyList(),
    val currentPoints: List<Offset> = emptyList(),
    val currentColor: Color = Color.White,
    val currentStroke: Float = 25f
)

class DrawingViewModel : ViewModel() {

    var uiState by mutableStateOf(DrawingUiState())
        private set

    // Helper setState
    private fun update(transform: DrawingUiState.() -> DrawingUiState) {
        uiState = uiState.transform()
    }

    fun setDrawingEnabled(enabled: Boolean) = update {
        copy(isDrawingEnabled = enabled)
    }

    fun setCanvasSize(size: Size) = update {
        copy(canvasSize = size)
    }

    fun setBrushStyle(style: BrushStyle) = update {
        copy(brushStyle = style)
    }

    fun setColor(color: Color) = update {
        copy(currentColor = color)
    }

    fun setStroke(stroke: Float) = update {
        copy(currentStroke = stroke)
    }

    fun startNewPath(point: Offset) = update {
        copy(currentPoints = listOf(point))
    }

    fun addPoint(point: Offset) = update {
        copy(currentPoints = currentPoints + point)
    }

    fun endPath() = update {
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

    fun undo() = update {
        if (paths.isEmpty()) this
        else copy(paths = paths.dropLast(1))
    }

    fun clear() = update {
        copy(paths = emptyList())
    }
}


