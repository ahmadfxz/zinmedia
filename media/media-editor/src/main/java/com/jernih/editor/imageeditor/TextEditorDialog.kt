package com.jernih.editor.imageeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jernih.editor.domain.model.FontItem
import com.jernih.editor.ui.VerticalColorPicker
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Close
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorDialog(
    initialText: String = "",
    initialColor: Color = Color.White,
    fonts: List<FontItem>,
    initialBackgroundColor: Color,
    onDismissRequest: () -> Unit,
    onTextEdited: (String, Color, Int, Int) -> Unit // Change to Int for color code
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedFontIndex by remember { mutableIntStateOf(0) }
    val selectedFont = remember(selectedFontIndex) {
        fonts.getOrNull(selectedFontIndex) ?: fonts.first()
    }
    val colorOptions = listOf(
        Color.Transparent to "None",
        Color.Black to "Black",
        Color.White to "White",
        Color.Red to "Red",
        Color.Blue to "Blue",
        Color(0xFFFFA500) to "Orange"
    )

    val initialIndex = colorOptions.indexOfFirst { it.first == initialBackgroundColor }
        .takeIf { it != -1 } ?: 0
    var currentColorIndex by remember { mutableIntStateOf(initialIndex) }

    val backgroundColor = colorOptions[currentColorIndex].first


    // Auto-focus dan show keyboard
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // Delay sedikit untuk memastikan UI sudah siap
        delay(100)
    }

    Dialog(
        onDismissRequest = {
            keyboardController?.hide()
            onDismissRequest()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.2f))
                .clickable {
                    keyboardController?.hide()
                    onDismissRequest()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header dengan Done button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            onDismissRequest()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            EvaIcons.Fill.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    TextButton(
                        onClick = {
                            if (text.isNotEmpty()) {
                                onTextEdited(text, backgroundColor, selectedColor.toArgb(), selectedFontIndex)
                            }
                            keyboardController?.hide()
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF2F2F2F),
                            containerColor = Color(0xFFFFFFFF)
                        ),
                        enabled = text.isNotEmpty()
                    ) {
                        Text(
                            "Selesai",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Text Input Area - FIXED VERSION
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Gunakan BasicTextField untuk kontrol yang lebih baik
                    BasicTextField(
                        value = text,
                        onValueChange = { newText ->
                            text = newText
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    // Keyboard sudah otomatis show saat focus
                                }
                            },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            color = selectedColor,
                            textAlign = TextAlign.Center,
                            background = backgroundColor,
                            fontFamily = selectedFont.typeface?.let {
                                FontFamily(it)
                            } ?: MaterialTheme.typography.headlineMedium.fontFamily
                        ),
                        cursorBrush = SolidColor(selectedColor),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = "Ketikan sesuatu...",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            color = selectedColor.copy(alpha = 0.5f),
                                            textAlign = TextAlign.Center,
                                            background = backgroundColor,
                                            fontFamily = selectedFont.typeface?.let {
                                                FontFamily(it)
                                            } ?: MaterialTheme.typography.headlineMedium.fontFamily
                                        )
                                    )
                                }
                                innerTextField()
                            
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Color Picker Section - FIXED
                Column (modifier = Modifier.imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                                modifier = Modifier
                                    .height(35.dp)
                                    .width(45.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(backgroundColor)
                                    .border(
                                        width = 2.dp,
                                        color = Color.Gray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        currentColorIndex = (currentColorIndex + 1) % colorOptions.size
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aa",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = selectedColor
                                )
                            }

                        // Font Pilihan
                        LazyRow(
                            modifier = Modifier.height(35.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(fonts) { index, font ->

                                val isSelected = index == selectedFontIndex

                                Box(
                                    modifier = Modifier
                                        .height(35.dp)
                                       // .width(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedFontIndex = index
                                        }
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.Gray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(
                                            if (isSelected) Color(0x33FFFFFF) else Color.Transparent
                                        )
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = font.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = font.typeface?.let { FontFamily(it) }
                                            ?: MaterialTheme.typography.bodyLarge.fontFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }
            VerticalColorPicker(
                onColorChange = {
                    selectedColor = it
                },
                colorThumb = selectedColor,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}
