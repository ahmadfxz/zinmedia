package com.zinmedia.photoeditor.imageeditor

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.zinmedia.photoeditor.ui.EditorBottomSheet
import com.zinmedia.photoeditor.ui.TrayTabs

/** Tray stiker: satu sheet dengan tab Emoji dan Stiker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerTraySheet(
    onEmojiSelected: (String) -> Unit,
    onStickerSelected: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    EditorBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
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
                items(EmojiData.emojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEmojiSelected(emoji) },
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
                items(StickerData.stickerUrls) { url ->
                    StickerCell(url = url, onStickerSelected = onStickerSelected)
                }
            }
        }
    }
}

@Composable
private fun StickerCell(
    url: String,
    onStickerSelected: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isLoading) { loadSticker(context, url, onStickerSelected) }
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).size(256, 256).build(),
            contentDescription = "Stiker",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onLoading = { isLoading = true },
            onSuccess = { isLoading = false },
            onError = { isLoading = false },
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

private val TrayHeight = 360.dp

/** Muat stiker sebagai bitmap software (dibutuhkan engine editor untuk digambar ke Canvas). */
private fun loadSticker(
    context: Context,
    url: String,
    onLoaded: (Bitmap) -> Unit,
) {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(400, 400)
        .allowHardware(false)
        .target { image -> onLoaded(image.toBitmap()) }
        .build()
    SingletonImageLoader.get(context).enqueue(request)
}

object StickerData {
    // Image Urls from flaticon(https://www.flaticon.com/stickers-pack/food-289)
    val stickerUrls = listOf(
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
}

object EmojiData {
    val emojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
        "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
        "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
        "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
        "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
        "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
        "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
        "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
        "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈",
        "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾",
        "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿",
        "😾", "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤏", "✌️", "🤞",
        "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍",
        "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
        "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦵", "🦿", "🦶", "👣",
        "👂", "🦻", "👃", "🧠", "🦷", "🦴", "👀", "👁️", "👅", "👄",
        "💋", "🩸", "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
        "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝"
    )
}