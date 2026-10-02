package com.zinmedia.photoeditor.imageeditor

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zinmedia.photoeditor.R
import com.zinmedia.photoeditor.engine.PhotoEditorView
import com.zinmedia.photoeditor.engine.PhotoFilter
import com.zinmedia.photoeditor.imageeditor.filters.FilterListener
import com.zinmedia.photoeditor.imageeditor.filters.FiltersSection
import com.zinmedia.photoeditor.presentation.components.LoadingIndicator
import com.zinmedia.photoeditor.ui.EditorCaptionBar
import com.zinmedia.photoeditor.ui.EditorColors
import com.zinmedia.photoeditor.ui.EditorIconButton
import com.zinmedia.photoeditor.ui.EditorScrim
import com.zinmedia.photoeditor.ui.EditorTopBar
import com.zinmedia.photoeditor.ui.FilterHint

/**
 * Layar utama editor foto:
 * toolbar di atas, lalu filter, kolom keterangan, penerima, dan tombol kirim di bawah.
 */
@Composable
fun EditImageScreen(
    photoEditorView: PhotoEditorView,
    showChrome: Boolean,
    isFilterVisible: Boolean,
    selectedFilter: PhotoFilter,
    isLoading: Boolean,
    recipientLabel: String,
    caption: String,
    onCaptionChange: (String) -> Unit,
    snackbarMessage: String?,
    onClose: () -> Unit,
    onOpenCrop: () -> Unit,
    onOpenStickers: () -> Unit,
    onOpenText: () -> Unit,
    onOpenDraw: () -> Unit,
    onToggleFilter: () -> Unit,
    filterListener: FilterListener,
    onSend: (caption: String) -> Unit,
    onSnackbarShown: () -> Unit,
) {
    val context = LocalContext.current

    snackbarMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onSnackbarShown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorColors.Background)
    ) {
        PhotoEditorContainer(
            photoEditorView = photoEditorView,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        )

        AnimatedVisibility(
            visible = showChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                EditorScrim(top = true, modifier = Modifier.align(Alignment.TopCenter))
                EditorScrim(top = false, modifier = Modifier.align(Alignment.BottomCenter))

                EditorTopBar(onClose = onClose, modifier = Modifier.align(Alignment.TopCenter)) {
                    EditorIconButton(R.drawable.zm_ic_crop, "Potong & putar", onOpenCrop)
                    EditorIconButton(R.drawable.zm_ic_sticker, "Stiker", onOpenStickers)
                    EditorIconButton(R.drawable.zm_ic_text, "Teks", onOpenText)
                    EditorIconButton(R.drawable.zm_ic_pen, "Gambar", onOpenDraw)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FilterHint(expanded = isFilterVisible, onClick = onToggleFilter)
                    AnimatedVisibility(
                        visible = isFilterVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        FiltersSection(
                            selectedFilter = selectedFilter,
                            filterListener = filterListener,
                            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                        )
                    }
                    EditorCaptionBar(
                        caption = caption,
                        onCaptionChange = onCaptionChange,
                        recipientLabel = recipientLabel,
                        sendEnabled = !isLoading,
                        onSend = { onSend(caption) },
                    )
                }
            }
        }

        LoadingIndicator(isLoading = isLoading)
    }
}
