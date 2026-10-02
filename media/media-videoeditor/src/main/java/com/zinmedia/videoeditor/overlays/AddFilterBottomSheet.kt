package com.zinmedia.videoeditor.overlays

import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Keep
data class FilterEffectItem(
    val name: String,
    val cubeUrl: String,
    val thumbnailUrl: String
)

private val filtersEffectList = listOf(
    FilterEffectItem("Vintage",
        "https://storage.googleapis.com/jualxbeli/cube/a.cube",
        "https://storage.googleapis.com/jualxbeli/cube/a.jpg"
    ),
    FilterEffectItem("Hitam Putih",
        "https://storage.googleapis.com/jualxbeli/cube/b.cube",
        "https://storage.googleapis.com/jualxbeli/cube/b.jpg"
    ),
    FilterEffectItem("Punch",
        "https://storage.googleapis.com/jualxbeli/cube/c.cube",
        "https://storage.googleapis.com/jualxbeli/cube/c.jpg"
    )
)

/** Strip filter horizontal, tampilannya sama dengan filter editor foto. "Asli" = tanpa filter. */
@Composable
fun AddFilterBottomSheet(
    selectedUrl: String,
    onFilterClick: (FilterEffectItem) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(listOf(OriginalFilter) + filtersEffectList) { filter ->
            val selected = filter.cubeUrl == selectedUrl
            val shape = RoundedCornerShape(10.dp)
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .clickable { onFilterClick(filter) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 80.dp)
                        .clip(shape)
                        .background(Color(0xFF1F2C34))
                        .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier),
                ) {
                    if (filter.thumbnailUrl.isNotEmpty()) {
                        AsyncImage(
                            model = filter.thumbnailUrl,
                            contentDescription = filter.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = filter.name,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

private val OriginalFilter = FilterEffectItem(name = "Asli", cubeUrl = "", thumbnailUrl = "")
