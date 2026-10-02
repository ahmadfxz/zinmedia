package com.jernih.videoeditor.widget

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

//
//@Composable
//fun TrimHandle(modifier: Modifier = Modifier) {
//    Box(
//        modifier = modifier
//            .width(20.dp)
//            .fillMaxHeight()
//            .background(Color.White)
//    ) {
//        Box(
//            Modifier
//                .width(3.dp)
//                .height(10.dp)
//                .align(Alignment.Center)
//                .background(Color.Gray)
//        )
//    }
//}

@Composable
fun TrimHandle(modifier: Modifier = Modifier) {
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
fun ThumbnailStrip(
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

//    Canvas(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(24.dp)
//            .padding(horizontal = 8.dp)
//    ) {
//        if (videoDurationMs <= 0) return@Canvas
//
//        val intervalMs = 5000L
//        val totalMarkers = (videoDurationMs / intervalMs).toInt()
//        val pxPerMs = size.width / videoDurationMs
//
//        for (i in 0..totalMarkers) {
//            val timeMs = i * intervalMs
//            val x = timeMs * pxPerMs
//
//            drawLine(
//                color = Color.White,
//                start = Offset(x, 0f),
//                end = Offset(x, size.height * 0.4f),
//                strokeWidth = 2f
//            )
//
//            drawContext.canvas.nativeCanvas.apply {
//                val label = "${i * 5}s"
//                val textPaint = android.graphics.Paint().apply {
//                    color = android.graphics.Color.WHITE
//                    textSize = 24f
//                    textAlign = android.graphics.Paint.Align.CENTER
//                }
//                drawText(label, x, size.height, textPaint)
//            }
//        }
//    }

}

@Composable
fun rememberVideoThumbnails(
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



//@Composable
//fun rememberVideoThumbnails(
//    context: Context,
//    videoUri: Uri,
//    intervalMs: Long = 5000L
//): List<ImageBitmap> {
//
//    var thumbnails by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
//
//    LaunchedEffect(videoUri) {
//        withContext(Dispatchers.IO) {
//            val retriever = MediaMetadataRetriever()
//            retriever.setDataSource(context, videoUri)
//
//            val durationStr = retriever.extractMetadata(
//                MediaMetadataRetriever.METADATA_KEY_DURATION
//            ) ?: "0"
//            val durationMs = durationStr.toLong()
//
//            val list = mutableListOf<ImageBitmap>()
//
//            var time = 0L
//            while (time < durationMs) {
//                val bmp = retriever.getFrameAtTime(
//                    time * 1000,  // µs
//                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
//                )
//
//                bmp?.let {
//                    list.add(it.asImageBitmap())
//                }
//
//                time += intervalMs
//            }
//
//            retriever.release()
//
//            thumbnails = list
//        }
//    }
//
//    return thumbnails
//}


@Composable
fun TrimControls(
    videoUri: Uri,
    videoDurationMs: Long,
    maxTrimMs: Long = 10_000,
    currentPlayTimeMs: Long = 0L,
    onTrimChanged: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var totalWidthPx by remember { mutableStateOf(0f) }
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
            //    modifier = Modifier.padding(start = 12.dp)
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

            //  if (currentPlayTimeMs > 0 && currentPlayTimePx > 0) {
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
                // .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newStart = (startPx + delta)
                            .coerceIn(0f, endPx - msToPx(3000))

                        val newDuration = pxToMs(endPx) - pxToMs(newStart)
                        //  if (newDuration <= maxTrimMs) startPx = newStart
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
                // .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newEnd = (endPx + delta)
                            .coerceIn(startPx + msToPx(3000), totalWidthPx)

                        val newDuration = pxToMs(newEnd) - pxToMs(startPx)
                        // if (newDuration <= maxTrimMs) endPx = newEnd
                        if (newDuration in 3000..maxTrimMs) endPx = newEnd
                    }
                )
        )
    }
}

//
//@Composable
//fun TrimControls(
//    videoUri: Uri,
//    videoDurationMs: Long,
//    maxTrimMs: Long = 10_000,
//    currentPlayTimeMs: Long = 0L,
//    onTrimChanged: (Long, Long) -> Unit,
//    modifier: Modifier = Modifier
//) {
//    var totalWidthPx by remember { mutableStateOf(0f) }
//    var startPx by remember { mutableFloatStateOf(0f) }
//    var endPx by remember { mutableFloatStateOf(0f) }
//
//    val scrollState = rememberScrollState()
//    var containerWidthPx by remember { mutableStateOf(0f) }
//
//    // 1 detik = 30dp (mirip CapCut)
//    val pxPerSecondDp = 15.dp
//
//    // Hitung total width timeline
//    val timelineWidthDp = with(LocalDensity.current) {
//        (videoDurationMs / 1000f * pxPerSecondDp.value).dp
//    }
//
//    // ==========================
//    // Pixel → Millisecond
//    // ==========================
//    fun pxToMs(px: Float): Long {
//        if (totalWidthPx <= 0f) return 0L
//        val ratio = (px / totalWidthPx).coerceIn(0f, 1f)
//        return (ratio * videoDurationMs).toLong()
//    }
//
//    // Millisecond → Pixel
//    fun msToPx(ms: Long): Float {
//        if (videoDurationMs <= 0L) return 0f
//        val ratio = ms / videoDurationMs.toFloat()
//        return (ratio * totalWidthPx).coerceIn(0f, totalWidthPx)
//    }
//
//    val currentStart = pxToMs(startPx)
//    val currentEnd = pxToMs(endPx)
//    val currentDuration = (currentEnd - currentStart).coerceAtLeast(0)
//    val currentPlayTimePx = msToPx(currentPlayTimeMs)
//    // Auto-scroll ketika handle mendekati batas
//    LaunchedEffect(startPx, endPx, totalWidthPx) {
//        val visibleStart = scrollState.value.toFloat()
//        val visibleEnd = visibleStart + containerWidthPx
//
//        // Auto-scroll ketika handle kanan mendekati batas kanan
//        if (endPx > visibleEnd - 50f) {
//            val targetScroll = (endPx - containerWidthPx + 100f).toInt()
//            scrollState.animateScrollTo(targetScroll.coerceAtLeast(0))
//        }
//        // Auto-scroll ketika handle kiri mendekati batas kiri
//        else if (startPx < visibleStart + 50f) {
//            val targetScroll = (startPx - 50f).toInt()
//            scrollState.animateScrollTo(targetScroll.coerceAtLeast(0))
//        }
//    }
//
//    // Callback trim
//    LaunchedEffect(currentStart, currentEnd) {
//        if (currentDuration in 1000..maxTrimMs) {
//            delay(100)
//            onTrimChanged(currentStart, currentEnd)
//        }
//    }
//
//    // ==============================
//    // MAIN UI
//    // ==============================
//    Box(
//        modifier = modifier
//            .fillMaxWidth()
//            .onGloballyPositioned { layout ->
//                containerWidthPx = layout.size.width.toFloat()
//            }
//    ) {
//        // Scrollable timeline
//        Box(
//            modifier = Modifier
//                .horizontalScroll(scrollState)
//                .padding(horizontal = 20.dp)
//                .width(timelineWidthDp)
//                .height(40.dp)
//                .onGloballyPositioned { layout ->
//                    totalWidthPx = layout.size.width.toFloat()
//
//                    if (endPx == 0f && totalWidthPx > 0f) {
//                        val endMs = min(maxTrimMs, videoDurationMs)
//                        endPx = msToPx(endMs)
//                    }
//                }
//        ) {
//            // ==============================
//            // TIMELINE MARKERS + THUMBNAIL
//            // ==============================
//            ThumbnailStrip(
//                videoDurationMs = videoDurationMs,
//                videoUri = videoUri
//                //    modifier = Modifier.padding(start = 12.dp)
//            )
//
//            // ==============================
//            // SELECTED AREA
//            // ==============================
//            val selectionWidthPx = (endPx - startPx).coerceAtLeast(0f)
//            val selectionWidthDp = with(LocalDensity.current) { selectionWidthPx.toDp() }
//
//            // AREA yang dipilih
//            Box(
//                modifier = Modifier
//                    .offset { IntOffset(startPx.roundToInt(), 0) }
//                    .width(selectionWidthDp)
//                    .fillMaxHeight()
//                    .border(BorderStroke(2.dp, Color.White))
//                    .background(Color.White.copy(0.15f))
//                    .draggable(
//                        orientation = Orientation.Horizontal,
//                        state = rememberDraggableState { delta ->
//                            val newStart = (startPx + delta)
//                                .coerceIn(0f, totalWidthPx - selectionWidthPx)
//                            val newEnd = newStart + selectionWidthPx
//
//                            if (newEnd <= totalWidthPx) {
//                                startPx = newStart
//                                endPx = newEnd
//                            }
//                        }
//                    )
//            ) {
//                // ==============================
//                // LABEL DURASI TERPILIH
//                // ==============================
//                val centerX = (endPx - startPx) / 2
//
//                Box(
//                    modifier = Modifier
//                        .offset { IntOffset(centerX.roundToInt(), 0) }
//                        .clip(RoundedCornerShape(4.dp))
//                        .background(Color.White)
//                        .padding(horizontal = 8.dp)
//                ) {
//                    Text(
//                        text = "${currentDuration / 1000}s",
//                        fontWeight = FontWeight.Bold,
//                        color = Color.Black,
//                        fontSize = 12.sp
//                    )
//                }
//
//                //  if (currentPlayTimeMs > 0 && currentPlayTimePx > 0) {
//                CurrentTimeIndicator(
//                    positionPx = currentPlayTimePx,
//                )
//            }
//
//            // ==============================
//            // LEFT HANDLE
//            // ==============================
//            TrimHandle(
//                modifier = Modifier
//                    .offset { IntOffset((startPx - 25).roundToInt(), 0) }
//                    // .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
//                    .draggable(
//                        orientation = Orientation.Horizontal,
//                        state = rememberDraggableState { delta ->
//                            val newStart = (startPx + delta)
//                                .coerceIn(0f, endPx - msToPx(3000))
//
//                            val newDuration = pxToMs(endPx) - pxToMs(newStart)
//                            //  if (newDuration <= maxTrimMs) startPx = newStart
//                            if (newDuration in 3000..maxTrimMs) startPx = newStart
//                        }
//                    )
//            )
//
//            // ==============================
//            // RIGHT HANDLE
//            // ==============================
//            TrimHandle(
//                modifier = Modifier
//                    .offset { IntOffset((endPx - 30).roundToInt(), 0) }
//                    // .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
//                    .draggable(
//                        orientation = Orientation.Horizontal,
//                        state = rememberDraggableState { delta ->
//                            val newEnd = (endPx + delta)
//                                .coerceIn(startPx + msToPx(3000), totalWidthPx)
//
//                            val newDuration = pxToMs(newEnd) - pxToMs(startPx)
//                            // if (newDuration <= maxTrimMs) endPx = newEnd
//                            if (newDuration in 3000..maxTrimMs) endPx = newEnd
//                        }
//                    )
//            )
//
//        }
//    }
//}


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
            // .offset { IntOffset(positionPx.roundToInt(), 0) }
            .width(2.dp)
            .height(300.dp)
            .background(Color.Red)
    )
}


@Composable
fun screenCenterOffset(): Offset {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val centerX = with(density) { (configuration.screenWidthDp.dp / 2).toPx() }
    val centerY = with(density) { (configuration.screenHeightDp.dp / 2).toPx() }

    return Offset(centerX, centerY)
}

@Composable
fun screenCenterX(): Float {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    return with(density) { (configuration.screenWidthDp.dp / 2).toPx() }
}

@Composable
fun screenCenterDp(): DpOffset {
    val configuration = LocalConfiguration.current

    val centerX = configuration.screenWidthDp.dp / 2
    val centerY = configuration.screenHeightDp.dp / 2

    return DpOffset(centerX, centerY)
}


//
//@Composable
//fun TrimControls(
//    videoDurationMs: Long,
//    maxTrimMs: Long = 10_000,
//    onTrimChanged: (Long, Long) -> Unit
//) {
//    var totalWidthPx by remember { mutableStateOf(0f) }
//
//    var startPx by remember { mutableFloatStateOf(0f) }
//    var endPx by remember { mutableFloatStateOf(0f) }
//
//    // ==========================
//    // Pixel → Millisecond
//    // ==========================
//    fun pxToMs(px: Float): Long {
//        if (totalWidthPx <= 0f) return 0L
//        val ratio = (px / totalWidthPx).coerceIn(0f, 1f)
//        return (ratio * videoDurationMs).toLong()
//    }
//
//    // Millisecond → Pixel
//    fun msToPx(ms: Long): Float {
//        if (videoDurationMs <= 0L) return 0f
//        val ratio = ms / videoDurationMs.toFloat()
//        return (ratio * totalWidthPx).coerceIn(0f, totalWidthPx)
//    }
//
//    val currentStart = pxToMs(startPx)
//    val currentEnd = pxToMs(endPx)
//    val currentDuration = (currentEnd - currentStart).coerceAtLeast(0)
//
//    // Hanya kirim event jika durasi valid
//    LaunchedEffect(currentStart, currentEnd) {
//        if (currentDuration in 1000..maxTrimMs) {
//            onTrimChanged(currentStart, currentEnd)
//        }
//    }
//
//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(40.dp)
//            .onGloballyPositioned { layout ->
//                totalWidthPx = layout.size.width.toFloat()
//
//                // Set default selection 0 → maxTrim
//                if (endPx == 0f && totalWidthPx > 0f) {
//                    val endMs = min(maxTrimMs, videoDurationMs)
//                    endPx = msToPx(endMs)
//                }
//            }
//            .padding(horizontal = 8.dp)
//    ) {
//
//        // ============================== THUMBNAIL STRIP ==============================
//        ThumbnailStrip(videoDurationMs)
//
//        // ============================== SELECTED AREA ==============================
//        val selectionWidthPx = (endPx - startPx).coerceAtLeast(0f)
//
//        val selectionWidthDp = with(LocalDensity.current) {
//            selectionWidthPx.toDp()
//        }
//
//        Box(
//            modifier = Modifier
//                .offset { IntOffset(startPx.roundToInt(), 0) }
//                // gunakan width dalam PX, jangan .dp !!
//                .width(selectionWidthDp)
//                .fillMaxHeight()
//                .background(Color.White.copy(0.15f))
//                .draggable(
//                    orientation = Orientation.Horizontal,
//                    state = rememberDraggableState { delta ->
//                        val newStart = (startPx + delta)
//                            .coerceIn(0f, totalWidthPx - selectionWidthPx)
//                        val newEnd = newStart + selectionWidthPx
//
//                        // Pastikan tidak melebihi batas
//                        if (newEnd <= totalWidthPx) {
//                            startPx = newStart
//                            endPx = newEnd
//                        }
//                    }
//                )
//        )
//
//        // ============================== LEFT HANDLE ==============================
//        TrimHandle(
//            modifier = Modifier
//                .offset { IntOffset((startPx - 18).roundToInt(), 0) }
//                .draggable(
//                    orientation = Orientation.Horizontal,
//                    state = rememberDraggableState { delta ->
//                        val newStart = (startPx + delta)
//                            .coerceIn(0f, endPx - msToPx(1000))
//
//                        val newDuration = pxToMs(endPx) - pxToMs(newStart)
//                        if (newDuration <= maxTrimMs) startPx = newStart
//                    }
//                )
//                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
//        )
//
//        // ============================== RIGHT HANDLE ==============================
//        TrimHandle(
//            modifier = Modifier
//                .offset { IntOffset((endPx - 2).roundToInt(), 0) }
//                .draggable(
//                    orientation = Orientation.Horizontal,
//                    state = rememberDraggableState { delta ->
//                        val newEnd = (endPx + delta)
//                            .coerceIn(startPx + msToPx(1000), totalWidthPx)
//
//                        val newDuration = pxToMs(newEnd) - pxToMs(startPx)
//                        if (newDuration <= maxTrimMs) endPx = newEnd
//                    }
//                )
//                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
//
//        )
//
//
//        val centerX = startPx + (selectionWidthPx / 2)
//        Box(
//            contentAlignment = Alignment.Center,
//            modifier = Modifier
//                .offset {
//                    IntOffset(
//                        (centerX - 20).roundToInt(), // 20 adalah setengah dari lebar teks perkiraan
//                        (10) // Posisi di atas area trim
//                    )
//                }
//                .clip(RoundedCornerShape(4.dp))
//                .background(Color.White)
//                .padding(horizontal = 8.dp, vertical = 2.dp)
//        ) {
//            Text(
//                text = "${currentDuration / 1000}s",
//                color = Color.Black,
//                fontSize = 12.sp,
//                fontWeight = FontWeight.Bold,
//                textAlign = TextAlign.Center
//            )
//        }
//    }
//}
//

//V1
//@Composable
//fun TrimControls(
//    videoDurationMs: Long,
//    maxTrimMs: Long = 10_000,
//    onTrimChanged: (Long, Long) -> Unit
//) {
//    var totalWidthPx by remember { mutableStateOf(0f) }
//
//    var startPx by remember { mutableFloatStateOf(0f) }
//    var endPx by remember { mutableFloatStateOf(0f) }
//
//    val scrollState = rememberScrollState()
//
//    // 1 detik = 80dp (mirip CapCut)
//    val pxPerSecondDp = 30.dp
//
//    // Hitung total width timeline
//    val timelineWidthDp = with(LocalDensity.current) {
//        (videoDurationMs / 1000f * pxPerSecondDp.value).dp
//    }
//
//    // ==========================
//    // Pixel → Millisecond
//    // ==========================
//    fun pxToMs(px: Float): Long {
//        if (totalWidthPx <= 0f) return 0L
//        val ratio = (px / totalWidthPx).coerceIn(0f, 1f)
//        return (ratio * videoDurationMs).toLong()
//    }
//
//    // Millisecond → Pixel
//    fun msToPx(ms: Long): Float {
//        if (videoDurationMs <= 0L) return 0f
//        val ratio = ms / videoDurationMs.toFloat()
//        return (ratio * totalWidthPx).coerceIn(0f, totalWidthPx)
//    }
//
//
//    val currentStart = pxToMs(startPx)
//    val currentEnd = pxToMs(endPx)
//    val currentDuration = (currentEnd - currentStart).coerceAtLeast(0)
//
//    // Callback trim
//    LaunchedEffect(currentStart, currentEnd) {
//        if (currentDuration in 1000..maxTrimMs) {
//            onTrimChanged(currentStart, currentEnd)
//        }
//    }
//
//    // ==============================
//    // MAIN UI
//    // ==============================
//    Box(
//            modifier = Modifier
//                .horizontalScroll(scrollState)   // TIMELINE SCROLLABLE
//                .width(timelineWidthDp)
//                .height(40.dp)
//                .onGloballyPositioned { layout ->
//                    totalWidthPx = layout.size.width.toFloat()
//
//                    if (endPx == 0f && totalWidthPx > 0f) {
//                        val endMs = min(maxTrimMs, videoDurationMs)
//                        endPx = msToPx(endMs)
//                    }
//                }
//        ) {
//
//            // ==============================
//            // TIMELINE MARKERS + THUMBNAIL
//            // ==============================
//            ThumbnailStrip(
//                videoDurationMs = videoDurationMs,
//            )
//
//            // ==============================
//            // SELECTED AREA
//            // ==============================
//            val selectionWidthPx = (endPx - startPx).coerceAtLeast(0f)
//            val selectionWidthDp = with(LocalDensity.current) { selectionWidthPx.toDp() }
//
//            // AREA yang dipilih
//            Box(
//                modifier = Modifier
//                    .offset { IntOffset(startPx.roundToInt(), 0) }
//                    .width(selectionWidthDp)
//                    .fillMaxHeight()
//                    .background(Color.White.copy(0.15f))
//                    .draggable(
//                        orientation = Orientation.Horizontal,
//                        state = rememberDraggableState { delta ->
//                            val newStart = (startPx + delta)
//                                .coerceIn(0f, totalWidthPx - selectionWidthPx)
//
//                            val newEnd = newStart + selectionWidthPx
//
//                            if (newEnd <= totalWidthPx) {
//                                startPx = newStart
//                                endPx = newEnd
//                            }
//                        }
//                    )
//            )
//
//            // ==============================
//            // LEFT HANDLE
//            // ==============================
//            TrimHandle(
//                modifier = Modifier
//                    .offset { IntOffset((startPx - 18).roundToInt(), 0) }
//                    .draggable(
//                        orientation = Orientation.Horizontal,
//                        state = rememberDraggableState { delta ->
//                            val newStart = (startPx + delta)
//                                .coerceIn(0f, endPx - msToPx(1000))
//
//                            val newDuration = pxToMs(endPx) - pxToMs(newStart)
//                            if (newDuration <= maxTrimMs) startPx = newStart
//                        }
//                    )
//            )
//
//            // ==============================
//            // RIGHT HANDLE
//            // ==============================
//            TrimHandle(
//                modifier = Modifier
//                    .offset { IntOffset((endPx - 2).roundToInt(), 0) }
//                    .draggable(
//                        orientation = Orientation.Horizontal,
//                        state = rememberDraggableState { delta ->
//                            val newEnd = (endPx + delta)
//                                .coerceIn(startPx + msToPx(1000), totalWidthPx)
//
//                            val newDuration = pxToMs(newEnd) - pxToMs(startPx)
//                            if (newDuration <= maxTrimMs) endPx = newEnd
//                        }
//                    )
//            )
//        }
//
//        // ==============================
//        // LABEL DURASI TERPILIH
//        // ==============================
//        val centerX = startPx + (endPx - startPx) / 2 - scrollState.value
//
//        Box(
//            modifier = Modifier
//                .offset { IntOffset(centerX.roundToInt(), 50) }
//                .clip(RoundedCornerShape(4.dp))
//                .background(Color.White)
//                .padding(horizontal = 10.dp, vertical = 4.dp)
//        ) {
//            Text(
//                text = "${currentDuration / 1000}s",
//                fontWeight = FontWeight.Bold,
//                color = Color.Black,
//                fontSize = 14.sp
//            )
//        }
//}
//