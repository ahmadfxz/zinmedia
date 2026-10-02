package com.zinmedia.photoeditor.imageeditor

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zinmedia.photoeditor.imageeditor.widget.button.JernihTextButton
import com.zinmedia.photoeditor.imageeditor.widget.button.ShapeButton
import com.zinmedia.photoeditor.R
import com.zinmedia.photoeditor.ui.VerticalColorPicker
import com.zinmedia.photoeditor.ui.VerticalSizePicker
import com.zinmedia.photoeditor.engine.shape.ShapeType

// ShapeBottomSheet.kt
@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedShape by remember { mutableStateOf<ShapeType>(ShapeType.Brush) }
    var seletedColor by remember { mutableStateOf(Color.White) }
    var currentStoke by remember { mutableStateOf(20f) }

    if (isEnable) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Shape Picker - Top Center
            ShapePickerSection(
                enableUndo = enableUndo,
                isEnableEraser = isEnableEraser,
                onUndo = onUndo,
                onDone = onDone,
                selectedShape = selectedShape,
                onEraser = onEraser,
                onShapeSelected = { shapeType ->
                    selectedShape = shapeType
                    onShapePicked(shapeType)
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
            // Color Picker - Bottom Center

//            Column(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//            ) {
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.SpaceBetween,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(12.dp)
//                ) {
//                    // Slider kiri - normal (kecil di kiri, besar di kanan)
//                    CustomHorizontalSliderSection(
//                        title = "Opacity",
//                        onValueChange = { newValue ->
//                            onOpacityChanged((newValue * 2.5).roundToInt())
//                        },
//                        valueRange = 10f..100f,
//                    )

                    // Slider kanan - terbalik (kecil di kanan, besar di kiri)
//                    CustomHorizontalSliderSectionReversed(
//                        title = "Size",
//                        onValueChange = { newValue ->
//                            onShapeSizeChanged(newValue)
//                        },
//                        valueRange = 10f..100f,
//                    )
//                }
//            }

            VerticalColorPicker(
                onColorChange = {
                    onColorChanged(it.toArgb())
                    seletedColor = it
                },
                colorThumb = seletedColor,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
            )
            VerticalSizePicker(
                onValueChange = {
                    onShapeSizeChanged(it)
                    currentStoke = it
                },
                currentStroke = currentStoke,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
            )

        }
    }
}

@Composable
private fun ShapePickerSection(
    enableUndo: Boolean,
    isEnableEraser: Boolean,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    onEraser: () -> Unit,
    selectedShape: ShapeType,
    onShapeSelected: (ShapeType) -> Unit,
    modifier: Modifier,
) {
    var showMoreOptions by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Done Button
        JernihTextButton(
            title = "Selesai",
            onClick = onDone,
        )
        Spacer(modifier = Modifier.weight(1f))

        // Undo Button
        if (enableUndo)
            ShapeButton(
                isSelected = true,
                onClick = {
                    onUndo()
                },
                iconRes = R.drawable.ic_undo,
            )

        ShapeButton(
            isSelected = isEnableEraser,
            onClick = {
                onEraser()
            },
            iconRes = R.drawable.ic_eraser,
        )

        // Basic Shapes - Brush dan Line (selalu visible)
        ShapeOption(
            shapeType = ShapeType.Brush,
            iconRes = R.drawable.ic_brush,
            isSelected = selectedShape == ShapeType.Brush && !isEnableEraser,
            onSelected = onShapeSelected
        )

        ShapeOption(
            shapeType = ShapeType.Line,
            iconRes = R.drawable.ic_line, // Ganti dengan icon line yang sesuai
            isSelected = selectedShape == ShapeType.Line && !isEnableEraser,
            onSelected = onShapeSelected
        )

        // More Options Button dengan dropdown
        Box {
            // More Options Button
            ShapeButton(
                isSelected = (selectedShape == ShapeType.Rectangle || selectedShape == ShapeType.Oval || selectedShape is ShapeType.Arrow) && !isEnableEraser,
                onClick = {
                    showMoreOptions = !showMoreOptions
                },
                iconRes = R.drawable.ic_option,
            )

            // Dropdown Menu untuk shape tambahan
            if (showMoreOptions) {
                DropdownMenu(
                    expanded = showMoreOptions,
                    onDismissRequest = { showMoreOptions = false },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.8f))
                ) {
                    // Rectangle Option
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_rectangle),
                                    contentDescription = "Rectangle",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Rectangle",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        onClick = {
                            onShapeSelected(ShapeType.Rectangle)
                            showMoreOptions = false
                        }
                    )

                    // Oval Option
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_oval),
                                    contentDescription = "Oval",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Oval",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        onClick = {
                            onShapeSelected(ShapeType.Oval)
                            showMoreOptions = false
                        }
                    )

                    // Arrow Option
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow), // Ganti dengan icon arrow yang sesuai
                                    contentDescription = "Arrow",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Arrow",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        onClick = {
                            onShapeSelected(ShapeType.Arrow()) // Sesuaikan dengan constructor Arrow Anda
                            showMoreOptions = false
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun ShapeOption(
    shapeType: ShapeType,
    @DrawableRes iconRes: Int,
    isSelected: Boolean,
    onSelected: (ShapeType) -> Unit
) {
    ShapeButton(
        isSelected = isSelected,
        onClick = {
            onSelected(shapeType)
        },
        iconRes = iconRes,
    )
}