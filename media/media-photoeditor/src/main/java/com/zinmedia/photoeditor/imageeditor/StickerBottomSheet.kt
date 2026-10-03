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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.zinmedia.photoeditor.PhotoEditorConfig
import com.zinmedia.photoeditor.R
import com.zinmedia.photoeditor.ui.EditorBottomSheet
import com.zinmedia.photoeditor.ui.TrayTabs

/** Tray stiker: satu sheet dengan tab Emoji dan Stiker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerTraySheet(
    onEmojiSelected: (String) -> Unit,
    onStickerSelected: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val emojis = PhotoEditorConfig.emojis
    val stickers = PhotoEditorConfig.stickers
    val tabs = buildList {
        if (emojis.isNotEmpty()) add(TrayTab.Emoji)
        if (stickers.isNotEmpty()) add(TrayTab.Sticker)
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tab = tabs.getOrNull(selected) ?: tabs.firstOrNull() ?: return

    EditorBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (tabs.size > 1) {
            TrayTabs(
                tabs = tabs.map { stringResource(it.label) },
                selected = tabs.indexOf(tab),
                onSelect = { selected = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        when (tab) {
            TrayTab.Emoji -> LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrayHeight),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                items(emojis) { emoji ->
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

            TrayTab.Sticker -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrayHeight),
                contentPadding = PaddingValues(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(stickers) { url ->
                    StickerCell(url = url, onStickerSelected = onStickerSelected)
                }
            }
        }
    }
}

private enum class TrayTab(val label: Int) {
    Emoji(R.string.zm_tab_emoji),
    Sticker(R.string.zm_tab_sticker),
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
            contentDescription = stringResource(R.string.zm_sticker),
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
