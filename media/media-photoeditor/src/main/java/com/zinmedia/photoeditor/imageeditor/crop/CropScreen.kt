package com.zinmedia.photoeditor.imageeditor.crop

import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.zinmedia.photoeditor.R
import com.zinmedia.photoeditor.ui.EditorColors
import com.zinmedia.photoeditor.ui.EditorDoneButton
import com.zinmedia.photoeditor.ui.EditorIconButton
import com.zinmedia.photoeditor.ui.EditorTopBar
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val MaxZoom = 8f

private enum class Handle(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    TopLeft(true, true, false, false),
    TopRight(false, true, true, false),
    BottomLeft(true, false, false, true),
    BottomRight(false, false, true, true),
    Left(true, false, false, false),
    Top(false, true, false, false),
    Right(false, false, true, false),
    Bottom(false, false, false, true);

    val isCorner: Boolean get() = (left || right) && (top || bottom)
}

/**
 * Mode crop.
 *
 * - Tarik sudut/sisi bingkai untuk crop bebas; setelah dilepas, area crop otomatis di-zoom memenuhi layar.
 * - Cubit untuk zoom, geser untuk memindah gambar di bawah bingkai.
 * - Putar 90°, cermin, dan pilihan rasio (Bebas, Asli, 1:1, 4:5, 3:4, 9:16, 4:3, 16:9).
 * - Selalu bekerja dari foto asli, sehingga crop bisa diubah lagi kapan saja.
 */
@Composable
internal fun CropScreen(
    original: Bitmap,
    initial: CropState,
    onCancel: () -> Unit,
    onDone: (CropState, Bitmap) -> Unit,
) {
    BackHandler(onBack = onCancel)

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var quarterTurns by remember { mutableIntStateOf(initial.quarterTurns) }
    var flipped by remember { mutableStateOf(initial.flipped) }
    var aspect by remember { mutableStateOf(initial.aspect) }
    // Area crop (ternormalisasi) yang harus diterapkan begitu ukuran layar/gambar diketahui.
    var pendingRect by remember { mutableStateOf<Rect?>(initial.rect) }
    // Area crop terakhir yang konsisten dengan bingkai; dipakai ulang saat ukuran layar berubah.
    var committedRect by remember { mutableStateOf(initial.rect) }

    val display = remember(original, quarterTurns, flipped) { original.transformed(quarterTurns, flipped) }
    val image = remember(display) { display.asImageBitmap() }
    val imageW = display.width.toFloat()
    val imageH = display.height.toFloat()

    var areaSize by remember { mutableStateOf(IntSize.Zero) }
    val inset = with(density) { 28.dp.toPx() }
    // Dibaca langsung dari state (bukan nilai saat komposisi), agar bisa dihitung ulang
    // di onSizeChanged sebelum frame pertama digambar.
    fun avail(): Rect = Rect(inset, inset, areaSize.width - inset, areaSize.height - inset)
    fun fitScale(): Float {
        val a = avail()
        return if (a.width > 0 && a.height > 0) min(a.width / imageW, a.height / imageH) else 1f
    }

    var userScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(Rect.Zero) }
    val gridAlpha = remember { Animatable(0f) }

    fun imageRect(scale: Float = userScale, off: Offset = offset): Rect {
        val ds = fitScale() * scale
        val c = avail().center + off
        return Rect(c.x - imageW * ds / 2f, c.y - imageH * ds / 2f, c.x + imageW * ds / 2f, c.y + imageH * ds / 2f)
    }

    fun currentRect(): Rect {
        val ir = imageRect()
        if (ir.width <= 0f || ir.height <= 0f) return FullRect
        return Rect(
            ((frame.left - ir.left) / ir.width).coerceIn(0f, 1f),
            ((frame.top - ir.top) / ir.height).coerceIn(0f, 1f),
            ((frame.right - ir.left) / ir.width).coerceIn(0f, 1f),
            ((frame.bottom - ir.top) / ir.height).coerceIn(0f, 1f),
        )
    }

    /** Hitung zoom/posisi agar area [rect] memenuhi layar dengan bingkai di tengah. */
    fun targetFor(rect: Rect): Triple<Float, Offset, Rect> {
        val cropW = rect.width * imageW
        val cropH = rect.height * imageH
        val ds = min(avail().width / cropW, avail().height / cropH).coerceAtMost(fitScale() * MaxZoom)
        val scale = (ds / fitScale()).coerceAtLeast(1f)
        val dsFinal = fitScale() * scale
        val fw = cropW * dsFinal
        val fh = cropH * dsFinal
        val newFrame = Rect(avail().center.x - fw / 2f, avail().center.y - fh / 2f, avail().center.x + fw / 2f, avail().center.y + fh / 2f)
        val newOffset = Offset((0.5f - rect.center.x) * imageW * dsFinal, (0.5f - rect.center.y) * imageH * dsFinal)
        return Triple(scale, newOffset, newFrame)
    }

    fun snapTo(rect: Rect) {
        val (s, o, f) = targetFor(rect)
        userScale = s
        offset = o
        frame = f
        committedRect = rect
    }

    fun animateTo(rect: Rect) {
        val (s, o, f) = targetFor(rect)
        val s0 = userScale
        val o0 = offset
        val f0 = frame
        committedRect = rect
        scope.launch {
            animate(0f, 1f, animationSpec = tween(260)) { t, _ ->
                userScale = lerp(s0, s, t)
                offset = androidx.compose.ui.geometry.lerp(o0, o, t)
                frame = lerp(f0, f, t)
            }
        }
    }

    /** Pastikan gambar selalu menutupi bingkai (tidak ada area kosong di dalam crop). */
    fun clampImageToFrame() {
        val minScale = maxOf(frame.width / (imageW * fitScale()), frame.height / (imageH * fitScale()))
        userScale = userScale.coerceIn(minScale, MaxZoom)
        val ir = imageRect()
        var dx = 0f
        var dy = 0f
        if (ir.left > frame.left) dx = frame.left - ir.left
        if (ir.right < frame.right) dx = frame.right - ir.right
        if (ir.top > frame.top) dy = frame.top - ir.top
        if (ir.bottom < frame.bottom) dy = frame.bottom - ir.bottom
        offset += Offset(dx, dy)
    }

    fun applyAspect(newAspect: CropAspect, rect: Rect = currentRect()) {
        aspect = newAspect
        val ratio = newAspect.ratioFor(imageW, imageH)
        val target = if (ratio == null) {
            rect
        } else {
            // Rasio dalam koordinat ternormalisasi: (w/h piksel) dibagi (lebar/tinggi gambar).
            largestRectWithRatio(FullRect, ratio * imageH / imageW, rect.center)
        }
        animateTo(target)
    }

    // Setelah putar/cermin/atur ulang (ukuran area tidak berubah).
    LaunchedEffect(display, pendingRect) {
        val target = pendingRect
        if (target != null && areaSize.width > 0 && areaSize.height > 0) {
            snapTo(target)
            pendingRect = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorColors.Background)
    ) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(onClose = onCancel) {
                if (quarterTurns % 4 != 0 || flipped || aspect != CropAspect.Free || (frame.width > 0f && currentRect() != FullRect)) {
                    Text(
                        text = stringResource(R.string.zm_reset),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                quarterTurns = 0
                                flipped = false
                                aspect = CropAspect.Free
                                pendingRect = FullRect
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                EditorDoneButton(onClick = {
                    val rect = currentRect()
                    onDone(CropState(quarterTurns, flipped, rect, aspect), display.cropNormalized(rect))
                })
            }

            // Kanvas crop
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { size ->
                        areaSize = size
                        if (size.width > 0 && size.height > 0) {
                            snapTo(pendingRect ?: committedRect)
                            pendingRect = null
                        }
                    }
                    .pointerInput(display, aspect, areaSize) {
                        val touchSlop = with(density) { 28.dp.toPx() }
                        val minFrame = with(density) { 72.dp.toPx() }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val handle = hitHandle(frame, down.position, touchSlop)
                            scope.launch { gridAlpha.animateTo(1f, tween(120)) }
                            do {
                                val event = awaitPointerEvent()
                                if (handle != null && event.changes.size == 1) {
                                    val delta = event.changes.first().positionChange()
                                    frame = dragFrame(
                                        frame = frame,
                                        handle = handle,
                                        delta = delta,
                                        bounds = imageRect().intersect(avail()),
                                        minSize = minFrame,
                                        ratio = aspect.ratioFor(imageW, imageH),
                                    )
                                } else {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val centroid = event.calculateCentroid()
                                    if (zoom != 1f || pan != Offset.Zero) {
                                        val oldScale = userScale
                                        val newScale = (oldScale * zoom).coerceAtMost(MaxZoom)
                                        val center = avail().center + offset
                                        val pivot = if (centroid.isSpecified) centroid else center
                                        val newCenter = pivot + (center - pivot) * (newScale / oldScale) + pan
                                        userScale = newScale
                                        offset = newCenter - avail().center
                                        clampImageToFrame()
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })

                            scope.launch { gridAlpha.animateTo(0f, tween(300)) }
                            if (handle != null) animateTo(currentRect()) else committedRect = currentRect()
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // Jangan gambar sebelum posisi gambar & bingkai dihitung (mencegah kedipan
                    // gambar di pojok kiri atas saat dibuka, diputar, atau dicerminkan).
                    if (pendingRect != null || frame.width <= 0f) return@Canvas
                    val ir = imageRect()
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(ir.left.roundToInt(), ir.top.roundToInt()),
                        dstSize = IntSize(ir.width.roundToInt(), ir.height.roundToInt()),
                        filterQuality = FilterQuality.High,
                    )

                    // Redupkan area di luar bingkai
                    val dim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addRect(frame)
                    }
                    drawPath(dim, Color.Black.copy(alpha = 0.65f))

                    // Grid sepertiga saat berinteraksi
                    if (gridAlpha.value > 0f) {
                        val gridColor = Color.White.copy(alpha = 0.6f * gridAlpha.value)
                        for (i in 1..2) {
                            val x = frame.left + frame.width * i / 3f
                            val y = frame.top + frame.height * i / 3f
                            drawLine(gridColor, Offset(x, frame.top), Offset(x, frame.bottom), 1.dp.toPx())
                            drawLine(gridColor, Offset(frame.left, y), Offset(frame.right, y), 1.dp.toPx())
                        }
                    }

                    // Bingkai + sudut tebal
                    drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(1.5.dp.toPx()))
                    val len = 22.dp.toPx()
                    val w = 3.5.dp.toPx()
                    val o = w / 2f
                    fun corner(p: Offset, dx: Float, dy: Float) {
                        drawLine(Color.White, p, p + Offset(dx * len, 0f), w, StrokeCap.Square)
                        drawLine(Color.White, p, p + Offset(0f, dy * len), w, StrokeCap.Square)
                    }
                    corner(frame.topLeft + Offset(-o, -o), 1f, 1f)
                    corner(frame.topRight + Offset(o, -o), -1f, 1f)
                    corner(frame.bottomLeft + Offset(-o, o), 1f, -1f)
                    corner(frame.bottomRight + Offset(o, o), -1f, -1f)
                    // Penanda tengah sisi
                    val mid = 14.dp.toPx()
                    drawLine(Color.White, Offset(frame.center.x - mid, frame.top - o), Offset(frame.center.x + mid, frame.top - o), w)
                    drawLine(Color.White, Offset(frame.center.x - mid, frame.bottom + o), Offset(frame.center.x + mid, frame.bottom + o), w)
                    drawLine(Color.White, Offset(frame.left - o, frame.center.y - mid), Offset(frame.left - o, frame.center.y + mid), w)
                    drawLine(Color.White, Offset(frame.right + o, frame.center.y - mid), Offset(frame.right + o, frame.center.y + mid), w)
                }
            }

            // Pilihan rasio
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(CropAspect.entries) { option ->
                    val active = option == aspect
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (active) Color.White else EditorColors.Field)
                            .clickable { applyAspect(option) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.labelRes?.let { stringResource(it) } ?: option.label,
                            color = if (active) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Putar & cermin
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorIconButton(
                    icon = R.drawable.zm_ic_rotate,
                    contentDescription = stringResource(R.string.zm_rotate),
                    onClick = {
                        val rect = currentRect().rotatedCcw()
                        // Memutar gambar yang dicerminkan = mencerminkan gambar yang diputar arah sebaliknya.
                        quarterTurns += if (flipped) -1 else 1
                        aspect = aspect.rotated()
                        pendingRect = rect
                    },
                )
                Spacer(Modifier.weight(1f))
                EditorIconButton(
                    icon = R.drawable.zm_ic_flip,
                    contentDescription = stringResource(R.string.zm_mirror),
                    onClick = {
                        val rect = currentRect().flippedHorizontally()
                        flipped = !flipped
                        pendingRect = rect
                    },
                )
            }
        }
    }
}

private fun hitHandle(frame: Rect, p: Offset, slop: Float): Handle? {
    if (frame.width <= 0f) return null
    val nearL = abs(p.x - frame.left) <= slop
    val nearR = abs(p.x - frame.right) <= slop
    val nearT = abs(p.y - frame.top) <= slop
    val nearB = abs(p.y - frame.bottom) <= slop
    val withinX = p.x in (frame.left - slop)..(frame.right + slop)
    val withinY = p.y in (frame.top - slop)..(frame.bottom + slop)
    return when {
        nearL && nearT -> Handle.TopLeft
        nearR && nearT -> Handle.TopRight
        nearL && nearB -> Handle.BottomLeft
        nearR && nearB -> Handle.BottomRight
        nearL && withinY -> Handle.Left
        nearR && withinY -> Handle.Right
        nearT && withinX -> Handle.Top
        nearB && withinX -> Handle.Bottom
        else -> null
    }
}

/** Geser sisi/sudut bingkai, dibatasi [bounds], ukuran minimum, dan rasio (bila dikunci). */
private fun dragFrame(
    frame: Rect,
    handle: Handle,
    delta: Offset,
    bounds: Rect,
    minSize: Float,
    ratio: Float?,
): Rect {
    var l = frame.left
    var t = frame.top
    var r = frame.right
    var b = frame.bottom
    if (ratio != null && !handle.isCorner) return frame

    if (handle.left) l = (l + delta.x).coerceIn(bounds.left, r - minSize)
    if (handle.right) r = (r + delta.x).coerceIn(l + minSize, bounds.right)
    if (handle.top) t = (t + delta.y).coerceIn(bounds.top, b - minSize)
    if (handle.bottom) b = (b + delta.y).coerceIn(t + minSize, bounds.bottom)

    if (ratio != null) {
        // Lebar menentukan tinggi; bila tinggi melewati batas, lebar ikut disesuaikan.
        var w = r - l
        var h = w / ratio
        val maxH = if (handle.top) b - bounds.top else bounds.bottom - t
        if (h > maxH) {
            h = maxH
            w = h * ratio
        }
        if (handle.left) l = r - w else r = l + w
        if (handle.top) t = b - h else b = t + h
    }
    return Rect(l, t, r, b)
}
