@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.zinmedia.videoeditor

import androidx.compose.ui.unit.IntSize
import com.zinmedia.videoeditor.overlays.TrashTarget
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import com.zinmedia.videoeditor.textlayer.renderTextLayer
import com.zinmedia.videoeditor.textlayer.TextLayer
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.zinmedia.videoeditor.R
import com.zinmedia.videoeditor.data.FontManager
import com.zinmedia.videoeditor.data.Overlay
import com.zinmedia.videoeditor.data.VideoEditorViewModel
import com.zinmedia.videoeditor.data.VideoEditorViewModelFactory
import com.zinmedia.videoeditor.data.repository.VideoRepository
import com.zinmedia.videoeditor.draw.DrawingCanvas
import com.zinmedia.videoeditor.draw.DrawingControls
import com.zinmedia.videoeditor.draw.DrawingViewModel
import com.zinmedia.videoeditor.overlays.AddFilterBottomSheet
import com.zinmedia.videoeditor.overlays.StickerBottomSheetContent
import com.zinmedia.videoeditor.overlays.StickerOverlays
import com.zinmedia.videoeditor.overlays.TextEditorDialog
import com.zinmedia.videoeditor.widget.ExportProgress
import com.zinmedia.videoeditor.widget.TrimControls
import com.zinmedia.videoeditor.widget.VideoPreviewPlayer
import com.zinmedia.videoeditor.ui.EditorBottomSheet
import com.zinmedia.videoeditor.ui.DiscardChangesDialog
import com.zinmedia.videoeditor.ui.EditorCaptionBar
import com.zinmedia.videoeditor.ui.EditorColors
import com.zinmedia.videoeditor.ui.EditorIconButton
import com.zinmedia.videoeditor.ui.EditorScrim
import com.zinmedia.videoeditor.ui.EditorTopBar
import com.zinmedia.videoeditor.ui.FilterHint
import androidx.compose.runtime.saveable.rememberSaveable
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Clock
import kotlinx.coroutines.launch
import java.io.File
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import androidx.compose.ui.layout.ContentScale


/**
 * Editor video.
 *
 * @param sessionKey kunci state editor (trim, overlay, filter, coretan). Beri kunci berbeda per item
 *   bila beberapa video diedit dalam satu layar.
 * @param active hanya editor aktif yang membuat pemutar video; yang tidak aktif menampilkan frame awal.
 * @param standalone `false` bila dipakai di dalam editor lain: tombol tutup & back diserahkan ke
 *   [onDismiss], dan area keterangan/kirim diganti [bottomContent].
 * @param onToolActiveChange dipanggil saat mode teks/gambar dibuka atau ditutup.
 */
@OptIn(UnstableApi::class)
@Composable
public fun VideoEditorScreen(
    videoUri: Uri,
    onExportFinished: (Uri, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    recipientLabel: String = "Status",
    sessionKey: String? = null,
    active: Boolean = true,
    standalone: Boolean = true,
    bottomContent: (@Composable () -> Unit)? = null,
    onToolActiveChange: (Boolean) -> Unit = {},
) {
    val owner = LocalViewModelStoreOwner.current
        ?: throw IllegalStateException("No ViewModelStoreOwner found")

    val context = LocalContext.current

    val factory = remember { VideoEditorViewModelFactory(repository = VideoRepository()) }

    val vm: VideoEditorViewModel = viewModel(
        modelClass = VideoEditorViewModel::class.java,
        viewModelStoreOwner = owner,
        key = sessionKey,
        factory = factory
    )

    val exporting by vm.exporting.collectAsState()
    val progress by vm.exportProgress.collectAsState()

    var showTextEditor by remember { mutableStateOf(false) }
    // Lapisan teks yang sedang diedit (null = teks baru).
    var editingText by remember { mutableStateOf<Overlay?>(null) }


    val duration = vm.videoDurationMs.collectAsState().value
    val currentTimeMs = vm.currentPlayTimeMs.collectAsState().value
    val overlays by vm.overlays.collectAsState()
    val filterEffectUrl by vm.cubeUrl.collectAsState()
    val drawViewModel: DrawingViewModel = viewModel(
        viewModelStoreOwner = owner,
        key = sessionKey?.let { "$it-draw" },
        initializer = { DrawingViewModel() },
    )
    val isDrawingEnabled = drawViewModel.uiState.isDrawingEnabled
    // ukuran video
    var videoWidthPx by remember { mutableFloatStateOf(1f) }
    var videoHeightPx by remember { mutableFloatStateOf(1f) }
    val fontManager = remember { FontManager(context) }

    BackHandler(enabled = isDrawingEnabled) {
        drawViewModel.setDrawingEnabled(false)
    }
    // Pemutar hanya dibuat untuk editor yang aktif; state edit tetap tersimpan di ViewModel.
    var player by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    // Frame pengganti tetap tampil sampai pemutar benar-benar menggambar frame pertama (tanpa kedip hitam).
    var firstFrameRendered by remember { mutableStateOf(false) }
    LaunchedEffect(videoUri) { vm.prefetchVideoSize(context, videoUri) }
    LaunchedEffect(videoUri, active) {
        if (active) {
            firstFrameRendered = false
            vm.createExoPlayer(context)
            vm.exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrameRendered = true
                }
            })
            vm.loadUri(videoUri)
            player = vm.exoPlayer
        } else {
            player = null
            firstFrameRendered = false
            vm.releaseExoPlayer()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player = null
            vm.releaseExoPlayer()
        }
    }

    var sheetMode by remember { mutableStateOf(BottomSheetMode.NONE) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scope = rememberCoroutineScope()



    // Rasio bingkai preview mengikuti rasio asli video.
    // Default 9:16 dipakai sementara sebelum ukuran video
    // diketahui (mis. saat baru dibuka).
    val nativeVideoWidth by vm.videoWidth.collectAsState()
    val nativeVideoHeight by vm.videoHeight.collectAsState()
    val aspect = if (nativeVideoWidth > 0 && nativeVideoHeight > 0) {
        (nativeVideoWidth.toFloat() / nativeVideoHeight.toFloat())
            .coerceIn(9f / 21f, 21f / 9f)
    } else {
        9f / 16f
    }
    var caption by rememberSaveable { mutableStateOf("") }
    // Overlay sedang diseret: chrome disembunyikan dan tempat sampah tampil di bawah.
    var draggingOverlay by remember { mutableStateOf(false) }
    var overTrash by remember { mutableStateOf(false) }
    var trashBounds by remember { mutableStateOf(Rect.Zero) }
    val showChrome = !isDrawingEnabled && !showTextEditor && !draggingOverlay
    var showDiscardDialog by remember { mutableStateOf(false) }
    val trimStartMs by vm.startMs.collectAsState()
    val trimEndMs by vm.endMs.collectAsState()

    // Ada teks/stiker/coretan, filter, trim, atau keterangan yang belum dikirim.
    fun hasChanges(): Boolean =
        overlays.isNotEmpty() ||
            filterEffectUrl.isNotEmpty() ||
            caption.isNotBlank() ||
            trimStartMs > 0 ||
            (duration > 0 && trimEndMs in 1 until duration)

    fun requestClose() {
        if (hasChanges()) showDiscardDialog = true else onDismiss()
    }

    BackHandler(enabled = standalone && !isDrawingEnabled && !exporting) { requestClose() }

    val toolActive = isDrawingEnabled || showTextEditor || draggingOverlay
    // Laporkan di frame yang sama (bukan LaunchedEffect yang telat beberapa frame), agar elemen
    // milik layar induk langsung tersembunyi saat mode teks/gambar dibuka.
    SideEffect { onToolActiveChange(toolActive) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EditorColors.Background)
    ) {
        // Video di tengah area aman, toolbar menimpa di atas/bawah.
        var areaSize by remember { mutableStateOf(IntSize.Zero) }
        LaunchedEffect(areaSize, videoWidthPx) {
            vm.setPreviewLayout(videoWidthPx, areaSize.width.toFloat(), areaSize.height.toFloat())
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .onSizeChanged { areaSize = it }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .aspectRatio(aspect)
                    .onSizeChanged { layoutSize ->
                        videoWidthPx = layoutSize.width.toFloat()
                        videoHeightPx = layoutSize.height.toFloat()
                    },
            ) {
                val currentPlayer = player
                currentPlayer?.let {
                    VideoPreviewPlayer(player = it, modifier = Modifier)
                }
                if (currentPlayer == null || !firstFrameRendered) {
                    // Frame awal sebagai pengganti selama pemutar belum ada atau belum menggambar frame pertama.
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(videoUri)
                            .decoderFactory(VideoFrameDecoder.Factory())
                            .videoFrameMillis(0)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                StickerOverlays(
                    viewModel = vm,
                    overlays = overlays,
                    videoWidthPx = videoWidthPx,
                    videoHeightPx = videoHeightPx,
                    trashBounds = { trashBounds },
                    onDragChange = { dragging, over ->
                        draggingOverlay = dragging
                        overTrash = over
                    },
                    hiddenOverlayId = editingText?.id,
                    onTextDoubleTap = { overlay ->
                        onToolActiveChange(true)
                        editingText = overlay
                        showTextEditor = true
                    },
                )

                if (exporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    )
                    ExportProgress(progress, modifier = Modifier.align(Alignment.Center))
                }
            }
            // Coretan boleh di luar frame video (seluas area editor); kanvas ekspor ikut diperluas.
            DrawingCanvas(drawViewModel)
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                EditorScrim(top = true, modifier = Modifier.align(Alignment.TopCenter))
                EditorScrim(top = false, modifier = Modifier.align(Alignment.BottomCenter))

                EditorTopBar(
                    onClose = { if (standalone) requestClose() else onDismiss() },
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    if (VideoEditorConfig.stickers.isNotEmpty() || VideoEditorConfig.emojis.isNotEmpty()) {
                        EditorIconButton(R.drawable.zm_ic_sticker, stringResource(R.string.zm_sticker), { sheetMode = BottomSheetMode.STICKER })
                    }
                    EditorIconButton(R.drawable.zm_ic_text, stringResource(R.string.zm_text), { onToolActiveChange(true); editingText = null; showTextEditor = true })
                    EditorIconButton(R.drawable.zm_ic_pen, stringResource(R.string.zm_draw), { onToolActiveChange(true); drawViewModel.setDrawingEnabled(true) })
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (VideoEditorConfig.filters.isNotEmpty()) {
                        FilterHint(
                            expanded = sheetMode == BottomSheetMode.FILTER,
                            onClick = { sheetMode = BottomSheetMode.FILTER },
                        )
                    }
                    if (bottomContent != null) {
                        bottomContent()
                    } else {
                        EditorCaptionBar(
                            caption = caption,
                            onCaptionChange = { caption = it },
                            recipientLabel = recipientLabel,
                            sendEnabled = !exporting,
                            onSend = {
                                vm.exportVideo(
                                    context = context,
                                    cacheDir = VideoEditorFileProvider.outputDir(context),
                                    outputFile = null
                                ) { path, error ->
                                    if (path != null) {
                                        val uri = VideoEditorFileProvider.uriFor(context, File(path))
                                        onExportFinished(uri, caption)
                                        player?.pause()
                                    } else {
                                        android.util.Log.w("VideoEditorScreen", "Ekspor video gagal", error)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        // Timeline trim tetap di-compose (tinggi 0 saat disembunyikan) agar posisi trim tidak hilang.
        TrimControls(
            videoUri = videoUri,
            videoDurationMs = duration,
            maxTrimMs = 60_000,
            currentPlayTimeMs = currentTimeMs,
            onTrimChanged = { start, end -> vm.setTrim(start, end) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
                .then(if (showChrome) Modifier else Modifier.height(0.dp))
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = isDrawingEnabled,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            DrawingControls(
                drawViewModel,
                outputSize = {
                    val scale = vm.drawingScale(videoWidthPx)
                    (areaSize.width * scale).toInt() to (areaSize.height * scale).toInt()
                },
                onDoneDraw = { bitmap ->
                    vm.setDrawOverlay(
                        bitmap = bitmap,
                        areaWidthPx = areaSize.width.toFloat(),
                        areaHeightPx = areaSize.height.toFloat(),
                        videoWidthPx = videoWidthPx,
                        videoHeightPx = videoHeightPx,
                    )
                },
            )
        }

        if (draggingOverlay) {
            val hitSlop = with(LocalDensity.current) { 24.dp.toPx() }
            TrashTarget(
                active = overTrash,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
                    .onGloballyPositioned { trashBounds = it.boundsInRoot().inflate(hitSlop) },
            )
        }
    }

    if (sheetMode != BottomSheetMode.NONE) {
        EditorBottomSheet(
            onDismissRequest = { sheetMode = BottomSheetMode.NONE },
            sheetState = bottomSheetState,
        ) {
            when (sheetMode) {
                BottomSheetMode.STICKER -> {

                    StickerBottomSheetContent(
                        onEmojiClick = { emoji ->
                            // Emoji ditempel sebagai stiker (bitmap) agar ukurannya di hasil sama dengan preview.
                            vm.addOverlay(
                                Overlay(bitmap = emojiBitmap(emoji).asImageBitmap())
                            )
                            sheetMode = BottomSheetMode.NONE
                        },
                        onStickerClick = {
                            scope.launch {
                                val sticker = vm.addStickerFromUrl(
                                    context,
                                    it,
                                    posXpx = 0f,
                                    posYpx = 0f,
                                    scale = 0.65f
                                )
                                vm.addOverlay(sticker)
                                sheetMode = BottomSheetMode.NONE
                            }
                        }
                    )
                }

                BottomSheetMode.FILTER -> {
                    AddFilterBottomSheet(
                        onFilterClick = { item ->
                            vm.selectCubeEffect(item.cubeUrl)
                        },
                        selectedUrl = filterEffectUrl
                    )
                }

                else -> Unit
            }
        }
    }
    if (standalone && showDiscardDialog) {
        DiscardChangesDialog(
            onDiscard = onDismiss,
            onDismiss = { showDiscardDialog = false },
        )
    }

    if (showTextEditor) {
        val editing = editingText
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        TextEditorDialog(
            initial = editing?.textLayer ?: TextLayer(""),
            fonts = fontManager.fonts,
            onDismissRequest = {
                showTextEditor = false
                editingText = null
            },
            onDone = { layer, layoutWidthPx ->
                // Gambar yang sama persis dengan tampilan di mode teks; ukuran hasil ekspor ikut preview.
                val typeface = fontManager.getFont(layer.fontIndex)
                val image = renderTextLayer(
                    layer = layer,
                    fontFamily = FontFamily(typeface),
                    typeface = typeface,
                    measurer = textMeasurer,
                    density = density,
                    layoutWidthPx = layoutWidthPx,
                )
                if (editing != null) {
                    vm.updateOverlay(editing.copy(bitmap = image, textLayer = layer, widthFraction = 0f))
                } else {
                    vm.addOverlay(Overlay(bitmap = image, textLayer = layer))
                }
            },
        )
    }

}


internal enum class BottomSheetMode {
    NONE,
    STICKER,
    FILTER,
}

/** Gambar [emoji] ke bitmap persegi transparan, untuk ditempel sebagai stiker. */
private fun emojiBitmap(emoji: String, sizePx: Int = 256): android.graphics.Bitmap {
    val bitmap = androidx.core.graphics.createBitmap(sizePx, sizePx)
    val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx * 0.8f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val baseline = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
    android.graphics.Canvas(bitmap).drawText(emoji, sizePx / 2f, baseline, paint)
    return bitmap
}
