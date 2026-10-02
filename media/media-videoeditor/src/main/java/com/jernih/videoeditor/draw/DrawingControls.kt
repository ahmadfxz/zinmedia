package com.jernih.videoeditor.draw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.Bitmap
import com.jernih.media.videoeditor.R
import com.jernih.videoeditor.widget.ControlIcon
import com.jernih.videoeditor.ui.VerticalColorPicker
import com.jernih.videoeditor.ui.VerticalSizePicker

@Composable
fun DrawingControls(
    viewModel: DrawingViewModel,
    onDoneDraw: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(all = 6.dp)
    ) {
        // ===== Brush & Undo Bar =====
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            ControlIcon(
                icon = R.drawable.ic_undo,
                contentDescription = "Undo",
                onClick = { viewModel.undo() }
            )
            ControlIcon(
                icon = R.drawable.ic_eraser,
                contentDescription = "Eraser",
                onClick = { viewModel.setBrushStyle(BrushStyle.Eraser) }
            )
            ControlIcon(
                icon = R.drawable.ic_draw,
                contentDescription = "Pen",
                onClick = {
                    viewModel.setBrushStyle(BrushStyle.Pen)
                }
            )
            ControlIcon(
                icon = R.drawable.ic_neon,
                contentDescription = "Neon",
                onClick = { viewModel.setBrushStyle(BrushStyle.Neon) }
            )

            Spacer(Modifier.weight(1f))
            Button(
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                ),
                onClick = {
                    val bitmapWidth = 720
                    val bitmapHeight = 1280
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
                        bitmapWidth,
                        bitmapHeight
                    )
                    onDoneDraw(bitmap)
                    viewModel.setDrawingEnabled(false)
                },
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                Text(
                    "Simpan",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        // ===== Color Picker =====
        VerticalColorPicker(
            onColorChange = { viewModel.setColor(it) },
            colorThumb = uiState.currentColor,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        VerticalSizePicker(
            onValueChange = {
                viewModel.setStroke(it)
            },
            currentStroke = uiState.currentStroke,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}
