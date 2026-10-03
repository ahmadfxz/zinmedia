package com.zinmedia.photoeditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.zinmedia.photoeditor.imageeditor.TrashTarget
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.zinmedia.photoeditor.textlayer.renderTextLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.asAndroidBitmap
import com.zinmedia.photoeditor.imageeditor.DrawWidget
import com.zinmedia.photoeditor.imageeditor.EditImageScreen
import com.zinmedia.photoeditor.imageeditor.StickerTraySheet
import com.zinmedia.photoeditor.imageeditor.TextEditorDialog
import com.zinmedia.photoeditor.imageeditor.crop.CropScreen
import com.zinmedia.photoeditor.imageeditor.tools.ToolType

/**
 * Seluruh UI editor untuk satu foto: kanvas, toolbar, filter, mode teks/gambar/crop, dan tray stiker.
 *
 * @param onClose dipanggil saat tombol tutup ditekan.
 * @param bottomContent isi area paling bawah (mis. kolom keterangan & tombol kirim).
 */
@Composable
public fun PhotoEditorContent(
    state: PhotoEditorState,
    onClose: () -> Unit,
    bottomContent: @Composable () -> Unit,
) {
    LaunchedEffect(state) { state.load() }

    // Satu wadah agar mode gambar/crop selalu ditumpuk di atas kanvas, termasuk di dalam pager.
    Box(Modifier.fillMaxSize()) {
        EditImageScreen(
            photoEditorView = state.editorView,
            showChrome = !state.isToolActive,
            isFilterVisible = state.isFilterVisible,
            selectedFilter = state.selectedFilter,
            isLoading = state.isLoading,
            snackbarMessage = state.snackbarMessage,
            onClose = onClose,
            onOpenCrop = { if (state.originalBitmap != null) state.showCrop = true },
            onOpenStickers = { state.onToolSelected(ToolType.STICKER) },
            onOpenText = { state.onToolSelected(ToolType.TEXT) },
            onOpenDraw = { state.onToolSelected(ToolType.SHAPE) },
            onToggleFilter = { state.onToolSelected(ToolType.FILTER) },
            filterListener = state.filterListener,
            onSnackbarShown = { state.snackbarMessage = null },
            bottomContent = bottomContent,
        )

        val editor = state.editor
        if (state.showDrawShape) {
            DrawWidget(
                enableUndo = state.isUndoEnabled,
                onUndo = { state.isUndoEnabled = editor.undo() },
                isEnable = true,
                onColorChanged = { editor.setShape(state.shapeBuilder.withShapeColor(it)) },
                onOpacityChanged = { editor.setShape(state.shapeBuilder.withShapeOpacity(it)) },
                onShapeSizeChanged = { editor.setShape(state.shapeBuilder.withShapeSize(it)) },
                onShapePicked = { shapeType ->
                    if (state.currentTool != EditorTool.Draw) {
                        state.currentTool = EditorTool.Draw
                        editor.setBrushDrawingMode(true)
                    }
                    editor.setShape(state.shapeBuilder.withShapeType(shapeType))
                },
                onEraser = {
                    if (state.currentTool != EditorTool.Eraser) {
                        editor.brushEraser()
                        state.currentTool = EditorTool.Eraser
                    } else {
                        editor.setBrushDrawingMode(true)
                        state.currentTool = EditorTool.Draw
                    }
                },
                isEnableEraser = state.currentTool == EditorTool.Eraser,
                onDone = state::finishDrawing,
            )
        }

        if (state.showTextEditor) {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            TextEditorDialog(
                initial = state.textEditing,
                fonts = state.fontManager.fonts,
                onDismissRequest = {
                    state.showTextEditor = false
                    state.editingTextView?.visibility = android.view.View.VISIBLE
                    state.editingTextView = null
                    state.currentTool = EditorTool.Edit
                },
                onDone = { layer, layoutWidthPx ->
                    // Gambar yang sama persis dengan tampilan di mode teks (mesin & gaya teks yang sama).
                    val typeface = state.fontManager.getFont(layer.fontIndex)
                    val image = renderTextLayer(
                        layer = layer,
                        fontFamily = FontFamily(typeface),
                        typeface = typeface,
                        measurer = textMeasurer,
                        density = density,
                        layoutWidthPx = layoutWidthPx,
                    ).asAndroidBitmap()
                    val editing = state.editingTextView
                    if (editing != null) editor.editTextLayer(editing, image, layer) else editor.addTextLayer(image, layer)
                    state.currentTool = EditorTool.Text
                },
            )
        }

        val cropSource = state.originalBitmap
        if (state.showCrop && cropSource != null) {
            CropScreen(
                original = cropSource,
                initial = state.cropState,
                onCancel = { state.showCrop = false },
                onDone = { cropState, cropped ->
                    state.applyCrop(cropState, cropped)
                    state.showCrop = false
                },
            )
        }

        if (state.draggingLayer) {
            val hitSlop = with(LocalDensity.current) { 24.dp.toPx() }
            TrashTarget(
                active = state.overTrash,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
                    .onGloballyPositioned { state.trashBounds = it.boundsInWindow().inflate(hitSlop) },
            )
        }

        if (state.showStickerSheet) {
            StickerTraySheet(
                onEmojiSelected = { emoji ->
                    editor.addEmoji(emoji)
                    state.currentTool = EditorTool.Emoji
                    state.showStickerSheet = false
                },
                onStickerSelected = { bitmap ->
                    editor.addImage(bitmap)
                    state.currentTool = EditorTool.Sticker
                    state.showStickerSheet = false
                },
                onDismiss = { state.showStickerSheet = false },
            )
        }
    }
}
