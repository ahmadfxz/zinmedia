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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.core.content.FileProvider
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
import com.zinmedia.videoeditor.widget.DurationBottomRow
import com.zinmedia.videoeditor.widget.ExportProgress
import com.zinmedia.videoeditor.widget.ExportResultScreen
import com.zinmedia.videoeditor.widget.OverlayControls
import com.zinmedia.videoeditor.widget.TrimControls
import com.zinmedia.videoeditor.widget.VideoPreviewPlayer
import com.zinmedia.videoeditor.ui.BottomChatDetail
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
    modifier: Modifier = Modifier
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

    var exportedPath by remember { mutableStateOf<String?>(null) }

    var showDurationStatus by remember { mutableStateOf(false) }
    var durationStatus by remember { mutableIntStateOf(24) }
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
    Box(
        //  contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 30.dp)
                .aspectRatio(aspect)
                .onSizeChanged { layoutSize ->
                    videoWidthPx = (layoutSize.width).toFloat()
                    videoHeightPx = (layoutSize.height).toFloat()
                },
        ) {
            exoPlayer.value?.let {
                VideoPreviewPlayer(
                    player = it,
                    modifier = Modifier
                )
            }

            TextOverlays(vm, overlays, videoWidthPx, videoHeightPx)
            StickerOverlays(vm, overlays, videoWidthPx, videoHeightPx)
//            AddAudioButtonWidget(
//                title = titleAudio.value,
//                onClick = {
//                    sheetMode = BottomSheetMode.AUDIO
//                },
//                onClear = {
//                    titleAudio.value = null
//                    vm.setAudioReplacement(context, null)
//                },
//                modifier = Modifier.align(Alignment.TopCenter)
//            )


            DrawingCanvas(drawViewModel)

            if (exporting)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            if (exporting)
                ExportProgress(progress, modifier = Modifier.align(Alignment.Center))

        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isDrawingEnabled && !showTextEditor,
                enter = fadeIn(tween(200)) +
                        slideInHorizontally(tween(200), initialOffsetX = { it }),
                exit = fadeOut(tween(200)) +
                        slideOutHorizontally(tween(200), targetOffsetX = { it })
            ) {
                OverlayControls(
                    onFilter = { sheetMode = BottomSheetMode.FILTER },
                    onText = { showTextEditor = true },
                    onSticker = { sheetMode = BottomSheetMode.STICKER },
                    onDraw = {
                        drawViewModel.setDrawingEnabled(true)
                    },
                    onDismiss = onDismiss
                )
            }
            TrimControls(
                videoUri = videoUri,
                videoDurationMs = duration,
                maxTrimMs = 60_000,
                currentPlayTimeMs = currentTimeMs,
                onTrimChanged = { start, end ->
                    vm.setTrim(start, end)
                },
                modifier = if (!isDrawingEnabled && !showTextEditor) Modifier else Modifier.height(0.dp)
            )

        }
        androidx.compose.animation.AnimatedVisibility(
            visible = isDrawingEnabled,
            enter = fadeIn(tween(200)) +
                    slideInHorizontally(tween(200), initialOffsetX = { -40 }),
            exit = fadeOut(tween(200)) +
                    slideOutHorizontally(tween(200), targetOffsetX = { -40 })
        ) {
            DrawingControls(drawViewModel, onDoneDraw = {
                vm.setDrawOverlay(it)
            }, modifier = Modifier.align(Alignment.TopCenter))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(vertical = 4.dp)
        ) {
            BottomChatDetail(
                enableButton = !exporting && !isDrawingEnabled && !showTextEditor,
                inputTitle = "Tambahkan keterangan",
                onSend = { message ->
                    vm.exportVideo(
                        context = context,
                        cacheDir = context.cacheDir,
                        outputFile = null
                    ) { path, error ->
                        if (path != null) {
                            exportedPath = path
                            //   sheetMode = BottomSheetMode.RESULT

                            //        konversi file path → uri via FileProvider
                            val file = File(path)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            onExportFinished(uri, message)
//
                            exoPlayer.let { it.value?.pause() }
                        } else error?.printStackTrace()
                    }
                },
                modifier = Modifier.imePadding()
            )

//            Box(
//                modifier = Modifier.fillMaxWidth(),
//                contentAlignment = Alignment.CenterStart
//            ) {
//
//                // Panel Duration
//                androidx.compose.animation.AnimatedVisibility(
//                    visible = showDurationStatus,
//                    enter = fadeIn(tween(200)) +
//                            slideInHorizontally(tween(200), initialOffsetX = { it }),
//                    exit = fadeOut(tween(200)) +
//                            slideOutHorizontally(tween(200), targetOffsetX = { it })
//                ) {
//                    DurationBottomRow(
//                        inisialValue = durationStatus,
//                        onSelect = {
//                            durationStatus = it
//                            showDurationStatus = false
//                        }
//                    )
//                }
//
//                // Row kecil
//                androidx.compose.animation.AnimatedVisibility(
//                    visible = !showDurationStatus,
//                    enter = fadeIn(tween(200)) +
//                            slideInHorizontally(tween(200), initialOffsetX = { -40 }),
//                    exit = fadeOut(tween(200)) +
//                            slideOutHorizontally(tween(200), targetOffsetX = { -40 })
//                ) {
//                    Row(
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.SpaceBetween,
//                        modifier = Modifier
//                            .padding(vertical = 6.dp)
//                            .clickable { showDurationStatus = true }
//                    ) {
//                        Icon(
//                            painter = painterResource(R.drawable.ic_clock),
//                            contentDescription = "Clock",
//                            tint = Color.Green.copy(alpha = 0.5f),
//                            modifier = Modifier.size(25.dp)
//                        )
//                        Spacer(Modifier.width(6.dp))
//
//                        Text(
//                            "Status $durationStatus jam",
//                            style = MaterialTheme.typography.labelSmall,
//                        )
//                    }
//                }
//            }


        }

    }

    if (sheetMode != BottomSheetMode.NONE) {
        ModalBottomSheet(
            onDismissRequest = { sheetMode = BottomSheetMode.NONE },
            sheetState = bottomSheetState,
            containerColor = Color(0xFF111111)
        ) {
            when (sheetMode) {
                BottomSheetMode.STICKER -> {

                    StickerBottomSheetContent(
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

