package com.zinmedia.videoeditor.overlays

import com.zinmedia.videoeditor.R
import androidx.compose.ui.res.stringResource
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
import com.zinmedia.videoeditor.VideoEditorConfig
import com.zinmedia.videoeditor.ui.TrayTabs

/** Isi tray stiker: tab Emoji dan Stiker. */
@Composable
internal fun StickerBottomSheetContent(
    onEmojiClick: (String) -> Unit,
    onStickerClick: (String) -> Unit,
) {
    val emojis = VideoEditorConfig.emojis
    val stickers = VideoEditorConfig.stickers
    val tabs = buildList {
        if (emojis.isNotEmpty()) add(0)
        if (stickers.isNotEmpty()) add(1)
    }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tab = tabs.getOrNull(selected) ?: tabs.firstOrNull() ?: return

    if (tabs.size > 1) {
        TrayTabs(
            tabs = tabs.map { if (it == 0) stringResource(R.string.zm_tab_emoji) else stringResource(R.string.zm_tab_sticker) },
            selected = tabs.indexOf(tab),
            onSelect = { selected = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
    when (tab) {
        0 -> LazyVerticalGrid(
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
            items(stickers) { url ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onStickerClick(url) }
                        .padding(6.dp),
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = stringResource(R.string.zm_sticker),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

private val TrayHeight = 360.dp
