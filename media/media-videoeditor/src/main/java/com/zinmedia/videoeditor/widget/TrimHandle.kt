package com.zinmedia.videoeditor.widget

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.Outline
import compose.icons.evaicons.fill.Close
import compose.icons.evaicons.fill.PlayCircle
import compose.icons.evaicons.outline.PlayCircle
import compose.icons.evaicons.outline.Search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun TrimHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(20.dp)
            .fillMaxHeight()
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .align(Alignment.Center)
                .background(Color.White)
        )
        Box(
            Modifier
                .size(15.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
internal fun ThumbnailStrip(
    videoUri: Uri,
    videoDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnails = rememberVideoThumbnails(context, videoUri)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        thumbnails.forEach { img ->
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(90.dp)
                    .fillMaxHeight()
            )
        }
    }

}

@Composable
internal fun rememberVideoThumbnails(
    context: Context,
    videoUri: Uri,
    targetThumbnailCount: Int = 6   // Misal 12 thumbnail
): List<ImageBitmap> {

    var thumbnails by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }

    LaunchedEffect(videoUri) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong() ?: 0L

            // ❗ Interval menyesuaikan durasi video
            val intervalMs = (durationMs / targetThumbnailCount).coerceAtLeast(500L)

            val list = mutableListOf<ImageBitmap>()

            var time = 0L
            while (time < durationMs) {
                val bmp = retriever.getFrameAtTime(
                    time * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                bmp?.let { list.add(it.asImageBitmap()) }
                time += intervalMs
            }

            retriever.release()
            thumbnails = list
        }
    }

    return thumbnails
}

@Composable
internal fun TrimControls(
    videoUri: Uri,
    videoDurationMs: Long,
    onTrimChanged: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    maxTrimMs: Long = 10_000,
    currentPlayTimeMs: Long = 0L,
) {
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var startPx by remember { mutableFloatStateOf(0f) }
    var endPx by remember { mutableFloatStateOf(0f) }
    // ==========================
    // Pixel → Millisecond
    // ==========================
    fun pxToMs(px: Float): Long {
        if (totalWidthPx <= 0f) return 0L
        val ratio = (px / totalWidthPx).coerceIn(0f, 1f)
        return (ratio * videoDurationMs).toLong()
    }

    // Millisecond → Pixel
    fun msToPx(ms: Long): Float {
        if (videoDurationMs <= 0L) return 0f
        val ratio = ms / videoDurationMs.toFloat()
        return (ratio * totalWidthPx).coerceIn(0f, totalWidthPx)
    }

    val currentStart = pxToMs(startPx)
    val currentEnd = pxToMs(endPx)
    val currentDuration = (currentEnd - currentStart).coerceAtLeast(0)
    val currentPlayTimePx = msToPx(currentPlayTimeMs)

    // Callback trim
    LaunchedEffect(currentStart, currentEnd) {
        if (currentDuration in 1000..maxTrimMs) {
            delay(100)
            onTrimChanged(currentStart, currentEnd)
        }
    }

    Box(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(40.dp)
            .onGloballyPositioned { layout ->
                totalWidthPx = layout.size.width.toFloat()

                if (endPx == 0f && totalWidthPx > 0f) {
                    val endMs = min(maxTrimMs, videoDurationMs)
                    endPx = msToPx(endMs)
                }
            }
    ) {
        // ==============================
        // TIMELINE MARKERS + THUMBNAIL
        // ==============================
        ThumbnailStrip(
            videoDurationMs = videoDurationMs,
            videoUri = videoUri
        )

        // ==============================
        // SELECTED AREA
        // ==============================
        val selectionWidthPx = (endPx - startPx).coerceAtLeast(0f)
        val selectionWidthDp = with(LocalDensity.current) { selectionWidthPx.toDp() }

        // AREA yang dipilih
        Box(
            modifier = Modifier
                .offset { IntOffset(startPx.roundToInt(), 0) }
                .width(selectionWidthDp)
                .fillMaxHeight()
                .border(BorderStroke(2.dp, Color.White))
                .background(Color.White.copy(0.15f))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newStart = (startPx + delta)
                            .coerceIn(0f, totalWidthPx - selectionWidthPx)
                        val newEnd = newStart + selectionWidthPx

                        if (newEnd <= totalWidthPx) {
                            startPx = newStart
                            endPx = newEnd
                        }
                    }
                )
        ) {
            // ==============================
            // LABEL DURASI TERPILIH
            // ==============================
            val centerX = (endPx - startPx) / 2

            Box(
                modifier = Modifier
                    .offset { IntOffset(centerX.roundToInt(), 0) }
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "${currentDuration / 1000}s",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 12.sp
                )
            }

            CurrentTimeIndicator(
                positionPx = currentPlayTimePx,
            )
        }

        // ==============================
        // LEFT HANDLE
        // ==============================
        TrimHandle(
            modifier = Modifier
                .offset { IntOffset((startPx - 25).roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newStart = (startPx + delta)
                            .coerceIn(0f, endPx - msToPx(3000))

                        val newDuration = pxToMs(endPx) - pxToMs(newStart)
                        if (newDuration in 3000..maxTrimMs) startPx = newStart
                    }
                )
        )

        // ==============================
        // RIGHT HANDLE
        // ==============================
        TrimHandle(
            modifier = Modifier
                .offset { IntOffset((endPx - 30).roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newEnd = (endPx + delta)
                            .coerceIn(startPx + msToPx(3000), totalWidthPx)

                        val newDuration = pxToMs(newEnd) - pxToMs(startPx)
                        if (newDuration in 3000..maxTrimMs) endPx = newEnd
                    }
                )
        )
    }
}

@Composable
private fun CurrentTimeIndicator(
    positionPx: Float,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(positionPx.roundToInt(), 0) }
            .width(2.dp)
            .height(40.dp)
            .background(Color.White)
    )
}

@Composable
private fun CurrentTimeIndicator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(2.dp)
            .height(300.dp)
            .background(Color.Red)
    )
}

