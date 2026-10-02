package com.zinmedia.videoeditor.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zinmedia.videoeditor.data.FontItem
import com.zinmedia.videoeditor.ui.EditorColors
import com.zinmedia.videoeditor.ui.EditorDoneButton
import com.zinmedia.videoeditor.ui.EditorTopBar
import com.zinmedia.videoeditor.ui.VerticalColorPicker

/** Latar teks yang bisa diputar lewat tombol "A": tanpa latar lalu beberapa warna. */
private val TextBackgrounds = listOf(
    Color.Transparent,
    Color.White,
    Color.Black,
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFFFFA000),
)

/**
 * Mode teks: layar diredupkan, teks besar di tengah, slider warna di kanan,
 * dan pilihan latar + font di atas keyboard.
 */
@Composable
fun TextEditorDialog(
    initialText: String = "",
    initialColor: Color = Color.White,
    fonts: List<FontItem>,
    initialBackgroundColor: Color,
    onDismissRequest: () -> Unit,
    onTextEdited: (String, Color, Int, Int) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var textColor by remember { mutableStateOf(initialColor) }
    var fontIndex by remember { mutableIntStateOf(0) }
    var backgroundIndex by remember {
        mutableIntStateOf(TextBackgrounds.indexOf(initialBackgroundColor).coerceAtLeast(0))
    }
    val background = TextBackgrounds[backgroundIndex]
    val fontFamily = fonts.getOrNull(fontIndex)?.typeface?.let { FontFamily(it) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun dismiss() {
        keyboard?.hide()
        onDismissRequest()
    }

    fun done() {
        if (text.isNotBlank()) {
            onTextEdited(text, background, contrastTextColor(textColor, background).toArgb(), fontIndex)
        }
        dismiss()
    }

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
                    TextBackgroundButton(
                        background = background,
                        textColor = textColor,
                        onClick = { backgroundIndex = (backgroundIndex + 1) % TextBackgrounds.size },
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
                    val style = TextStyle(
                        color = contrastTextColor(textColor, background),
                        fontSize = 30.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        fontFamily = fontFamily,
                        background = background,
                    )
                    BasicTextField(
                        value = text,
                        onValueChange = { newText ->
                            // Teks dirender ke video, jadi panjangnya dibatasi.
                            val lines = newText.count { it == '\n' } + 1
                            if (newText.length <= MaxTextChars && lines <= MaxTextLines) text = newText
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = style,
                        cursorBrush = SolidColor(EditorColors.Accent),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                if (text.isEmpty()) {
                                    Text(
                                        "Ketik teks",
                                        style = style.copy(color = Color.White.copy(alpha = 0.5f), background = Color.Transparent),
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
                onColorChange = { textColor = it },
                colorThumb = textColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 120.dp, end = 10.dp),
            )
        }
    }
}

/** Tombol "A" dalam kotak: menampilkan gaya latar teks yang aktif. */
@Composable
private fun TextBackgroundButton(
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(EditorColors.IconContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .border(1.5.dp, Color.White, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = contrastTextColor(textColor, background), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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

/** Saat teks berlatar dan warnanya sama dengan latar, pakai warna kontras agar tetap terbaca. */
private fun contrastTextColor(textColor: Color, background: Color): Color =
    if (background != Color.Transparent && background == textColor) {
        if (background == Color.White) Color.Black else Color.White
    } else {
        textColor
    }

private const val MaxTextChars = 50
private const val MaxTextLines = 5
