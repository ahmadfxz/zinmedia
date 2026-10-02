@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.zinmedia.videoeditor

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
import androidx.compose.foundation.layout.imePadding
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
import com.zinmedia.videoeditor.audio.AddAudioBottomSheet
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
import com.zinmedia.videoeditor.overlays.TextOverlays
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


@OptIn(UnstableApi::class)
@Composable
fun VideoEditorScreen(
    videoUri: Uri,
    onExportFinished: (Uri, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    recipientLabel: String = "Status",
) {
    val owner = LocalViewModelStoreOwner.current
        ?: throw IllegalStateException("No ViewModelStoreOwner found")

    val context = LocalContext.current

    val repository = VideoRepository()

    val factory = remember { VideoEditorViewModelFactory(repository = repository) }

    val vm: VideoEditorViewModel = viewModel(
        modelClass = VideoEditorViewModel::class.java,
        viewModelStoreOwner = owner,
        factory = factory
    )

    val exporting by vm.exporting.collectAsState()
    val progress by vm.exportProgress.collectAsState()

    var showTextEditor by remember { mutableStateOf(false) }


    val duration = vm.videoDurationMs.collectAsState().value
    val currentTimeMs = vm.currentPlayTimeMs.collectAsState().value
    val overlays by vm.overlays.collectAsState()
    val filterEffectUrl by vm.cubeUrl.collectAsState()
    val drawViewModel = remember { DrawingViewModel() }
    val isDrawingEnabled = drawViewModel.uiState.isDrawingEnabled
    // ukuran video
    var videoWidthPx by remember { mutableStateOf(1f) }
    var videoHeightPx by remember { mutableStateOf(1f) }
    val fontManager = remember { FontManager(context) }

    BackHandler(enabled = isDrawingEnabled) {
        drawViewModel.setDrawingEnabled(false)
    }
    // load video
    LaunchedEffect(videoUri) {
        vm.createExoPlayer(context)
        vm.loadUri(videoUri)
    }

    // Cleanup saat keluar composable
    DisposableEffect(Unit) {
        onDispose {
            vm.releaseExoPlayer()
        }
    }

    var sheetMode by remember { mutableStateOf(BottomSheetMode.NONE) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scope = rememberCoroutineScope()

    val exoPlayer = rememberUpdatedState(vm.exoPlayer)

    val titleAudio = remember { mutableStateOf<String?>(null) }

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
    val showChrome = !isDrawingEnabled && !showTextEditor
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

    BackHandler(enabled = !isDrawingEnabled && !exporting) { requestClose() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EditorColors.Background)
    ) {
        // Video di tengah area aman, toolbar menimpa di atas/bawah.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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
                exoPlayer.value?.let {
                    VideoPreviewPlayer(player = it, modifier = Modifier)
                }

                TextOverlays(vm, overlays, videoWidthPx, videoHeightPx)
                StickerOverlays(vm, overlays, videoWidthPx, videoHeightPx)
                DrawingCanvas(drawViewModel)

                if (exporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    )
                    ExportProgress(progress, modifier = Modifier.align(Alignment.Center))
                }
            }
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

                EditorTopBar(onClose = ::requestClose, modifier = Modifier.align(Alignment.TopCenter)) {
                    EditorIconButton(R.drawable.zm_ic_sticker, "Stiker", { sheetMode = BottomSheetMode.STICKER })
                    EditorIconButton(R.drawable.zm_ic_text, "Teks", { showTextEditor = true })
                    EditorIconButton(R.drawable.zm_ic_pen, "Gambar", { drawViewModel.setDrawingEnabled(true) })
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FilterHint(
                        expanded = sheetMode == BottomSheetMode.FILTER,
                        onClick = { sheetMode = BottomSheetMode.FILTER },
                    )
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
                                    exoPlayer.value?.pause()
                                } else {
                                    error?.printStackTrace()
                                }
                            }
                        },
                    )
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
            DrawingControls(drawViewModel, onDoneDraw = { vm.setDrawOverlay(it) })
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
                            vm.addOverlay(
                                Overlay(
                                    type = Overlay.Type.TEXT,
                                    text = emoji,
                                    fontSize = 50.sp,
                                    scale = 0.8f,
                                )
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

                BottomSheetMode.AUDIO -> {
                    AddAudioBottomSheet(
                        onPlay = {},
                        onSelect = {
                            vm.setAudioReplacement(context, it.url)
                            titleAudio.value = "${it.title} - ${it.artist}"
                            sheetMode = BottomSheetMode.NONE
                        }
                    )
                }

                BottomSheetMode.RESULT -> {
                    // if (exportedPath != null) {
//                        ExportResultScreen(
//                            exportedPath = exportedPath!!,
//                            onClose = { sheetMode = BottomSheetMode.NONE },
//                            onShare = { uri ->
//                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
//                                    type = "video/mp4"
//                                    putExtra(Intent.EXTRA_STREAM, uri)
//                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                                }
//                                context.startActivity(
//                                    Intent.createChooser(
//                                        shareIntent,
//                                        "Share Video"
//                                    )
//                                )
//                            }
//                        )
                    //     }
                }

                else -> Unit
            }
        }
    }
    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDiscard = onDismiss,
            onDismiss = { showDiscardDialog = false },
        )
    }

    if (showTextEditor) {
        TextEditorDialog(
            initialText = "",
            initialColor = Color.White,
            initialBackgroundColor = Color.Transparent,
            fonts = fontManager.fonts,
            onDismissRequest = {
                showTextEditor = false
            },
            onTextEdited = { inputText, backgroundColor, colorCode, fontId ->
                vm.addOverlay(
                    Overlay(
                        type = Overlay.Type.TEXT,
                        text = inputText,
                        color = Color(colorCode),
                        bgcolor = backgroundColor,
                        typeface = fontManager.getFont(fontId),
                        fontSize = 50.sp,
                        posXpx = 0f,  // misal default pixel X
                        posYpx = 0f,
                        scale = 0.5f// misal default pixel Y
                    )
                )
                showTextEditor = false
            }
        )
    }

}


enum class BottomSheetMode {
    NONE,
    STICKER,
    FILTER,
    AUDIO,
    RESULT
}

