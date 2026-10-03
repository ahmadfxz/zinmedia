package com.zinmedia.photoeditor.imageeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zinmedia.photoeditor.R
import com.zinmedia.photoeditor.domain.model.FontItem
import com.zinmedia.photoeditor.textlayer.TextLayer
import com.zinmedia.photoeditor.textlayer.TextLayerAlign
import com.zinmedia.photoeditor.textlayer.TextLayerBackground
import com.zinmedia.photoeditor.textlayer.TextLayerMetrics
import com.zinmedia.photoeditor.textlayer.glyphMetrics
import com.zinmedia.photoeditor.textlayer.textLayerBackground
import com.zinmedia.photoeditor.textlayer.textLayerStyle
import com.zinmedia.photoeditor.ui.EditorColors
import com.zinmedia.photoeditor.ui.EditorDoneButton
import com.zinmedia.photoeditor.ui.EditorIconButton
import com.zinmedia.photoeditor.ui.EditorTopBar
import com.zinmedia.photoeditor.ui.VerticalColorPicker

/**
 * Mode teks: layar diredupkan, teks besar di tengah, slider warna di kanan, perataan & gaya latar
 * di atas, dan pilihan font di atas keyboard. Tampilan latar & susunan baris di sini identik dengan
 * hasil akhir (lihat [com.zinmedia.photoeditor.textlayer.renderTextLayer]).
 *
 * @param onDone dipanggil dengan lapisan teks dan lebar kolom teks (px) untuk render hasil.
 */
@Composable
internal fun TextEditorDialog(
    initial: TextLayer,
    fonts: List<FontItem>,
    onDismissRequest: () -> Unit,
    onDone: (layer: TextLayer, layoutWidthPx: Int) -> Unit,
) {
    var value by remember {
        mutableStateOf(TextFieldValue(initial.text, selection = TextRange(initial.text.length)))
    }
    var color by remember { mutableIntStateOf(initial.color) }
    var background by remember { mutableStateOf(initial.background) }
    var align by remember { mutableStateOf(initial.align) }
    var fontIndex by remember { mutableIntStateOf(initial.fontIndex) }
    var layoutWidthPx by remember { mutableIntStateOf(0) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val layer = TextLayer(value.text, color, background, align, fontIndex)
    val fontFamily = fonts.getOrNull(fontIndex)?.typeface?.let { FontFamily(it) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun dismiss() {
        keyboard?.hide()
        onDismissRequest()
    }

    fun done() {
        if (value.text.isNotBlank() && layoutWidthPx > 0) onDone(layer, layoutWidthPx)
        dismiss()
    }

    val density = LocalDensity.current
    val typeface = fonts.getOrNull(fontIndex)?.typeface
    val glyphs = remember(typeface, density) {
        glyphMetrics(typeface, with(density) { TextLayerMetrics.FontSize.toPx() })
    }
    // Gaya yang sama persis dengan renderer hasil (lihat renderTextLayer).
    val textStyle = textLayerStyle(layer, fontFamily, glyphs, density)

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::done,
                )
        ) {
            Column(Modifier.fillMaxSize()) {
                EditorTopBar(onClose = ::dismiss) {
                    EditorIconButton(
                        icon = align.icon(),
                        contentDescription = stringResource(R.string.zm_text_align),
                        onClick = { align = TextLayerAlign.entries[(align.ordinal + 1) % TextLayerAlign.entries.size] },
                    )
                    TextBackgroundButton(
                        background = background,
                        onClick = {
                            background = TextLayerBackground.entries[(background.ordinal + 1) % TextLayerBackground.entries.size]
                        },
                    )
                    EditorDoneButton(onClick = ::done)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { layoutWidthPx = it.width }
                            .focusRequester(focusRequester)
                            .drawBehind {
                                val layout = textLayout
                                if (layer.backgroundColor != null && layout != null && value.text.isNotEmpty()) {
                                    drawPath(textLayerBackground(layout, glyphs, density), Color(layer.backgroundColor!!))
                                }
                            },
                        textStyle = textStyle,
                        onTextLayout = { textLayout = it },
                        cursorBrush = SolidColor(if (background == TextLayerBackground.None) EditorColors.Accent else Color(layer.textColor)),
                        decorationBox = { inner ->
                            Box {
                                if (value.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.zm_type_text),
                                        style = textStyle.copy(color = Color.White.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }

                FontChips(
                    fonts = fonts,
                    selected = fontIndex,
                    onSelect = { fontIndex = it },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 12.dp),
                )
            }

            VerticalColorPicker(
                onColorChange = { color = it.toArgb() },
                colorThumb = Color(color),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 120.dp, end = 10.dp),
            )
        }
    }
}

/** Tombol "A" dalam kotak; tampilannya mengikuti gaya latar yang aktif. */
@Composable
private fun TextBackgroundButton(
    background: TextLayerBackground,
    onClick: () -> Unit,
) {
    val boxColor = when (background) {
        TextLayerBackground.None -> Color.Transparent
        TextLayerBackground.Solid -> Color.White
        TextLayerBackground.Translucent -> Color.White.copy(alpha = 0.45f)
    }
    val letterColor = if (background == TextLayerBackground.Solid) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.zm_text_background), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(EditorColors.IconContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(boxColor, RoundedCornerShape(6.dp))
                    .border(1.5.dp, Color.White, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", color = letterColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FontChips(
    fonts: List<FontItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        itemsIndexed(fonts) { index, font ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) Color.White else EditorColors.Field)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = font.name,
                    color = if (active) Color.Black else Color.White,
                    fontSize = 14.sp,
                    fontFamily = font.typeface?.let { FontFamily(it) },
                    maxLines = 1,
                )
            }
        }
    }
}


private fun TextLayerAlign.icon(): Int = when (this) {
    TextLayerAlign.Left -> R.drawable.zm_ic_align_left
    TextLayerAlign.Center -> R.drawable.zm_ic_align_center
    TextLayerAlign.Right -> R.drawable.zm_ic_align_right
}
