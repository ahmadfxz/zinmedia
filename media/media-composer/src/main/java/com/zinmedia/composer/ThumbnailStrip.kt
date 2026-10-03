package com.zinmedia.composer

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import com.zinmedia.photoeditor.ui.EditorColors

private val ThumbSize = 52.dp
private val ThumbShape = RoundedCornerShape(10.dp)

/**
 * Deretan thumbnail media: ketuk untuk berpindah, "×" pada item terpilih untuk menghapus,
 * dan kotak "+" untuk menambah media selama belum mencapai batas.
 */
@Composable
internal fun ThumbnailStrip(
    items: List<ComposerItem>,
    selected: Int,
    canAdd: Boolean,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val removeLabel = stringResource(R.string.zm_composer_remove)
    val addLabel = stringResource(R.string.zm_composer_add)
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem(selected.coerceAtLeast(0)) }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val active = index == selected
            Box {
                Box(
                    modifier = Modifier
                        .size(ThumbSize)
                        .clip(ThumbShape)
                        .background(EditorColors.Field)
                        .then(if (active) Modifier.border(2.dp, Color.White, ThumbShape) else Modifier)
                        .clickable(role = Role.Tab) { onSelect(index) },
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.uri)
                            .apply {
                                if (item.type == MediaType.Video) {
                                    decoderFactory(VideoFrameDecoder.Factory())
                                    videoFrameMillis(0)
                                }
                            }
                            .size(160)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (item.type == MediaType.Video) {
                        Text(
                            text = "▶",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp),
                        )
                    }
                }
                if (active && items.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(role = Role.Button, onClickLabel = removeLabel) { onRemove(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (canAdd) {
            item(key = "add") {
                Box(
                    modifier = Modifier
                        .size(ThumbSize)
                        .clip(ThumbShape)
                        .background(EditorColors.Field)
                        .clickable(role = Role.Button, onClickLabel = addLabel, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}
