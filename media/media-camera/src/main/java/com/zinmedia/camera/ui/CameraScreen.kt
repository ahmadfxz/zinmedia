package com.zinmedia.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zinmedia.camera.CameraState
import com.zinmedia.camera.CaptureMode
import com.zinmedia.camera.R
import com.zinmedia.camera.Speeds
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private val RecordRed = Color(0xFFFE2C55)
private val Highlight = Color(0xFFFFD54F)
private val LabelStyle = TextStyle(
    color = Color.White,
    fontSize = 11.sp,
    fontWeight = FontWeight.Medium,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f),
)

@Composable
internal fun CameraScreen(
    state: CameraState,
    onClose: () -> Unit,
    onGallery: () -> Unit,
) {
    var showDiscard by remember { mutableStateOf(false) }
    val session = state.session

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ---- preview 9:16 ----
        Box(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            PreviewWithGestures(state)

            if (state.grid) GridOverlay()

            // Gradasi tipis agar ikon & teks putih tetap terbaca di latar terang.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent)))
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .width(88.dp)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))))
            )

            // Atas: tutup + progress bar segmen.
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (state.mode != CaptureMode.Photo) {
                    SegmentProgress(state)
                    Spacer(Modifier.height(8.dp))
                }
                if (!state.isRecording) {
                    ToolButton(
                        icon = R.drawable.zm_ic_camera_close,
                        label = null,
                        description = stringResource(R.string.zm_camera_close),
                        onClick = { if (state.segments.isNotEmpty()) showDiscard = true else onClose() },
                    )
                }
            }

            // Kanan: alat kamera.
            AnimatedVisibility(
                visible = !state.isRecording && state.countdown == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 8.dp),
            ) {
                CameraToolbar(state)
            }

            state.countdown?.let { second ->
                Text(
                    text = second.toString(),
                    style = LabelStyle.copy(fontSize = 96.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (session.zoomRatio > 1.01f) {
                Text(
                    text = "%.1fx".format(session.zoomRatio),
                    style = LabelStyle.copy(fontSize = 13.sp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 180.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        // ---- kontrol bawah ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(visible = state.showFilters && !state.isRecording) {
                FilterStrip(state)
            }
            AnimatedVisibility(visible = state.showSpeed && !state.isRecording && state.mode != CaptureMode.Photo) {
                SpeedSelector(state)
            }
            Spacer(Modifier.height(12.dp))
            CaptureRow(state, onGallery)
            Spacer(Modifier.height(12.dp))
            ModeTabs(state)
        }

        if (state.screenFlash) {
            Box(Modifier.fillMaxSize().background(Color.White))
        }

        state.processing?.let { progress -> ProcessingOverlay(progress) }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.zm_camera_discard_title)) },
            text = { Text(stringResource(R.string.zm_camera_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    state.reset()
                    onClose()
                }) { Text(stringResource(R.string.zm_camera_discard), color = RecordRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.zm_camera_cancel)) }
            },
            containerColor = Color(0xFF1F1F1F),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
        )
    }
}

/**
 * Preview kamera: ketuk = fokus, ketuk 2x = balik kamera, cubit = zoom,
 * geser kiri/kanan = ganti filter.
 */
@Composable
private fun PreviewWithGestures(state: CameraState) {
    val session = state.session
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var filterLabelKey by remember { mutableIntStateOf(0) }
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val currentState by rememberUpdatedState(state)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        currentState.session.focusAt(offset.x, offset.y)
                        focusPoint = offset
                    },
                    onDoubleTap = { currentState.flipCamera() },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dragX = 0f
                    var multiTouch = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            // Cubit: rasio jarak dua jari sekarang vs sebelumnya.
                            multiTouch = true
                            val (a, b) = pressed
                            val now = (a.position - b.position).getDistance()
                            val before = (a.previousPosition - b.previousPosition).getDistance()
                            if (before > 0f) currentState.session.setZoom(currentState.session.zoomRatio * now / before)
                        } else if (pressed.size == 1 && !multiTouch) {
                            dragX += pressed[0].positionChange().x
                        }
                    } while (event.changes.any { it.pressed })
                    if (!multiTouch && abs(dragX) > swipeThreshold && !currentState.isBusy) {
                        currentState.selectFilter(currentState.filterIndex + if (dragX < 0) 1 else -1)
                        filterLabelKey++
                    }
                }
            }
    ) {
        AndroidView(factory = { session.previewView }, modifier = Modifier.fillMaxSize())

        focusPoint?.let { point -> FocusRing(point, onDone = { focusPoint = null }) }

        // Nama filter muncul sebentar saat diganti.
        var showFilterName by remember { mutableStateOf(false) }
        LaunchedEffect(filterLabelKey, state.filterIndex) {
            if (filterLabelKey == 0 && !state.showFilters) return@LaunchedEffect
            showFilterName = true
            delay(900)
            showFilterName = false
        }
        AnimatedVisibility(
            visible = showFilterName,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(state.filter.name, style = LabelStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun FocusRing(point: Offset, onDone: () -> Unit) {
    var shrink by remember(point) { mutableStateOf(false) }
    val size by animateDpAsState(if (shrink) 56.dp else 76.dp, label = "focus")
    LaunchedEffect(point) {
        shrink = true
        delay(800)
        onDone()
    }
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    Box(
        Modifier
            .offset { IntOffset((point.x - half).roundToInt(), (point.y - half).roundToInt()) }
            .size(size)
            .border(1.5.dp, Highlight, CircleShape)
    )
}

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val color = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
    }
}

@Composable
private fun SegmentProgress(state: CameraState) {
    val max = state.mode.maxMs.toFloat()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.3f))
    ) {
        val filled = (state.totalOutputMs / max).coerceIn(0f, 1f) * size.width
        drawRect(RecordRed, size = size.copy(width = filled))
        // Penanda batas antar segmen.
        var position = 0L
        for (segment in state.segments) {
            position += segment.outputMs
            val x = position / max * size.width
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        }
    }
}

@Composable
private fun CameraToolbar(state: CameraState) {
    val session = state.session
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (session.hasFrontAndBack) {
            ToolButton(R.drawable.zm_ic_camera_flip, stringResource(R.string.zm_camera_flip), onClick = state::flipCamera)
        }
        if (state.mode != CaptureMode.Photo) {
            ToolButton(
                icon = R.drawable.zm_ic_camera_speed,
                label = stringResource(R.string.zm_camera_speed),
                active = state.showSpeed || state.speed != 1f,
                onClick = {
                    state.showSpeed = !state.showSpeed
                    if (state.showSpeed) state.showFilters = false
                },
            )
        }
        ToolButton(
            icon = R.drawable.zm_ic_camera_filter,
            label = stringResource(R.string.zm_camera_filter),
            active = state.showFilters || state.filterIndex != 0,
            onClick = {
                state.showFilters = !state.showFilters
                if (state.showFilters) state.showSpeed = false
            },
        )
        ToolButton(
            icon = R.drawable.zm_ic_camera_beauty,
            label = stringResource(R.string.zm_camera_beauty),
            active = state.smoothing,
            onClick = state::toggleSmoothing,
        )
        ToolButton(
            icon = R.drawable.zm_ic_camera_timer,
            label = if (state.timerSeconds == 0) {
                stringResource(R.string.zm_camera_timer)
            } else {
                stringResource(R.string.zm_camera_timer_seconds, state.timerSeconds)
            },
            active = state.timerSeconds != 0,
            onClick = state::cycleTimer,
        )
        if (session.hasFlashUnit || session.isFront) {
            ToolButton(
                icon = if (state.flash) R.drawable.zm_ic_camera_flash_on else R.drawable.zm_ic_camera_flash_off,
                label = stringResource(R.string.zm_camera_flash),
                active = state.flash,
                onClick = state::toggleFlash,
            )
        }
        ToolButton(
            icon = R.drawable.zm_ic_camera_grid,
            label = stringResource(R.string.zm_camera_grid),
            active = state.grid,
            onClick = state::toggleGrid,
        )
    }
}

@Composable
private fun ToolButton(
    icon: Int,
    label: String?,
    active: Boolean = false,
    description: String? = label,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (active) Highlight else Color.White, label = "tool")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (description != null) contentDescription = description }
            .padding(4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(28.dp)
                .shadow(0.dp),
        )
        if (label != null) {
            Spacer(Modifier.height(2.dp))
            Text(label, style = LabelStyle.copy(color = tint))
        }
    }
}

@Composable
private fun FilterStrip(state: CameraState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.filterIndex) { listState.animateScrollToItem((state.filterIndex - 2).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        itemsIndexed(state.filters) { index, filter ->
            val selected = index == state.filterIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable(role = Role.Button) { state.selectFilter(index) },
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(filter.swatch))
                        .border(
                            width = if (selected) 2.5.dp else 0.dp,
                            color = if (selected) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    filter.name,
                    style = LabelStyle.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SpeedSelector(state: CameraState) {
    Row(
        Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(3.dp),
    ) {
        Speeds.forEach { speed ->
            val selected = state.speed == speed
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable(role = Role.Button) { state.speed = speed },
            ) {
                Text(
                    text = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x",
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CaptureRow(state: CameraState, onGallery: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(96.dp)) {
        // Kiri: galeri (sebelum merekam) atau hapus klip terakhir.
        Box(Modifier.align(Alignment.CenterStart).padding(start = 40.dp)) {
            when {
                state.isRecording -> Unit
                state.segments.isNotEmpty() -> RoundIconButton(
                    icon = R.drawable.zm_ic_camera_backspace,
                    description = stringResource(R.string.zm_camera_delete_segment),
                    onClick = state::deleteLastSegment,
                )
                else -> RoundIconButton(
                    icon = R.drawable.zm_ic_camera_gallery,
                    description = stringResource(R.string.zm_camera_gallery),
                    onClick = onGallery,
                )
            }
        }

        ShutterButton(state, Modifier.align(Alignment.Center))

        // Kanan: selesai (bila sudah ada klip).
        val doneDescription = stringResource(R.string.zm_camera_done)
        if (state.segments.isNotEmpty() && !state.isRecording) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 40.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(RecordRed)
                    .clickable(role = Role.Button, onClick = state::finish)
                    .semantics { contentDescription = doneDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.zm_ic_camera_check), null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

/**
 * Tombol rana. Foto: ketuk. Video: ketuk untuk mulai/berhenti, atau tahan selama merekam
 * (geser jari ke atas saat menahan untuk zoom).
 */
@Composable
private fun ShutterButton(state: CameraState, modifier: Modifier = Modifier) {
    val currentState by rememberUpdatedState(state)
    val recording = state.isRecording
    val video = state.mode != CaptureMode.Photo
    val outer by animateDpAsState(if (recording) 96.dp else 80.dp, label = "outer")
    val inner by animateDpAsState(if (recording) 30.dp else 64.dp, label = "inner")
    val innerCorner by animateDpAsState(if (recording) 8.dp else 32.dp, label = "corner")
    val ringAlpha by animateFloatAsState(if (recording) 0.35f else 1f, label = "ring")
    val description = stringResource(R.string.zm_camera_shutter)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(outer)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pressedAt = System.currentTimeMillis()
                    val s = currentState
                    val holdMode = s.mode != CaptureMode.Photo && !s.isRecording && s.timerSeconds == 0 && s.countdown == null
                    if (holdMode) s.onShutterHoldStart()
                    val startZoom = s.session.zoomRatio
                    var up = false
                    while (!up) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (holdMode) {
                            // Geser ke atas = zoom masuk.
                            val dy = change.position.y - down.position.y
                            if (dy < 0f) s.session.setZoom(startZoom * exp(-dy / 400f))
                        }
                        up = !change.pressed
                    }
                    val heldMs = System.currentTimeMillis() - pressedAt
                    when {
                        holdMode && heldMs >= HoldThresholdMs -> s.onShutterHoldEnd()
                        holdMode -> Unit // ketukan singkat: tetap merekam sampai diketuk lagi
                        else -> s.onShutterTap()
                    }
                }
            },
    ) {
        Box(
            Modifier
                .size(outer)
                .graphicsLayer { alpha = ringAlpha }
                .border(5.dp, if (video) RecordRed.copy(alpha = 0.5f) else Color.White, CircleShape)
        )
        Box(
            Modifier
                .size(inner)
                .clip(RoundedCornerShape(innerCorner))
                .background(if (video) RecordRed else Color.White)
        )
    }
}

private const val HoldThresholdMs = 400L

@Composable
private fun ModeTabs(state: CameraState) {
    if (state.isBusy || state.segments.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf(
            CaptureMode.Video60 to stringResource(R.string.zm_camera_mode_60),
            CaptureMode.Video15 to stringResource(R.string.zm_camera_mode_15),
            CaptureMode.Photo to stringResource(R.string.zm_camera_mode_photo),
        ).forEach { (mode, label) ->
            val selected = state.mode == mode
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Tab) { state.selectMode(mode) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun ProcessingOverlay(progress: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = RecordRed)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.zm_camera_processing) +
                    if (progress > 0f) " ${(progress * 100).roundToInt()}%" else "",
                color = Color.White,
                fontSize = 14.sp,
            )
        }
    }
}
