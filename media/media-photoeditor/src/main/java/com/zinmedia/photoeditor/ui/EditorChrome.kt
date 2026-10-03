package com.zinmedia.photoeditor.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.imePadding
import androidx.annotation.RestrictTo
import androidx.compose.ui.res.stringResource
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zinmedia.photoeditor.R

/** Palet warna editor (mode gelap). */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object EditorColors {
    public val Background: Color = Color.Black
    public val Field: Color = Color(0xFF1F2C34)
    public val Accent: Color = Color(0xFF21C063)
    public val IconContainer: Color = Color.Black.copy(alpha = 0.35f)
    public val TextSecondary: Color = Color.White.copy(alpha = 0.6f)
    public val BottomBar: Color = Color.Black.copy(alpha = 0.6f)
    public val Dialog: Color = Color(0xFF233138)
}

private val QuickEmojis = listOf(
    "😀", "😂", "😍", "🥰", "😎", "😭", "😡", "🥳", "😴", "🤔",
    "❤️", "🔥", "👍", "🙏", "👏", "🎉", "💯", "✨", "😇", "🤩",
)

/** Tombol ikon bulat dengan latar gelap transparan, terbaca di atas media apa pun. */
@Composable
internal fun EditorIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    // Area sentuh 48dp (minimum aksesibilitas), lingkaran visual 40dp.
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (selected) Color.White else EditorColors.IconContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = when {
                    selected -> Color.Black
                    enabled -> Color.White
                    else -> Color.White.copy(alpha = 0.35f)
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Baris atas: tombol tutup di kiri, aksi di kanan. */
@Composable
internal fun EditorTopBar(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes closeIcon: Int = R.drawable.zm_ic_close,
    closeDescription: String = stringResource(R.string.zm_close),
    closeEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EditorIconButton(
            icon = closeIcon,
            contentDescription = closeDescription,
            onClick = onClose,
            enabled = closeEnabled,
        )
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Tombol "Selesai" berbentuk pil putih. */
@Composable
internal fun EditorDoneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = stringResource(R.string.zm_done),
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Gradasi gelap di belakang toolbar atas/bawah agar ikon tetap terbaca. */
@Composable
internal fun EditorScrim(top: Boolean, modifier: Modifier = Modifier) {
    val colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (top) 140.dp else 220.dp)
            .background(Brush.verticalGradient(if (top) colors else colors.reversed())),
    )
}

/** Petunjuk "Filter" di atas kolom keterangan; panah menunjukkan buka/tutup. */
@Composable
internal fun FilterHint(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(if (expanded) R.drawable.zm_ic_expand_more else R.drawable.zm_ic_expand_less),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Text(stringResource(R.string.zm_filter), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Bagian bawah editor: kolom keterangan berbentuk pil, lalu bar hitam transparan selebar layar
 * (sampai di belakang navigation bar) berisi chip penerima di kiri dan tombol kirim hijau di kanan.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Composable
public fun EditorCaptionBar(
    caption: String,
    onCaptionChange: (String) -> Unit,
    recipientLabel: String,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.zm_caption_hint),
) {
    val focusManager = LocalFocusManager.current
    var showEmojis by rememberSaveable { mutableStateOf(false) }
    var captionFocused by remember { mutableStateOf(false) }

    // Ikut naik hanya untuk keyboard milik kolom keterangan ini, bukan keyboard mode teks
    // (atau sisa animasinya saat mode teks ditutup).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (captionFocused) Modifier.imePadding() else Modifier)
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
            AnimatedVisibility(visible = showEmojis) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(QuickEmojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(EditorColors.Field)
                                .clickable { onCaptionChange((caption + emoji).take(MaxCaptionLength)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emoji, fontSize = 22.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(EditorColors.Field)
                    .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { showEmojis = !showEmojis },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.zm_ic_emoji),
                        contentDescription = stringResource(R.string.zm_emoji),
                        tint = if (showEmojis) EditorColors.Accent else EditorColors.TextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = caption,
                    onValueChange = { onCaptionChange(it.take(MaxCaptionLength)) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                        .onFocusChanged { captionFocused = it.isFocused },
                    maxLines = 4,
                    cursorBrush = SolidColor(EditorColors.Accent),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp, lineHeight = 21.sp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (caption.isEmpty()) {
                                Text(placeholder, color = EditorColors.TextSecondary, fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                    },
                )
            }

        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EditorColors.BottomBar)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(EditorColors.Field)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.zm_ic_status),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(recipientLabel, color = Color.White, fontSize = 14.sp, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (sendEnabled) EditorColors.Accent else EditorColors.Accent.copy(alpha = 0.4f))
                    .clickable(enabled = sendEnabled, role = Role.Button) {
                        focusManager.clearFocus()
                        showEmojis = false
                        onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.zm_ic_send),
                    contentDescription = stringResource(R.string.zm_send),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Tab berbentuk pil; tab aktif berwarna terang. */
@Composable
internal fun TrayTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(EditorColors.Field)
            .padding(4.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    color = if (active) Color.White else EditorColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Dialog konfirmasi saat menutup editor yang sudah diubah:
 * kartu gelap membulat, judul + penjelasan, dan tombol teks hijau di kanan bawah.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Composable
public fun DiscardChangesDialog(
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(EditorColors.Dialog)
                .padding(start = 24.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
        ) {
            Text(stringResource(R.string.zm_discard_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.zm_discard_message),
                color = EditorColors.TextSecondary,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(end = 12.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                DialogTextButton(stringResource(R.string.zm_cancel), onDismiss)
                DialogTextButton(stringResource(R.string.zm_discard), onDiscard)
            }
        }
    }
}

@Composable
private fun DialogTextButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = EditorColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private const val MaxCaptionLength = 700
