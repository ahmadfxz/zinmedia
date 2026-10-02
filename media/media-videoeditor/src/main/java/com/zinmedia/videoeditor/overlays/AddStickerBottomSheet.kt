package com.zinmedia.videoeditor.overlays

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StickerBottomSheetContent(
    onStickerClick: (String) -> Unit,
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            "Stickers",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier.padding(16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(12.dp)
        ) {
            items(stickerUrls) { url ->
                Box(
                    modifier = Modifier
                        .size(75.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                        .clickable { onStickerClick(url) },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }

}


private val stickerUrls = listOf(
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