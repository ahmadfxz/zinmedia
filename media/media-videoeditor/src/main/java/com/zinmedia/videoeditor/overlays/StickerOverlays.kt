package com.zinmedia.videoeditor.overlays

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.zinmedia.videoeditor.data.Overlay
import com.zinmedia.videoeditor.data.VideoEditorViewModel
import compose.icons.EvaIcons
import compose.icons.evaicons.Outline
import compose.icons.evaicons.outline.Trash
import compose.icons.evaicons.outline.Trash2
import kotlin.math.roundToInt

/**
 * Stiker & teks di atas video: geser, cubit (zoom/putar), dan seret ke [TrashTarget] untuk menghapus.
 * Teks diketuk dua kali untuk diedit.
 *
 * @param trashBounds batas tempat sampah (koordinat root); overlay dihapus bila jari dilepas di atasnya.
 * @param onDragChange dipanggil saat overlay mulai/selesai diseret dan saat jari masuk/keluar tempat sampah.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun StickerOverlays(
    viewModel: VideoEditorViewModel,
    overlays: List<Overlay>,
    videoWidthPx: Float,
    videoHeightPx: Float,
    trashBounds: () -> Rect,
    onDragChange: (dragging: Boolean, overTrash: Boolean) -> Unit,
    hiddenOverlayId: String? = null,
    onTextDoubleTap: (Overlay) -> Unit = {},
) {
    val currentTrashBounds by rememberUpdatedState(trashBounds)
    val currentOnDragChange by rememberUpdatedState(onDragChange)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        overlays.filter { it.id != hiddenOverlayId }.forEach { overlay ->
            key(overlay.id) {
                // Gesture berjalan lama; selalu pakai data overlay terbaru (mis. setelah teks diedit).
                val currentOverlay by rememberUpdatedState(overlay)
                var scale by remember { mutableFloatStateOf(1f) }
                var rotation by remember { mutableFloatStateOf(0f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                var overTrash by remember { mutableStateOf(false) }
                val coordinates = remember { CoordinatesRef() }

                val state = rememberTransformableState { zoom, pan, rotate ->
                    scale = (scale * zoom).coerceIn(MinScale, MaxScale)
                    rotation += rotate
                    offset += pan
                }

                // Teks tampil seukuran pikselnya (sama dengan mode teks); stiker di-fit ke kotak persegi.
                val density = LocalDensity.current
                val bitmapW = overlay.bitmap.width.toFloat()
                val bitmapH = overlay.bitmap.height.toFloat()
                val displaySize = if (overlay.textLayer != null) {
                    with(density) { DpSize(bitmapW.toDp(), bitmapH.toDp()) }
                } else {
                    DpSize(StickerBaseSize, StickerBaseSize)
                }
                val baseWidthPx = if (overlay.textLayer != null) {
                    bitmapW
                } else {
                    with(density) { StickerBaseSize.toPx() } * minOf(1f, bitmapW / bitmapH)
                }
                // Lebar relatif terhadap frame; dipakai ekspor agar ukurannya sama dengan preview.
                fun widthFraction(): Float =
                    if (videoWidthPx > 1f) baseWidthPx * scale / videoWidthPx else 0f
                LaunchedEffect(videoWidthPx, overlay.bitmap) {
                    if (overlay.widthFraction == 0f && videoWidthPx > 1f) {
                        viewModel.updateOverlay(overlay.copy(widthFraction = widthFraction()))
                    }
                }

                val dragModifier = Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.pressed } ?: break
                            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (dragging) {
                                val layout = coordinates.value
                                overTrash = layout != null && layout.isAttached &&
                                    currentTrashBounds().contains(layout.localToRoot(change.position))
                                currentOnDragChange(true, overTrash)
                            }
                        } while (event.changes.any { it.pressed })

                        if (dragging) {
                            if (overTrash) {
                                viewModel.removeOverlay(currentOverlay.id)
                            } else {
                                // Pusat dalam NDC frame video; boleh di luar [-1, 1] (kanvas ekspor diperluas).
                                val anchorX = (videoWidthPx / 2f + offset.x) / videoWidthPx * 2f - 1f
                                val anchorY = -((videoHeightPx / 2f + offset.y) / videoHeightPx * 2f - 1f)
                                viewModel.updateOverlay(
                                    currentOverlay.copy(
                                        posXpx = anchorX,
                                        posYpx = anchorY,
                                        scale = scale,
                                        rotation = -rotation,
                                        widthFraction = widthFraction(),
                                    )
                                )
                            }
                            overTrash = false
                            currentOnDragChange(false, false)
                        }
                    }
                }
                val animateScale by animateFloatAsState(if (overTrash) 0.6f else 1f, label = "trashScale")
                Box(
                    Modifier
                        .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                        .onGloballyPositioned { coordinates.value = it }
                        .transformable(state)
                        .then(dragModifier)
                        .then(
                            if (overlay.textLayer != null) {
                                // Ketuk sekali untuk menggeser (seperti stiker), ketuk dua kali untuk mengedit.
                                Modifier.pointerInput(overlay.id) {
                                    detectTapGestures(onDoubleTap = { onTextDoubleTap(currentOverlay) })
                                }
                            } else {
                                Modifier
                            }
                        )
                        .scale(animateScale)
                ) {
                    Image(
                        bitmap = overlay.bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                rotationZ = rotation,
                                transformOrigin = TransformOrigin.Center
                            )
                            .size(displaySize)
                            .clipToBounds()
                    )
                }
            }
        }
    }
}

/** Tempat sampah yang muncul saat overlay diseret; membesar dan memerah saat jari di atasnya. */
@Composable
internal fun TrashTarget(active: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (active) 1.3f else 1f, label = "trash")
    Box(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .background(if (active) Color.Red.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (active) EvaIcons.Outline.Trash2 else EvaIcons.Outline.Trash,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

/** Posisi layout overlay, dibaca dari dalam gesture tanpa memicu recomposition. */
private class CoordinatesRef {
    var value: LayoutCoordinates? = null
}

private const val MinScale = 0.5f
private const val MaxScale = 3f

/** Ukuran dasar stiker di preview (sebelum dicubit/zoom). */
private val StickerBaseSize = 160.dp
