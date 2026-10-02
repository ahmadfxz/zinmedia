package com.zinmedia.photoeditor.imageeditor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zinmedia.photoeditor.domain.model.ToolItem
import com.zinmedia.photoeditor.imageeditor.filters.FilterListener
import com.zinmedia.photoeditor.imageeditor.filters.FiltersSection
import com.zinmedia.photoeditor.imageeditor.tools.ToolType
import com.zinmedia.photoeditor.imageeditor.widget.button.JernihTextButton
import com.zinmedia.photoeditor.presentation.components.LoadingIndicator
import com.zinmedia.photoeditor.presentation.components.SlideFadeVisibility
import com.zinmedia.photoeditor.R
import com.zinmedia.photoeditor.ui.BottomChatDetail
import com.zinmedia.photoeditor.engine.PhotoEditorView


// EditImageScreen.kt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditImageScreen(
    currentTool: String,
    isFilterVisible: Boolean,
    isLoading: Boolean,
    snackbarMessage: String?,
    onBackPressed: () -> Unit,
    onShareImage: (String, Int) -> Unit,
    onToolSelected: (ToolType) -> Unit,
    onCloseFilter: () -> Unit,
    photoEditorView: PhotoEditorView,
    filterListener: FilterListener,
    onSnackbarShown: () -> Unit
) {
    val context = LocalContext.current
    var showDurationStatus by remember { mutableStateOf(false) }
    var durationStatus by remember { mutableIntStateOf(24) }

    // Handle snackbar messages
    snackbarMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onSnackbarShown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PhotoEditorContainer(photoEditorView = photoEditorView)
            when (currentTool) {
                "Text", "Draw", "Eraser" -> {}
                else -> {
                    ToolsGridSection(
                        onToolSelected = onToolSelected,
                        onDissmiss = onBackPressed,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                        // .padding(bottom = 20.dp)
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(vertical = 4.dp)
                    ) {
                        BottomChatDetail(
                            enableButton = !isLoading,
                            inputTitle = "Tambahkan keterangan",
                            onSend = { message ->
                                onShareImage(message, 24)

                                //     konversi file path → uri via FileProvider
//                            val file = File(path)
//                            val uri = FileProvider.getUriForFile(
//                                context,
//                                "${context.packageName}.fileprovider",
//                                file
//                            )
//                            onExportFinished(uri, message)
                            },
                            modifier = Modifier.imePadding()
                        )
//                        Box(
//                            modifier = Modifier.fillMaxWidth(),
//                            contentAlignment = Alignment.CenterStart
//                        ) {
//
//                            // Panel Duration
//
//                            SlideFadeVisibility(
//                                visible = showDurationStatus,
//                                initialOffsetX = { it },
//                                targetOffsetX = { it }
//                            ) {
//                                DurationBottomRow(
//                                    inisialValue = durationStatus,
//                                    onSelect = {
//                                        durationStatus = it
//                                        showDurationStatus = false
//                                    }
//                                )
//                            }
//
//                            // Row kecil
//                            SlideFadeVisibility(
//                                visible = !showDurationStatus,
//                                initialOffsetX = { -40 },
//                                targetOffsetX = { -40 }
//                            ) {
//                                Row(
//                                    verticalAlignment = Alignment.CenterVertically,
//                                    horizontalArrangement = Arrangement.SpaceBetween,
//                                    modifier = Modifier
//                                        .padding(vertical = 6.dp)
//                                        .clickable { showDurationStatus = true }
//                                ) {
//                                    Icon(
//                                        painter = painterResource(R.drawable.ic_clock),
//                                        contentDescription = "Clock",
//                                        tint = Color.Green.copy(alpha = 0.5f),
//                                        modifier = Modifier.size(25.dp)
//                                    )
//                                    Spacer(Modifier.width(6.dp))
//
//                                    Text(
//                                        "Status $durationStatus jam",
//                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
//                                    )
//                                }
//                            }
//                        }
                    }

//                    InstagramHeaderCompact(
//                        onBackPressed = onBackPressed,
//                        onShareImage = onShareImage,
//                    )
                }
            }
            if (isFilterVisible)
                FiltersSection(
                    filterListener = filterListener,
                    onCloseClicked = onCloseFilter,
                    modifier = Modifier
                        .padding(bottom = 100.dp)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(140.dp)
                )
        }

        LoadingIndicator(isLoading = isLoading)
    }

}

// Versi alternatif dengan layout yang lebih compact
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramHeaderCompact(
    onBackPressed: () -> Unit,
    onShareImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button
        IconButton(
            onClick = onBackPressed,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            )
        ) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(25.dp)
            )
        }

        // Action Buttons
        JernihTextButton(
            title = "Bagikan",
            onClick = onShareImage,
            containerColor = Color(0xFF212121),
            contentColor = Color(0xFFFFFFFF)
        )
    }
}

@Composable
fun ToolsGridSection(
    onToolSelected: (ToolType) -> Unit,
    onDissmiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        ToolItem(ToolType.TEXT, R.drawable.ic_text, "Text", Color(0xFFFFFFFF)),
        ToolItem(ToolType.SHAPE, R.drawable.ic_brush, "Draw", Color(0xFFFFFFFF)),
        ToolItem(ToolType.FILTER, R.drawable.ic_filter, "Filter", Color(0xFFFFFFFF)),
        ToolItem(ToolType.STICKER, R.drawable.ic_sticker, "Sticker", Color(0xFFFFFFFF)),
        ToolItem(ToolType.EMOJI, R.drawable.ic_emot, "Emoji", Color(0xFFFFFFFF)),
        // ToolItem(ToolType.ERASER, R.drawable.ic_eraser, "Eraser", Color(0xFFFFFFFF))
    )

    Row(
        modifier = modifier
            .height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(35.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .clickable {
                    onDissmiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(id = R.drawable.ic_close),
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        tools.forEach { tool ->
            InstagramToolItem(tool = tool, onToolSelected = onToolSelected)
        }
    }
}

@Composable
fun InstagramToolItem(
    tool: ToolItem,
    onToolSelected: (ToolType) -> Unit
) {
    Box(
        modifier = Modifier
            .size(35.dp)
            .background(
                color = Color.Black.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .clickable { onToolSelected(tool.type) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(id = tool.iconRes),
            contentDescription = tool.label,
            tint = tool.color,
            modifier = Modifier.size(24.dp)
        )
    }
}


@Composable
fun DurationBottomRow(
    modifier: Modifier = Modifier,
    inisialValue: Int,
    onSelect: (Int) -> Unit = {},
) {
    val categories = listOf(24, 6, 12, 42)

    LazyRow(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),

        ) {
        items(categories) { cat ->

            val isSelected = inisialValue == cat

            val borderColor =
                if (isSelected) Color.Green.copy(alpha = 0.6f)
                else Color.Transparent

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                    // .background(backgroundColor)
                    .clickable { onSelect(cat) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {

                    Icon(
                        painter = painterResource(R.drawable.ic_clock),
                        contentDescription = "Clock",
                        tint = if (isSelected)
                            Color.Green.copy(alpha = 0.6f)
                        else
                            Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.size(25.dp)
                    )
                    Text(
                        text = "$cat jam",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}


