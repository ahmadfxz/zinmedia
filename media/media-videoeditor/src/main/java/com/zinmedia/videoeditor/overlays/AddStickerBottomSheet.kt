package com.zinmedia.videoeditor.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zinmedia.videoeditor.ui.TrayTabs

/** Isi tray stiker: tab Emoji dan Stiker. */
@Composable
fun StickerBottomSheetContent(
    onEmojiClick: (String) -> Unit,
    onStickerClick: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    TrayTabs(
        tabs = listOf("Emoji", "Stiker"),
        selected = tab,
        onSelect = { tab = it },
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(12.dp))
    when (tab) {
        0 -> LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height(TrayHeight),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            items(TrayEmojis) { emoji ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onEmojiClick(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 28.sp)
                }
            }
        }

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(TrayHeight),
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(stickerUrls) { url ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onStickerClick(url) }
                        .padding(6.dp),
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Stiker",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

private val TrayHeight = 360.dp

private val TrayEmojis = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
    "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
    "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
    "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
    "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
    "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
    "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
    "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
    "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈",
    "👋", "👌", "✌️", "🤞", "🤟", "🤘", "👍", "👎", "👏", "🙌",
    "🙏", "💪", "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
    "💔", "💕", "💯", "🔥", "✨", "🎉", "🎂", "🌹", "⭐", "☀️",
)

private val stickerUrls = listOf(
    "https://cdn-icons-png.flaticon.com/256/4392/4392471.png",
    "https://cdn-icons-png.flaticon.com/256/4392/4392522.png",
    "https://cdn-icons-png.flaticon.com/256/4213/4213612.png",
    "https://cdn-icons-png.flaticon.com/256/4213/4213605.png",
    "https://cdn-icons-png.flaticon.com/256/4213/4213517.png",
    "https://cdn-icons-png.flaticon.com/256/4228/4228685.png",
    "https://cdn-icons-png.flaticon.com/256/4329/4329960.png",
    "https://cdn-icons-png.flaticon.com/256/6702/6702479.png",
    "https://cdn-icons-png.flaticon.com/256/6852/6852961.png",
    "https://cdn-icons-png.flaticon.com/256/6852/6852993.png",
    "https://cdn-icons-png.flaticon.com/256/8137/8137252.png",
    "https://cdn-icons-png.flaticon.com/256/8137/8137255.png",
    "https://cdn-icons-png.flaticon.com/256/8137/8137228.png",
    "https://cdn-icons-png.flaticon.com/256/8137/8137225.png",
    "https://cdn-icons-png.flaticon.com/256/8137/8137202.png",
    "https://cdn-icons-png.flaticon.com/256/4392/4392452.png",
    "https://cdn-icons-png.flaticon.com/256/4392/4392455.png",
    "https://cdn-icons-png.flaticon.com/256/4392/4392459.png",
    "https://cdn-icons-png.flaticon.com/256/4392/4392462.png",
    "https://cdn-icons-png.flaticon.com/256/4392/4392465.png",
    "https://cdn-icons-png.flaticon.com/256/4392/4392467.png",
    "https://cdn-icons-png.flaticon.com/256/4392/4392469.png",
)