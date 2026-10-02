package com.zinmedia.photoeditor

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import com.zinmedia.photoeditor.data.manager.FontManager
import com.zinmedia.photoeditor.imageeditor.DrawWidget
import com.zinmedia.photoeditor.imageeditor.EditImageScreen
import com.zinmedia.photoeditor.imageeditor.StickerTraySheet
import com.zinmedia.photoeditor.imageeditor.crop.CropScreen
import com.zinmedia.photoeditor.imageeditor.crop.CropState
import com.zinmedia.photoeditor.ui.DiscardChangesDialog
import com.zinmedia.photoeditor.imageeditor.TextEditorDialog
import com.zinmedia.photoeditor.imageeditor.filters.FilterListener
import com.zinmedia.photoeditor.imageeditor.tools.ToolType
import com.zinmedia.photoeditor.ui.theme.MarketplaceTheme
import com.zinmedia.photoeditor.R
import com.zinmedia.photoeditor.engine.OnPhotoEditorListener
import com.zinmedia.photoeditor.engine.PhotoEditor
import com.zinmedia.photoeditor.engine.PhotoEditorView
import com.zinmedia.photoeditor.engine.PhotoFilter
import com.zinmedia.photoeditor.engine.SaveSettings
import com.zinmedia.photoeditor.engine.TextStyleBuilder
import com.zinmedia.photoeditor.engine.ViewType
import com.zinmedia.photoeditor.engine.shape.ShapeBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream


class ImageEditorActivity : ComponentActivity(), OnPhotoEditorListener, FilterListener {

    private lateinit var mPhotoEditor: PhotoEditor
    private lateinit var mPhotoEditorView: PhotoEditorView
    private lateinit var mShapeBuilder: ShapeBuilder

    private lateinit var fontManager: FontManager

    // State variables
    private var currentTool by mutableStateOf("Edit")
    private var isUndoEnabled by mutableStateOf(false)
    private var isRedoEnabled by mutableStateOf(false)
    private var isFilterVisible by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)
    private var snackbarMessage by mutableStateOf<String?>(null)
    private var showDrawShape by mutableStateOf(false)
    private var showTextEditor by mutableStateOf(false)
    private var textEditorInitialText by mutableStateOf("")
    private var textEditorInitialColor by mutableStateOf(Color.White)
    private var textEditorBgInitialColor by mutableStateOf(Color.Transparent)
    private var editingTextView: View? = null
    private var showStickerBottomSheet by mutableStateOf(false)
    private var selectedFilter by mutableStateOf(PhotoFilter.NONE)
    private var originalBitmap by mutableStateOf<Bitmap?>(null)
    private var cropState by mutableStateOf(CropState())
    private var showCrop by mutableStateOf(false)
    private var caption by mutableStateOf("")
    private var showDiscardDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fontManager = FontManager(this)

        initializePhotoEditor()
        setupBackPressHandler()
        // Editor selalu bertema gelap: ikon status & navigation bar terang di atas latar hitam.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        setContent {
            MarketplaceTheme {
                EditImageScreen(
                    photoEditorView = mPhotoEditorView,
                    showChrome = !showDrawShape && !showTextEditor && !showCrop,
                    isFilterVisible = isFilterVisible,
                    selectedFilter = selectedFilter,
                    isLoading = isLoading,
                    recipientLabel = intent.getStringExtra(EXTRA_RECIPIENT_LABEL) ?: DEFAULT_RECIPIENT_LABEL,
                    caption = caption,
                    onCaptionChange = { caption = it },
                    snackbarMessage = snackbarMessage,
                    onClose = { onBackPressedDispatcher.onBackPressed() },
                    onOpenCrop = { if (originalBitmap != null) showCrop = true },
                    onOpenStickers = { onToolSelected(ToolType.STICKER) },
                    onOpenText = { onToolSelected(ToolType.TEXT) },
                    onOpenDraw = { onToolSelected(ToolType.SHAPE) },
                    onToggleFilter = { onToolSelected(ToolType.FILTER) },
                    filterListener = this,
                    onSend = { caption -> publishImage(caption, 24) },
                    onSnackbarShown = { snackbarMessage = null },
                )

                if (showDrawShape) {
                    DrawWidget(
                        enableUndo = isUndoEnabled,
                        onUndo = {
                            isUndoEnabled = mPhotoEditor.undo()
                            isRedoEnabled = mPhotoEditor.isRedoAvailable
                        },
                        isEnable = showDrawShape,
                        onColorChanged = { colorCode ->
                            mPhotoEditor.setShape(mShapeBuilder.withShapeColor(colorCode))
                        },
                        onOpacityChanged = { opacity ->
                            mPhotoEditor.setShape(mShapeBuilder.withShapeOpacity(opacity))
                        },
                        onShapeSizeChanged = { shapeSize ->
                            mPhotoEditor.setShape(mShapeBuilder.withShapeSize(shapeSize))
                        },
                        onShapePicked = { shapeType ->
                            if (currentTool != "Draw") {
                                currentTool = "Draw"
                                mPhotoEditor.setBrushDrawingMode(true)
                            }
                            mPhotoEditor.setShape(mShapeBuilder.withShapeType(shapeType))
                        },
                        onEraser = {
                            if (currentTool != "Eraser") {
                                mPhotoEditor.brushEraser()
                                currentTool = "Eraser"
                            } else {
                                mPhotoEditor.setBrushDrawingMode(true)
                                currentTool = "Draw"
                            }

                        },
                        isEnableEraser = currentTool == "Eraser",
                        onDone = {
                            showDrawShape = false // Sembunyikan draw widget
                            mPhotoEditor.setBrushDrawingMode(false) // ⬅️ INI YANG PENTING! Disable drawing mode
                            currentTool = "Edit" // Kembali ke mode normal
                        }
                    )
                }
                if (showTextEditor) {
                    TextEditorDialog(
                        initialText = textEditorInitialText,
                        initialColor = textEditorInitialColor,
                        initialBackgroundColor = textEditorBgInitialColor,
                        fonts = fontManager.fonts,
                        onDismissRequest = {
                            showTextEditor = false
                            editingTextView = null
                            currentTool = "Edit"
                        },
                        onTextEdited = { inputText, backgroundColor, colorCode, fontId ->
                            if (editingTextView != null) {
                                // Editing existing text
                                val styleBuilder = TextStyleBuilder()
                                styleBuilder.withTextColor(colorCode)
                                styleBuilder.withBackgroundColor(backgroundColor.toArgb())

                                val typeface = fontManager.getFont(fontId)
                                styleBuilder.withTextFont(typeface)

                                mPhotoEditor.editText(editingTextView!!, inputText, styleBuilder)
                            } else {
                                // Adding new text
                                val styleBuilder = TextStyleBuilder()
                                styleBuilder.withTextColor(colorCode)
                                styleBuilder.withBackgroundColor(backgroundColor.toArgb())

                                val typeface = fontManager.getFont(fontId)
                                styleBuilder.withTextFont(typeface)
                                styleBuilder.withTextSize(28f)
                                mPhotoEditor.addText(inputText, styleBuilder)
                            }
                            currentTool = "Text"
                            showTextEditor = false
                            editingTextView = null
                        }
                    )
                }

                if (showDiscardDialog) {
                    DiscardChangesDialog(
                        onDiscard = { finish() },
                        onDismiss = { showDiscardDialog = false },
                    )
                }

                val cropSource = originalBitmap
                if (showCrop && cropSource != null) {
                    CropScreen(
                        original = cropSource,
                        initial = cropState,
                        onCancel = { showCrop = false },
                        onDone = { state, cropped ->
                            cropState = state
                            mPhotoEditorView.source.setImageBitmap(cropped)
                            // Engine mereset filter saat gambar diganti; pasang lagi filter aktif.
                            mPhotoEditor.setFilterEffect(selectedFilter)
                            showCrop = false
                        },
                    )
                }

                if (showStickerBottomSheet) {
                    StickerTraySheet(
                        onEmojiSelected = { emoji ->
                            mPhotoEditor.addEmoji(emoji)
                            currentTool = "Emoji"
                            showStickerBottomSheet = false
                        },
                        onStickerSelected = { bitmap ->
                            mPhotoEditor.addImage(bitmap)
                            currentTool = "Sticker"
                            showStickerBottomSheet = false
                        },
                        onDismiss = { showStickerBottomSheet = false },
                    )
                }
            }
        }
    }


    private fun initializePhotoEditor() {
        mPhotoEditorView = PhotoEditorView(this).apply {
            id = R.id.photoEditorView // PERBAIKAN
            layoutParams = FrameLayout.LayoutParams( // PERBAIKAN
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // initializeFonts()

        val pinchTextScalable =
            intent.getBooleanExtra(PINCH_TEXT_SCALABLE_INTENT_KEY, true) // PERBAIKAN

        mPhotoEditor = PhotoEditor.Builder(this, mPhotoEditorView)
            .setPinchTextScalable(pinchTextScalable)
            .build()

        mPhotoEditor.setOnPhotoEditorListener(this)
        mPhotoEditor.setFilterEffect(PhotoFilter.NONE)
        setImageSource()
    }

    private fun setImageSource() {
        val imageUri = intent.data
        if (imageUri == null) {
            Log.e(TAG, "ImageEditorActivity dibuka tanpa URI gambar")
            finish()
            return
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    SampledBitmapLoader.load(this@ImageEditorActivity, imageUri)
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal memuat gambar: $imageUri", e)
                    null
                }
            }
            if (bitmap == null) {
                finish()
                return@launch
            }
            originalBitmap = bitmap
            mPhotoEditorView.source.setImageBitmap(bitmap)
        }
    }

    // Rest of your interface implementations...
    override fun onEditTextChangeListener(
        rootView: View,
        text: String,
        colorCode: Int,
        bgColorCode: Int
    ) {
        if (currentTool != "Draw") {
            textEditorInitialText = text
            textEditorInitialColor = Color(colorCode)
            textEditorBgInitialColor = Color(bgColorCode)
            showTextEditor = true
            editingTextView = rootView
            currentTool = "Text"
        }
    }

    override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {
        updateUndoRedoState()
    }

    override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) {
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        isUndoEnabled = mPhotoEditor.isUndoAvailable
        isRedoEnabled = mPhotoEditor.isRedoAvailable
    }

    override fun onStartViewChangeListener(viewType: ViewType) {}
    override fun onStopViewChangeListener(viewType: ViewType) {}
    override fun onTouchSourceImage(event: MotionEvent) {}

    override fun onFilterSelected(photoFilter: PhotoFilter) {
        selectedFilter = photoFilter
        mPhotoEditor.setFilterEffect(photoFilter)
    }

    private fun onToolSelected(toolType: ToolType) {
        when (toolType) {
            ToolType.SHAPE -> {
                isFilterVisible = false
                showTextEditor = false
                showStickerBottomSheet = false
                mPhotoEditor.setBrushDrawingMode(true)
                mShapeBuilder = ShapeBuilder()
                mPhotoEditor.setShape(mShapeBuilder)
                currentTool = "Draw"
                showDrawShape = true
            }

            ToolType.TEXT -> {
                textEditorInitialText = ""
                textEditorInitialColor = Color.White
                showTextEditor = true
                editingTextView = null
                currentTool = "Text"
            }

            ToolType.ERASER -> {
                // mPhotoEditor.brushEraser()
                // currentTool = "Eraser"
            }

            ToolType.FILTER -> {
                currentTool = "Filter"
                isFilterVisible = !isFilterVisible
            }

            ToolType.EMOJI, ToolType.STICKER -> {
                currentTool = "Sticker"
                showStickerBottomSheet = true
            }
        }
    }

    private fun sharePreview() {
        isLoading = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val saveSettings = SaveSettings.Builder()
                    .setClearViewsEnabled(true)
                    .setTransparencyEnabled(true)
                    .build()

                val bitmap = withContext(Dispatchers.Main) {
                    mPhotoEditor.saveAsBitmap(saveSettings)
                }

                val tempFile = File.createTempFile(
                    "share_image_editor",
                    ".png",
                    PhotoEditorFileProvider.outputDir(this@ImageEditorActivity)
                )

                FileOutputStream(tempFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                withContext(Dispatchers.Main) {
                    try {
                        val shareUri = PhotoEditorFileProvider.uriFor(
                            this@ImageEditorActivity,
                            tempFile
                        )

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*" // PERBAIKAN
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        startActivity(Intent.createChooser(intent, "Share Image")) // PERBAIKAN
                        snackbarMessage = "Sharing image..."

                    } catch (e: Exception) {
                        snackbarMessage = "Failed to share: ${e.message}"
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarMessage = "Share failed: ${e.message}"
                    e.printStackTrace()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) { // PERBAIKAN
                override fun handleOnBackPressed() {
                    handleSystemBack()
                }
            })
    }

    private fun handleSystemBack() {
        when {
            currentTool == "Draw" -> {
                currentTool = "Edit"
                showDrawShape = false
                mPhotoEditor.setBrushDrawingMode(false)
            }

            currentTool == "Eraser" -> {
                currentTool = "Edit"
                showDrawShape = false
                mPhotoEditor.setBrushDrawingMode(false)
            }

            isFilterVisible -> {
                isFilterVisible = false
                currentTool = "Edit"
            }

            hasChanges() -> showDiscardDialog = true
            else -> finish()
        }
    }

    /** Ada hasil edit (teks/stiker/gambar, crop, filter) atau keterangan yang belum dikirim. */
    private fun hasChanges(): Boolean =
        !mPhotoEditor.isCacheEmpty ||
            !cropState.isIdentity ||
            selectedFilter != PhotoFilter.NONE ||
            caption.isNotBlank()

//    private fun publishImage() {
//        isLoading = true
//
//        lifecycleScope.launch(Dispatchers.IO) {
//            try {
//                val saveSettings = SaveSettings.Builder()
//                    .setClearViewsEnabled(true)
//                    .setTransparencyEnabled(true)
//                    .build()
//
//                val bitmap = withContext(Dispatchers.Main) {
//                    mPhotoEditor.saveAsBitmap(saveSettings)
//                }
//
//                val file = File(cacheDir, "edited_image_editor.png")
//                file.outputStream().use { output ->
//                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
//                }
//
//                val uri = FileProvider.getUriForFile(
//                    this@ImageEditorActivity,
//                    "${packageName}.fileprovider",
//                    file
//                )
//
//                val resultIntent = Intent().apply {
//                    data = uri
//                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                }
//
//                withContext(Dispatchers.Main) {
//                    setResult(Activity.RESULT_OK, resultIntent)
//                    finish()
//                }
//
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    snackbarMessage = "Gagal menyimpan: ${e.message}"
//                }
//            } finally {
//                isLoading = false
//            }
//        }
//    }

    private fun publishImage(
        keterangan: String,
        durasi: Int
    ) {

        isLoading = true

        lifecycleScope.launch(Dispatchers.IO) {

            try {

                val saveSettings = SaveSettings.Builder()
                    .setClearViewsEnabled(true)
                    .setTransparencyEnabled(true)
                    .build()

                val bitmap = withContext(Dispatchers.Main) {
                    mPhotoEditor.saveAsBitmap(saveSettings)
                }

                val file = File(PhotoEditorFileProvider.outputDir(this@ImageEditorActivity), "edited_image.png")

                file.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }

                val uri = PhotoEditorFileProvider.uriFor(this@ImageEditorActivity, file)

                val resultIntent = Intent().apply {

                    data = uri

                    putExtra("keterangan", keterangan)
                    putExtra("durasi", durasi)
                    putExtra("media_type", "image")

                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {

                    setResult(Activity.RESULT_OK, resultIntent)

                    finish()
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    snackbarMessage = e.message
                }
            }
        }
    }


    companion object {
        const val PINCH_TEXT_SCALABLE_INTENT_KEY = "PINCH_TEXT_SCALABLE"

        /** Label penerima di kiri tombol kirim, mis. "Status (Kontak)". Default: "Status". */
        const val EXTRA_RECIPIENT_LABEL = "com.zinmedia.extra.RECIPIENT_LABEL"
        private const val DEFAULT_RECIPIENT_LABEL = "Status"
        private const val TAG = "ImageEditorActivity"
    }
}