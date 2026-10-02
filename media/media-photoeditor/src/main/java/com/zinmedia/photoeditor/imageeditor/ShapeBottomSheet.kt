package com.zinmedia.photoeditor.imageeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.zinmedia.photoeditor.R
import com.zinmedia.photoeditor.engine.shape.ShapeType
import com.zinmedia.photoeditor.ui.EditorDoneButton
import com.zinmedia.photoeditor.ui.EditorIconButton
import com.zinmedia.photoeditor.ui.EditorScrim
import com.zinmedia.photoeditor.ui.EditorTopBar
import com.zinmedia.photoeditor.ui.VerticalColorPicker
import com.zinmedia.photoeditor.ui.VerticalSizePicker

private data class DrawTool(val shape: ShapeType, val icon: Int, val label: String)

private val DrawTools = listOf(
    DrawTool(ShapeType.Brush, R.drawable.zm_ic_pen, "Pena"),
    DrawTool(ShapeType.Line, R.drawable.ic_line, "Garis"),
    DrawTool(ShapeType.Arrow(), R.drawable.ic_arrow, "Panah"),
    DrawTool(ShapeType.Oval, R.drawable.ic_oval, "Oval"),
    DrawTool(ShapeType.Rectangle, R.drawable.ic_rectangle, "Kotak"),
)

/**
 * Mode gambar: undo + "Selesai" di atas, slider warna di kanan,
 * slider ukuran di kiri, dan pilihan kuas/bentuk/penghapus di bawah.
 */
@Composable
fun DrawWidget(
    enableUndo: Boolean,
    isEnableEraser: Boolean,
    onUndo: () -> Unit,
    onEraser: () -> Unit,
    isEnable: Boolean,
    onColorChanged: (Int) -> Unit,
    onOpacityChanged: (Int) -> Unit,
    onShapeSizeChanged: (Float) -> Unit,
    onShapePicked: (ShapeType) -> Unit,
    onDone: () -> Unit,
) {
    if (!isEnable) return

    var selectedTool by remember { mutableStateOf(DrawTools.first()) }
    var color by remember { mutableStateOf(Color.White) }
    var stroke by remember { mutableFloatStateOf(20f) }

    // Samakan warna & ukuran kuas engine dengan nilai awal slider.
    LaunchedEffect(Unit) {
        onColorChanged(color.toArgb())
        onShapeSizeChanged(stroke)
    }

    Box(Modifier.fillMaxSize()) {
        EditorScrim(top = true, modifier = Modifier.align(Alignment.TopCenter))
        EditorScrim(top = false, modifier = Modifier.align(Alignment.BottomCenter))

        EditorTopBar(
            onClose = onUndo,
            closeIcon = R.drawable.zm_ic_undo,
            closeDescription = "Urungkan",
            closeEnabled = enableUndo,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            EditorDoneButton(onClick = onDone)
        }

        VerticalColorPicker(
            onColorChange = {
                color = it
                onColorChanged(it.toArgb())
            },
            colorThumb = color,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 120.dp, end = 10.dp),
        )

        VerticalSizePicker(
            onValueChange = {
                stroke = it
                onShapeSizeChanged(it)
            },
            currentStroke = stroke,
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
            DrawTools.forEach { tool ->
                EditorIconButton(
                    icon = tool.icon,
                    contentDescription = tool.label,
                    selected = !isEnableEraser && tool == selectedTool,
                    onClick = {
                        selectedTool = tool
                        if (isEnableEraser) onEraser()
                        onShapePicked(tool.shape)
                    },
                )
            }
            EditorIconButton(
                icon = R.drawable.ic_eraser,
                contentDescription = "Penghapus",
                selected = isEnableEraser,
                onClick = onEraser,
            )
        }
    }
}
