@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.jernih.videoeditor

import androidx.compose.material3.ExperimentalMaterial3Api


//
//@OptIn(UnstableApi::class)
//@Composable
//fun VideoEditorScreenTest(
//    videoUri: Uri,
//    onExportFinished: (Uri) -> Unit,
//    modifier: Modifier = Modifier
//) {
//    val owner = LocalViewModelStoreOwner.current
//        ?: throw IllegalStateException("No ViewModelStoreOwner found")
//
//    val context = LocalContext.current
//
//    val repository = VideoRepository()
//
//    val factory = remember { VideoEditorViewModelFactory(repository = repository) }
//
//    val vm: VideoEditorViewModelTest = viewModel(
//        modelClass = VideoEditorViewModelTest::class.java,
//        viewModelStoreOwner = owner,
//        factory = factory
//    )
//    val state by vm.state.collectAsState()
//
//    val exporting = vm.state.value.exporting
//    val progress = state.exportProgress
//
//    var showTextEditor by remember { mutableStateOf(false) }
//    var showDraw by remember { mutableStateOf(false) }
//
//    var exportedPath by remember { mutableStateOf<String?>(null) }
//    val duration = state.durationMs
//    val currentTimeMs = state.currentPosMs
//    val overlays by vm.overlays.collectAsState()
//    val filterEffectUrl = state.cubeUrl
//    val drawViewModel = remember { DrawingViewModel() }
//
//    // ukuran video
//    var videoWidthPx by remember { mutableStateOf(1f) }
//    var videoHeightPx by remember { mutableStateOf(1f) }
//    val fontManager = remember { FontManager(context) }
//
//    // load video
//    LaunchedEffect(videoUri) {
//        vm.initPlayer(context)
//        vm.loadVideo(videoUri)
//    }
//
//    // Cleanup saat keluar composable
//    DisposableEffect(Unit) {
//        onDispose {
//            vm.releasePlayer()
//        }
//    }
//
//    var sheetMode by remember { mutableStateOf(BottomSheetMode.NONE) }
//
//    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
//
//    val scope = rememberCoroutineScope()
//
//    val exoPlayer = rememberUpdatedState(vm.getPlayer())
//
//    val titleAudio = remember { mutableStateOf<String?>(null) }
//    val aspect = 9f / 16f
//    Box(
//        modifier = modifier.fillMaxSize()
//    ) {
//        Box(
//            contentAlignment = Alignment.Center,
//            modifier = Modifier
//                .aspectRatio(aspect)
//                .onSizeChanged { layoutSize ->
//                    videoWidthPx = (layoutSize.width).toFloat()
//                    videoHeightPx = (layoutSize.height).toFloat()
//                },
//        ) {
//            exoPlayer.value?.let {
//                VideoPreviewPlayer(
//                    player = it,
//                    modifier = Modifier
//                )
//            }
//
//            TextOverlays(vm, overlays, videoWidthPx, videoHeightPx)
//            StickerOverlays(vm, overlays, videoWidthPx, videoHeightPx)
////            AddAudioButtonWidget(
////                title = titleAudio.value,
////                onClick = {
////                    sheetMode = BottomSheetMode.AUDIO
////                },
////                onClear = {
////                    titleAudio.value = null
////                    vm.setAudioReplacement(context, null)
////                },
////                modifier = Modifier.align(Alignment.TopCenter)
////            )
//            if (!showDraw)
//                OverlayControls(
//                    onFilter = { sheetMode = BottomSheetMode.FILTER },
//                    onText = { showTextEditor = true },
//                    onSticker = { sheetMode = BottomSheetMode.STICKER },
//                    onDraw = {
//                        showDraw = true
//                        drawViewModel.setDrawingEnabled(true)
//                    },
//                    modifier = Modifier.align(Alignment.TopCenter)
//                )
//
//            DrawingCanvas(drawViewModel)
//            if (showDraw)
//                DrawingControls(drawViewModel, onDoneDraw = {
//                    vm.setDrawOverlay(it)
//                    showDraw = false
//                }, modifier = Modifier.align(Alignment.TopCenter))
//
//            TrimControls(
//                videoDurationMs = duration,
//                maxTrimMs = 15_000,
//                currentPlayTimeMs = currentTimeMs,
//                onTrimChanged = { start, end ->
//                    vm.setTrim(context, start, end)
//                },
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .padding(bottom = 12.dp)
//            )
//            if (exporting)
//                Box(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .background(Color.Black.copy(alpha = 0.3f))
//                )
//            if (exporting)
//                ExportProgress(progress, modifier = Modifier.align(Alignment.Center))
//
//        }
//        Row(
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.SpaceBetween,
//            modifier = Modifier
//                .align(Alignment.BottomCenter)
//                .padding(vertical = 4.dp, horizontal = 16.dp)
//        ) {
//            Spacer(modifier = Modifier.weight(1f))
//            ExportButton(
//                exporting = exporting,
//                onClick = {
//                    vm.exportVideo(
//                        context = context,
//                        cacheDir = context.cacheDir,
//                        outputFile = null
//                    ) { path, error ->
//                        if (path != null) {
//                            exportedPath = path
//                            sheetMode = BottomSheetMode.RESULT
//
//                            // konversi file path → uri via FileProvider
////                            val file = File(path)
////                            val uri = FileProvider.getUriForFile(
////                                context,
////                                "${context.packageName}.fileprovider",
////                                file
////                            )
////                            onExportFinished(uri)
////
//                            exoPlayer.let { it.value?.pause() }
//                        } else error?.printStackTrace()
//                    }
//                }
//            )
//        }
//    }
//
//    if (sheetMode != BottomSheetMode.NONE) {
//        ModalBottomSheet(
//            onDismissRequest = { sheetMode = BottomSheetMode.NONE },
//            sheetState = bottomSheetState,
//            containerColor = Color(0xFF111111)
//        ) {
//            when (sheetMode) {
//                BottomSheetMode.STICKER -> {
//
//                    StickerBottomSheetContent(
//                        onStickerClick = {
//                            scope.launch {
//                                val sticker = vm.addStickerFromUrl(
//                                    context,
//                                    it,
//                                    posXpx = 0f,
//                                    posYpx = 0f,
//                                    scale = 0.65f
//                                )
//                                vm.addOverlay(sticker)
//                                sheetMode = BottomSheetMode.NONE
//                            }
//                        }
//                    )
//                }
//
//                BottomSheetMode.FILTER -> {
//                    AddFilterBottomSheet(
//                        onFilterClick = { item ->
//                            vm.selectCubeEffect(item.cubeUrl)
//                        },
//                        selectedUrl = filterEffectUrl
//                    )
//                }
//
//                BottomSheetMode.AUDIO -> {
//                    AddAudioBottomSheet(
//                        onPlay = {},
//                        onSelect = {
//                            vm.setAudio(it.url, context)
//                            titleAudio.value = "${it.title} - ${it.artist}"
//                            sheetMode = BottomSheetMode.NONE
//                        }
//                    )
//                }
//
//                BottomSheetMode.RESULT -> {
//                    if (exportedPath != null) {
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
//                    }
//                }
//
//                else -> Unit
//            }
//        }
//    }
//    if (showTextEditor) {
//        TextEditorDialog(
//            initialText = "",
//            initialColor = Color.White,
//            initialBackgroundColor = Color.Transparent,
//            fonts = fontManager.fonts,
//            onDismissRequest = {
//                showTextEditor = false
//            },
//            onTextEdited = { inputText, backgroundColor, colorCode, fontId ->
//                vm.addOverlay(
//                    Overlay(
//                        type = Overlay.Type.TEXT,
//                        text = inputText,
//                        color = Color(colorCode),
//                        bgcolor = backgroundColor,
//                        typeface = fontManager.getFont(fontId),
//                        fontSize = 50.sp,
//                        posXpx = 0f,  // misal default pixel X
//                        posYpx = 0f,
//                        scale = 0.5f// misal default pixel Y
//                    )
//                )
//                showTextEditor = false
//            }
//        )
//    }
//
//}