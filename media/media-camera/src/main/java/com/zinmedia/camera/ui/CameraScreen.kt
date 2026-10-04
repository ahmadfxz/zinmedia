package com.zinmedia.camera.ui

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.luminance
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
import com.zinmedia.videoeditor.VideoEditorConfig
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** Merah rekam (tombol rana & progress): menandakan sedang merekam, tidak ikut warna utama. */
private val RecordRed = Color(0xFFFE2C55)

/** Warna utama aplikasi ([VideoEditorConfig.accentColor]) dan warna ikon di atasnya. */
private val Accent: Color get() = Color(VideoEditorConfig.accentColor)
private val OnAccent: Color get() = if (Accent.luminance() > 0.6f) Color.Black else Color.White
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
    // Deretan foto (untuk menghapus) dibuka dengan mengetuk tumpukan foto.
    var photoTrayOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.photos.isEmpty()) { if (state.photos.isEmpty()) photoTrayOpen = false }
    // Deretan tetap digambar selama animasi tutup berjalan.
    var photoTrayShown by remember { mutableStateOf(false) }
    LaunchedEffect(photoTrayOpen) {
        if (photoTrayOpen) {
            photoTrayShown = true
        } else {
            delay(trayCloseDurationMs(state.photos.size))
            photoTrayShown = false
        }
    }
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

            ShutterBlink(state.photoTakenCount)

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
                        onClick = { if (state.hasCaptures) showDiscard = true else onClose() },
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

            state.faceEffectLoading?.let { progress ->
                Text(
                    text = stringResource(R.string.zm_camera_face_loading, (progress * 100).roundToInt()),
                    style = LabelStyle.copy(fontSize = 13.sp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
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
            AnimatedVisibility(visible = state.showFaceEffects && !state.isRecording) {
                FaceEffectStrip(state)
            }
            AnimatedVisibility(visible = state.showSpeed && !state.isRecording && state.mode != CaptureMode.Photo) {
                SpeedSelector(state)
            }
            // Tanpa animasi wadah: tiap foto masuk/keluar sendiri-sendiri (animasi list).
            if (photoTrayShown && state.photos.isNotEmpty()) PhotoTray(state, open = photoTrayOpen)
            Spacer(Modifier.height(12.dp))
            CaptureRow(state, onGallery, onPhotoStackClick = { photoTrayOpen = !photoTrayOpen })
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
                }) { Text(stringResource(R.string.zm_camera_discard), color = Accent) }
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
                    if (state.showSpeed) {
                        state.showFilters = false
                        state.showFaceEffects = false
                    }
                },
            )
        }
        ToolButton(
            icon = R.drawable.zm_ic_camera_filter,
            label = stringResource(R.string.zm_camera_filter),
            active = state.showFilters || state.filterIndex != 0,
            onClick = {
                state.showFilters = !state.showFilters
                if (state.showFilters) {
                    state.showSpeed = false
                    state.showFaceEffects = false
                }
            },
        )
        if (state.faceEffects.isNotEmpty()) {
            ToolButton(
                icon = R.drawable.zm_ic_camera_face,
                label = stringResource(R.string.zm_camera_face_effects),
                active = state.showFaceEffects || state.faceEffectIndex >= 0,
                onClick = {
                    state.showFaceEffects = !state.showFaceEffects
                    if (state.showFaceEffects) {
                        state.showFilters = false
                        state.showSpeed = false
                    }
                },
            )
        }
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
private fun CaptureRow(state: CameraState, onGallery: () -> Unit, onPhotoStackClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(96.dp)) {
        // Kiri: galeri (sebelum mengambil apa pun), tumpukan foto, atau hapus klip terakhir.
        Box(Modifier.align(Alignment.CenterStart).padding(start = 40.dp)) {
            when {
                state.isRecording -> Unit
                state.photos.isNotEmpty() -> PhotoStack(state.photos, onClick = onPhotoStackClick)
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

        // Kanan: selesai (bila sudah ada klip atau foto).
        val doneDescription = stringResource(R.string.zm_camera_done)
        if (state.hasCaptures && !state.isRecording) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 40.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .clickable(role = Role.Button, onClick = state::finish)
                    .semantics { contentDescription = doneDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.zm_ic_camera_check), null, tint = OnAccent, modifier = Modifier.size(26.dp))
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
    // Tab disembunyikan saat merekam, setelah ada klip, atau bila hanya ada satu mode.
    if (state.isBusy || state.hasCaptures || state.modes.size < 2) {
        Spacer(Modifier.height(24.dp))
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // Urutan tab mengikuti state.modes: 15d, 1m, 30d, Foto.
        val labels = mapOf(
            CaptureMode.Video15 to stringResource(R.string.zm_camera_mode_15),
            CaptureMode.Video60 to stringResource(R.string.zm_camera_mode_60),
            CaptureMode.Video30 to stringResource(R.string.zm_camera_mode_30),
            CaptureMode.Photo to stringResource(R.string.zm_camera_mode_photo),
        )
        state.modes.map { it to labels.getValue(it) }.forEach { (mode, label) ->
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
            CircularProgressIndicator(color = Accent)
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

/** Kedip gelap singkat setiap kali foto diambil. */
@Composable
private fun ShutterBlink(photoTakenCount: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(photoTakenCount) {
        if (photoTakenCount == 0) return@LaunchedEffect
        visible = true
        delay(120)
        visible = false
    }
    val alpha by animateFloatAsState(if (visible) 0.6f else 0f, label = "blink")
    if (alpha > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
}

/** Foto yang sudah diambil (mode Foto, lebih dari satu): pratinjau kecil, hapus (×), dan jumlah. */
@Composable
private fun PhotoTray(state: CameraState, open: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(state.photos, key = { _, file -> file.path }) { index, file ->
                // Buka: masuk berurutan dari kiri (arah tumpukan foto). Tutup: keluar berurutan dari
                // kanan kembali ke tumpukan. Hapus/geser memakai animasi item.
                val appear = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(open) {
                    if (open) {
                        delay(index * TrayStaggerInMs)
                        appear.animateTo(1f, androidx.compose.animation.core.tween(TrayItemInMs))
                    } else {
                        delay((state.photos.size - 1 - index) * TrayStaggerOutMs)
                        appear.animateTo(0f, androidx.compose.animation.core.tween(TrayItemOutMs))
                    }
                }
                val slide = with(LocalDensity.current) { 24.dp.toPx() }
                Box(
                    Modifier
                        .animateItem()
                        .graphicsLayer {
                            alpha = appear.value
                            translationX = (1f - appear.value) * -slide
                        }
                        .size(56.dp)
                ) {
                    PhotoThumbnail(file, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
                    val removeLabel = stringResource(R.string.zm_camera_remove_photo)
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(role = Role.Button) { state.removePhoto(index) }
                            .semantics { contentDescription = removeLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.zm_ic_camera_close), null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        // Jumlah foto ikut muncul/hilang bersama deretan.
        val counter = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(open) {
            if (open) {
                counter.animateTo(1f, androidx.compose.animation.core.tween(TrayItemInMs))
            } else {
                counter.animateTo(0f, androidx.compose.animation.core.tween(TrayItemOutMs))
            }
        }
        Text(
            text = stringResource(R.string.zm_camera_photo_count, state.photos.size, state.photoLimit),
            style = LabelStyle.copy(fontSize = 13.sp),
            modifier = Modifier
                .padding(start = 8.dp)
                .graphicsLayer { alpha = counter.value },
        )
    }
}

/** Pratinjau kecil dari file JPEG hasil kamera (didekode kecil di luar thread UI). */
@Composable
private fun PhotoThumbnail(file: java.io.File, modifier: Modifier = Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
            android.graphics.BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
        }
    }
    Box(modifier.background(Color.DarkGray)) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Foto yang sudah diambil, ditumpuk seperti kartu yang sedikit miring (acak tapi tetap per foto),
 * dengan jumlah foto. Ketuk untuk membuka/menutup deretan foto.
 */
@Composable
private fun PhotoStack(photos: List<java.io.File>, onClick: () -> Unit) {
    val description = stringResource(R.string.zm_camera_photo_count, photos.size, photos.size)
    // Kartu teratas "masuk" dengan sedikit memantul setiap ada foto baru.
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(photos.size) {
        pop.snapTo(1.25f)
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f))
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        val firstVisible = (photos.size - 3).coerceAtLeast(0)
        val visible = photos.subList(firstVisible, photos.size)
        visible.forEachIndexed { index, file ->
            val isTop = index == visible.lastIndex
            PhotoThumbnail(
                file = file,
                modifier = Modifier
                    .size(width = 40.dp, height = 52.dp)
                    .graphicsLayer {
                        rotationZ = cardTilt(file, firstVisible + index)
                        if (isTop) {
                            scaleX = pop.value
                            scaleY = pop.value
                        }
                    }
                    .shadow(3.dp, RoundedCornerShape(6.dp))
                    .border(1.5.dp, Color.White, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        // Jumlah foto.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Accent),
        ) {
            Text(photos.size.toString(), color = OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Kemiringan kartu: bergantian kiri/kanan sesuai urutan foto, besarnya acak 4°–12° dari isi nama
 * file, sehingga tetap sama untuk foto yang sama.
 */
private fun cardTilt(file: java.io.File, position: Int): Float {
    val hash = file.name.hashCode()
    val mixed = (hash xor (hash ushr 16)) * 0x45d9f3b
    val magnitude = 4 + (mixed and 0x7fffffff) % 9
    return (if (position % 2 == 0) -magnitude else magnitude).toFloat()
}

private const val TrayStaggerInMs = 45L
private const val TrayItemInMs = 220
private const val TrayStaggerOutMs = 35L
private const val TrayItemOutMs = 160

/** Lama animasi tutup deretan untuk [count] foto (sampai foto terakhir selesai keluar). */
private fun trayCloseDurationMs(count: Int): Long = (count - 1).coerceAtLeast(0) * TrayStaggerOutMs + TrayItemOutMs + 20

/** Pilihan efek wajah: "Tanpa" lalu efek dari aplikasi (gambar efeknya sebagai pratinjau). */
@Composable
private fun FaceEffectStrip(state: CameraState) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        item {
            FaceEffectItem(
                label = stringResource(R.string.zm_camera_face_none),
                imageUrl = null,
                selected = state.faceEffectIndex < 0,
                onClick = { state.selectFaceEffect(-1) },
            )
        }
        itemsIndexed(state.faceEffects) { index, effect ->
            FaceEffectItem(
                label = effect.name,
                imageUrl = effect.imageUrl,
                selected = index == state.faceEffectIndex,
                onClick = { state.selectFaceEffect(index) },
            )
        }
    }
}

@Composable
private fun FaceEffectItem(label: String, imageUrl: String?, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .border(
                    width = if (selected) 2.5.dp else 0.dp,
                    color = if (selected) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            if (imageUrl != null) {
                coil3.compose.AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                Icon(painterResource(R.drawable.zm_ic_camera_close), null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = LabelStyle.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium), maxLines = 1)
    }
}
