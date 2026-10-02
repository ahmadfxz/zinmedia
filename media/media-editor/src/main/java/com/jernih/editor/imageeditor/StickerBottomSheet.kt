package com.jernih.editor.imageeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap


// StickerBottomSheet.kt
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun StickerBottomSheet(
    onStickerSelected: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val stickerUrls = remember { StickerData.stickerUrls }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
      //  containerColor = Color(0xFF1E1E1E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Text(
                text = "Stickers",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Stickers Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(stickerUrls) { stickerUrl ->
                    StickerItemReliable(
                        stickerUrl = stickerUrl,
                        onStickerSelected = onStickerSelected,
                    )
                }
            }
        }
    }
}


@Composable
fun StickerItemReliable(
    stickerUrl: String,
    onStickerSelected: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .size(100.dp)
            .clickable(
                enabled = !isLoading,
                onClick = {
                    // Load bitmap ketika diklik
                    loadStickerSimple(context, stickerUrl, onStickerSelected)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Tampilkan preview image
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(stickerUrl)
                .size(100, 100)
                .build(),
            contentDescription = "Sticker",
            modifier = Modifier
                .fillMaxSize(),
            contentScale = ContentScale.Fit,
            onLoading = { isLoading = true },
            onSuccess = { isLoading = false },
            onError = { isLoading = false }
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

// StickerData.kt
object StickerData {
    // Image Urls from flaticon(https://www.flaticon.com/stickers-pack/food-289)
    val stickerUrls = listOf(
        "https://cdn-icons-png.flaticon.com/256/4392/4392471.png",
        "https://cdn-icons-png.flaticon.com/256/4392/4392522.png",
        "https://cdn-icons-png.flaticon.com/256/4213/4213612.png",
        "https://cdn-icons-png.flaticon.com/256/4213/4213605.png",
        "https://cdn-icons-png.flaticon.com/256/4213/4213517.png",
        "https://cdn-icons-png.flaticon.com/256/4228/4228685.png",
        "https://cdn-icons-png.flaticon.com/256/4329/4329960.png",
        "https://cdn-icons-png.flaticon.com/256/6702/6702479.png",
        "https://cdn-icons-png.flaticon.com/256/6852/6852961.png",
        "https://cdn-icons-png.flaticon.com/256/6852/6852993.png",
        "https://cdn-icons-png.flaticon.com/256/8137/8137252.png",
        "https://cdn-icons-png.flaticon.com/256/8137/8137255.png",
        "https://cdn-icons-png.flaticon.com/256/8137/8137228.png",
        "https://cdn-icons-png.flaticon.com/256/8137/8137225.png",
        "https://cdn-icons-png.flaticon.com/256/8137/8137202.png",
        "https://cdn-icons-png.flaticon.com/256/4392/4392452.png",
        "https://cdn-icons-png.flaticon.com/256/4392/4392455.png",
        "https://cdn-icons-png.flaticon.com/256/4392/4392459.png",
        "https://cdn-icons-png.flaticon.com/256/4392/4392462.png",
        "https://cdn-icons-png.flaticon.com/256/4392/4392465.png",
        "https://cdn-icons-png.flaticon.com/256/4392/4392467.png",
        "https://cdn-icons-png.flaticon.com/256/4392/4392469.png",
    )
}

data class StickerItem(
    val url: String,
    val id: Int
)


// Helper function untuk load bitmap dengan ukuran yang tepat
private fun loadStickerSimple(
    context: Context,
    stickerUrl: String,
    onStickerLoaded: (Bitmap) -> Unit
) {
    val imageLoader = ImageLoader.Builder(context)
        .build()

    val request = ImageRequest.Builder(context)
        .data(stickerUrl)
        .size(400, 400)
        .allowHardware(false) // ⬅️ Ini yang paling penting
        .target { drawable ->
            try {
//                val bitmap = (drawable as? BitmapDrawable)?.bitmap
//                bitmap?.let { onStickerLoaded(it) }
                val bitmap = drawable.toBitmap()
                bitmap?.let {
                    onStickerLoaded(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        .build()

    imageLoader.enqueue(request)
}