package com.zinmedia.videoeditor.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.zinmedia.videoeditor.core.color.ListWarnaPicker
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Edit
import kotlin.math.ceil


@Composable
fun ColorPickerSection(
    onColorChanged: (Int) -> Unit,
    initialColor: Color = Color(0xFFFFFFFF),
    modifier: Modifier = Modifier
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    val colors = ListWarnaPicker
    val itemsPerPage = 8
    val totalPages = ceil(colors.size / itemsPerPage.toDouble()).toInt()

    val pagerState = rememberPagerState(
        pageCount = { totalPages },
        initialPage = 0
    )

    // Panggil callback ketika selectedColor berubah
    LaunchedEffect(selectedColor) {
        onColorChanged(selectedColor.toArgb())
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.height(40.dp)
        ) { page ->
            val startIndex = page * itemsPerPage
            val endIndex = minOf(startIndex + itemsPerPage, colors.size)
            val pageColors = colors.subList(startIndex, endIndex)

            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Isi dengan 8 warna per halaman
                pageColors.forEach { color ->
                    ColorOption(
                        color = color,
                        isSelected = color == selectedColor,
                        onColorSelected = {
                            selectedColor = color
                        }
                    )
                }

                // Tambahkan placeholder jika kurang dari 8 item
                repeat(itemsPerPage - pageColors.size) {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
        }

        // Page indicators
        if (totalPages > 1) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                repeat(totalPages) { index ->
                    val color = if (index == pagerState.currentPage) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.3f)
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(6.dp)
                            .background(color, CircleShape)

                    )
                }
            }
        }
    }
}


// Extension function untuk mengecek apakah warna terang atau gelap
private fun Color.isLightColor(): Boolean {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return luminance > 0.5f
}


@Composable
private fun ColorOption(
    color: Color,
    isSelected: Boolean,
    onColorSelected: () -> Unit
) {
    val borderColor = if (isSelected) {
        Color.White
    } else {
        Color.White.copy(alpha = 0.3f)
    }

    val borderWidth = if (isSelected) 3.dp else 2.dp

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onColorSelected)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Indikator centang untuk warna yang dipilih
        if (isSelected) {
            Icon(
                imageVector = EvaIcons.Fill.Edit,
                contentDescription = "Selected",
                tint = if (color.isLightColor()) Color.Black else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}