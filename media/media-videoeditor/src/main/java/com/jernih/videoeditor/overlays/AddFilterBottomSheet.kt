package com.jernih.videoeditor.overlays

import androidx.annotation.Keep
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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

@Keep
data class FilterEffectItem(
    val name: String,
    val cubeUrl: String,
    val thumbnailUrl: String
)

private val filtersEffectList = listOf(
    FilterEffectItem("A",
        "https://storage.googleapis.com/jualxbeli/cube/a.cube",
        "https://storage.googleapis.com/jualxbeli/cube/a.jpg"
    ),
    FilterEffectItem("B",
        "https://storage.googleapis.com/jualxbeli/cube/b.cube",
        "https://storage.googleapis.com/jualxbeli/cube/b.jpg"
    ),
    FilterEffectItem("C",
        "https://storage.googleapis.com/jualxbeli/cube/c.cube",
        "https://storage.googleapis.com/jualxbeli/cube/c.jpg"
    )
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddFilterBottomSheet(
    selectedUrl: String,
    onFilterClick: (FilterEffectItem) -> Unit
) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Filter",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(12.dp)
            ) {

                items(filtersEffectList) { filter ->

                    val isSelected = filter.cubeUrl == selectedUrl

                    Box(
                        modifier = Modifier
                            .size(85.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onFilterClick(filter) }
                    ) {
                        AsyncImage(
                            model = filter.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Overlay jika selected
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f))
                            )
                        }

                        // Label
                        Text(
                            filter.name,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(0.4f))
                                .padding(vertical = 2.dp, horizontal = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }
}
