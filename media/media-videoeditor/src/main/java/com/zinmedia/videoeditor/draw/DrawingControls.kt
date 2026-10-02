package com.zinmedia.videoeditor.draw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.Bitmap
import com.zinmedia.videoeditor.R
import com.zinmedia.videoeditor.ui.EditorDoneButton
import com.zinmedia.videoeditor.ui.EditorIconButton
import com.zinmedia.videoeditor.ui.EditorScrim
import com.zinmedia.videoeditor.ui.EditorTopBar
import com.zinmedia.videoeditor.ui.VerticalColorPicker
import com.zinmedia.videoeditor.ui.VerticalSizePicker

/**
 * Mode gambar: undo + "Selesai" di atas, slider warna di kanan,
 * slider ukuran di kiri, dan pilihan kuas di bawah.
 */
@Composable
fun DrawingControls(
    viewModel: DrawingViewModel,
    onDoneDraw: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState

    fun saveDrawing() {
        val allPaths = uiState.paths.toMutableList()
        if (uiState.currentPoints.isNotEmpty()) {
            allPaths.add(
                DrawPath(
                    points = uiState.currentPoints.toMutableList(),
                    color = uiState.currentColor,
                    stroke = uiState.currentStroke,
                    style = uiState.brushStyle
                )
            )
        }
        val bitmap = renderPathsToBitmap(
            allPaths,
            uiState.canvasSize.width,
            uiState.canvasSize.height,
            OutputWidth,
            OutputHeight
        )
        onDoneDraw(bitmap)
        viewModel.setDrawingEnabled(false)
    }

    Box(modifier = modifier.fillMaxSize()) {
        EditorScrim(top = true, modifier = Modifier.align(Alignment.TopCenter))
        EditorScrim(top = false, modifier = Modifier.align(Alignment.BottomCenter))

        EditorTopBar(
            onClose = { viewModel.undo() },
            closeIcon = R.drawable.zm_ic_undo,
            closeDescription = "Urungkan",
            closeEnabled = uiState.paths.isNotEmpty(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            EditorDoneButton(onClick = ::saveDrawing)
        }

        VerticalColorPicker(
            onColorChange = { viewModel.setColor(it) },
            colorThumb = uiState.currentColor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 120.dp, end = 10.dp),
        )

        VerticalSizePicker(
            onValueChange = { viewModel.setStroke(it) },
            currentStroke = uiState.currentStroke,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            EditorIconButton(
                icon = R.drawable.zm_ic_pen,
                contentDescription = "Pena",
                selected = uiState.brushStyle == BrushStyle.Pen,
                onClick = { viewModel.setBrushStyle(BrushStyle.Pen) },
            )
            EditorIconButton(
                icon = R.drawable.ic_neon,
                contentDescription = "Neon",
                selected = uiState.brushStyle == BrushStyle.Neon,
                onClick = { viewModel.setBrushStyle(BrushStyle.Neon) },
            )
            EditorIconButton(
                icon = R.drawable.ic_eraser,
                contentDescription = "Penghapus",
                selected = uiState.brushStyle == BrushStyle.Eraser,
                onClick = { viewModel.setBrushStyle(BrushStyle.Eraser) },
            )
        }
    }
}

private const val OutputWidth = 720
private const val OutputHeight = 1280
