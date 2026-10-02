package com.zinmedia.videoeditor.overlays

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.zinmedia.videoeditor.data.Overlay
import com.zinmedia.videoeditor.data.VideoEditorViewModel
import compose.icons.EvaIcons
import compose.icons.evaicons.Outline
import compose.icons.evaicons.outline.Trash
import compose.icons.evaicons.outline.Trash2
import kotlin.math.roundToInt


@UnstableApi
@Composable
fun TextOverlays(
    viewModel: VideoEditorViewModel,
    overlays: List<Overlay>,
    videoWidthPx: Float,
    videoHeightPx: Float,
) {
    var showDeleteIcon by remember { mutableStateOf(false) }

    val deleteZoneHeight = videoHeightPx * 0.10f      // 15% bawah layar
    val deleteZoneStartX = videoWidthPx * 0.33f       // ⅓ kiri
    val deleteZoneEndX = videoWidthPx * 0.66f         // ⅓ kanan (middle area)

    // State global untuk semua stiker: overlayId -> isInDeleteArea
    val overlayDeleteMap = remember {
        mutableStateMapOf<String, Boolean>().apply {
            overlays.filter { it.type == Overlay.Type.TEXT }
                .forEach { put(it.id, false) }
        }
    }


    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        ///OVERLAYS TEXT
        overlays.filter { it.type == Overlay.Type.TEXT }
            .forEach { overlay ->
                key(overlay.id) {
                    val text = overlay.text ?: ""
                    // gunakan posisi pixel sebagai state
                    val minScale = 0.5f
                    val maxScale = 1f

                    var scale by remember { mutableFloatStateOf(0.5f) }
                    var rotation by remember { mutableFloatStateOf(0f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }

                    val state = rememberTransformableState { zoom, pan, rotate ->
                        // scale dengan batasan
                        scale = (scale * zoom).coerceIn(minScale, maxScale)

                        rotation += rotate
                        offset += pan
                    }

                    val textMeasurer = rememberTextMeasurer()
                    val textLayoutResult = textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = TextStyle(fontSize = overlay.fontSize)
                    )

                    val textWidth = textLayoutResult.size.width.toFloat()
                    val textHeight = textLayoutResult.size.height.toFloat()
                    val animateScale by animateFloatAsState(
                        if (overlayDeleteMap[overlay.id] == true) 0.6f else 1f,
                        label = ""
                    )
                    val absX = videoWidthPx / 2f + offset.x
                    val absY = videoHeightPx / 2f + offset.y

                    overlayDeleteMap[overlay.id] =
                        absY > videoHeightPx - deleteZoneHeight &&
                                absX > deleteZoneStartX &&
                                absX < deleteZoneEndX
                    val endGestureModifier = Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false)
                                showDeleteIcon = true
                                do {
                                    val event = awaitPointerEvent()

                                } while (event.changes.any { it.pressed })

                                showDeleteIcon = false
//
                                if (overlayDeleteMap[overlay.id] == true) {
                                    Log.d("HAPUS STIKER", "DELETE STIKER")
                                    viewModel.removeOverlay(overlay.id)
                                } else {
                                    val absoluteX = videoWidthPx / 2f + offset.x
                                    val absoluteY = videoHeightPx / 2f + offset.y

                                    var anchorX = (absoluteX / videoWidthPx) * 2f - 1f
                                    var anchorY = -((absoluteY / videoHeightPx) * 2f - 1f)

                                    anchorX = anchorX.coerceIn(-1f, 1f)
                                    anchorY = anchorY.coerceIn(-1f, 1f)

                                    viewModel.updateOverlay(
                                        overlay.copy(
                                            posXpx = anchorX,
                                            posYpx = anchorY,
                                            scale = scale,
                                            rotation = -rotation
                                        )
                                    )

                                }
                                overlayDeleteMap.keys.forEach {
                                    overlayDeleteMap[it] = false
                                }
                            }
                        }
                    }

                    Box(
                        Modifier
                            .offset {
                                IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
                            }
                            .transformable(state)
                            .then(endGestureModifier)
                            .scale(animateScale)
                    ) {
                        Text(
                         //   softWrap = false,
                            text = text,
                            color = overlay.color,
                            fontSize = overlay.fontSize,
                            fontFamily = FontFamily(overlay.typeface),
                            style = TextStyle(
                                background = overlay.bgcolor // background di area teks
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    rotationZ = rotation,
                                    transformOrigin = TransformOrigin.Center
                                )
                                // .size(250.dp)
                                .clipToBounds()
                            //  .offset { centerOffset(offsetX, offsetY, textWidth, textHeight) }
//                                .pointerInput(overlay.id) {
//                                    detectDragGestures(
//                                        onDragStart = {
//                                            showDeleteIcon = true
//                                        },
//                                        onDragEnd = {
//                                            showDeleteIcon = false
//                                            if (overlayDeleteMap[overlay.id] == true) {
//                                                Log.d("HAPUS STIKER", "DELETE STIKER")
//                                                viewModel.removeOverlay(overlay.id)
//                                            } else {
//                                                // konversi ke anchor -1..1
//                                                val anchorX = pixelToAnchorX(offsetX, videoWidthPx)
//                                                val anchorY = pixelToAnchorY(offsetY, videoHeightPx)
//
//                                                viewModel.updateOverlay(
//                                                    overlay.copy(
//                                                        posXpx = anchorX,
//                                                        posYpx = anchorY
//                                                    )
//                                                )
//                                            }
//                                            overlayDeleteMap.keys.forEach {
//                                                overlayDeleteMap[it] = false
//                                            }
//                                        }
//                                    ) { change, dragAmount ->
//                                        change.consume()
//
//                                        offsetX =
//                                            (offsetX + dragAmount.x).coerceIn(0f, videoWidthPx)
//                                        offsetY =
//                                            (offsetY + dragAmount.y).coerceIn(0f, videoHeightPx)
//                                    }
//                                }
                        )
                    }
                }
            }
        if (showDeleteIcon) {
            val anyInDeleteArea = overlayDeleteMap.values.any { it }
            val scale by animateFloatAsState(
                if (anyInDeleteArea) 1.4f else 1f,
                label = ""
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(50.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .background(
                        if (anyInDeleteArea) Color.Red.copy(alpha = 0.8f)
                        else Color.Gray.copy(alpha = 0.6f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (anyInDeleteArea) EvaIcons.Outline.Trash2 else EvaIcons.Outline.Trash,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

        }
    }
}


//
//@UnstableApi
//@Composable
//fun TextOverlays(
//    viewModel: VideoEditorViewModelTest,
//    overlays: List<Overlay>,
//    videoWidthPx: Float,
//    videoHeightPx: Float,
//) {
//    var showDeleteIcon by remember { mutableStateOf(false) }
//
//    val deleteZoneHeight = videoHeightPx * 0.10f      // 15% bawah layar
//    val deleteZoneStartX = videoWidthPx * 0.33f       // ⅓ kiri
//    val deleteZoneEndX = videoWidthPx * 0.66f         // ⅓ kanan (middle area)
//
//    // State global untuk semua stiker: overlayId -> isInDeleteArea
//    val overlayDeleteMap = remember {
//        mutableStateMapOf<String, Boolean>().apply {
//            overlays.filter { it.type == Overlay.Type.TEXT }
//                .forEach { put(it.id, false) }
//        }
//    }
//
//
//    Box(Modifier.fillMaxSize()) {
//        ///OVERLAYS TEXT
//        overlays.filter { it.type == Overlay.Type.TEXT }
//            .forEach { overlay ->
//                key(overlay.id) {
//                   val text = overlay.text ?: ""
//                    // gunakan posisi pixel sebagai state
//                    var offsetX by remember(overlay.id) {
//                        mutableStateOf(550f)
//                    }
//                    var offsetY by remember(overlay.id) {
//                        mutableStateOf(1000f)
//                    }
//                    val textMeasurer = rememberTextMeasurer()
//                    val textLayoutResult = textMeasurer.measure(
//                        text = AnnotatedString(text),
//                        style = TextStyle(fontSize = overlay.fontSize)
//                    )
//
//                    val textWidth = textLayoutResult.size.width.toFloat()
//                    val textHeight = textLayoutResult.size.height.toFloat()
//                    val scale by animateFloatAsState(
//                        if (overlayDeleteMap[overlay.id] == true) 0.6f else 1f,
//                        label = ""
//                    )
//                    overlayDeleteMap[overlay.id] = offsetY > videoHeightPx - deleteZoneHeight &&
//                            offsetX > deleteZoneStartX &&
//                            offsetX < deleteZoneEndX
//                    // ambil font dari ID
//                    val fontFamily = FontFamily(overlay.typeface)
//                    Text(
//                        text = text,
//                        color = overlay.color,
//                        fontSize = overlay.fontSize,
//                        fontFamily = fontFamily,
//                        style = TextStyle(
//                            background = overlay.bgcolor // background di area teks
//                        ),
//                        textAlign = TextAlign.Center,
//                        modifier = Modifier
//                            .offset { centerOffset(offsetX, offsetY, textWidth, textHeight) }
//                            .pointerInput(overlay.id) {
//                                detectDragGestures(
//                                    onDragStart = {
//                                        showDeleteIcon = true
//                                    },
//                                    onDragEnd = {
//                                        showDeleteIcon = false
//                                        if (overlayDeleteMap[overlay.id] == true) {
//                                            Log.d("HAPUS STIKER", "DELETE STIKER")
//                                            viewModel.removeOverlay(overlay.id)
//                                        } else {
//                                            // konversi ke anchor -1..1
//                                            val anchorX = pixelToAnchorX(offsetX, videoWidthPx)
//                                            val anchorY = pixelToAnchorY(offsetY, videoHeightPx)
//
//                                            viewModel.updateOverlay(
//                                                overlay.copy(
//                                                    posXpx = anchorX,
//                                                    posYpx = anchorY
//                                                )
//                                            )
//                                        }
//                                        overlayDeleteMap.keys.forEach {
//                                            overlayDeleteMap[it] = false
//                                        }
//                                    }
//                                ) { change, dragAmount ->
//                                    change.consume()
//
//                                    offsetX = (offsetX + dragAmount.x).coerceIn(0f, videoWidthPx)
//                                    offsetY = (offsetY + dragAmount.y).coerceIn(0f, videoHeightPx)
//                                }
//                            }
//
//                            .background(Color.Transparent)
//                            .scale(scale)
//                    )
//                }
//            }
//        if (showDeleteIcon) {
//            val anyInDeleteArea = overlayDeleteMap.values.any { it }
//            val scale by animateFloatAsState(
//                if (anyInDeleteArea) 1.4f else 1f,
//                label = ""
//            )
//            Box(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .size(50.dp)
//                    .graphicsLayer(scaleX = scale, scaleY = scale)
//                    .background(
//                        if (anyInDeleteArea) Color.Red.copy(alpha = 0.8f)
//                        else Color.Gray.copy(alpha = 0.6f),
//                        shape = CircleShape
//                    ),
//                contentAlignment = Alignment.Center
//            ) {
//                Icon(
//                    imageVector = if (anyInDeleteArea) EvaIcons.Outline.Trash2 else EvaIcons.Outline.Trash,
//                    contentDescription = null,
//                    tint = Color.White,
//                    modifier = Modifier.size(28.dp)
//                )
//            }
//
//        }
//    }
//}

// Hitung X,Y offset tengah
private fun centerOffset(
    offsetX: Float,
    offsetY: Float,
    objWidth: Float,
    objHeight: Float
)
        : IntOffset {
    return IntOffset(
        (offsetX - objWidth / 2).roundToInt(),
        (offsetY - objHeight / 2).roundToInt()
    )
}

private fun pixelToAnchorX(px: Float, videoWidthPx: Float): Float {
    return 2f * (px / videoWidthPx) - 1f
}

private fun pixelToAnchorY(px: Float, videoHeightPx: Float): Float {
    return 1f - (px / videoHeightPx) * 2f
}


