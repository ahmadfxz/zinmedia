package com.zinmedia.photoeditor.imageeditor.filters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zinmedia.photoeditor.engine.PhotoFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class FilterItem(
    val filter: PhotoFilter,
    val iconPath: String,
    val name: String
)

object FilterData {
    val filters = listOf(
        FilterItem(PhotoFilter.NONE, "filters/original.webp", "Asli"),
        FilterItem(PhotoFilter.AUTO_FIX, "filters/auto_fix.webp", "Auto"),
        FilterItem(PhotoFilter.BRIGHTNESS, "filters/brightness.webp", "Cerah"),
        FilterItem(PhotoFilter.CONTRAST, "filters/contrast.webp", "Kontras"),
        FilterItem(PhotoFilter.DOCUMENTARY, "filters/documentary.webp", "Dokumenter"),
        FilterItem(PhotoFilter.DUE_TONE, "filters/dual_tone.webp", "Dual Tone"),
        FilterItem(PhotoFilter.FILL_LIGHT, "filters/fill_light.webp", "Fill Light"),
        FilterItem(PhotoFilter.FISH_EYE, "filters/fish_eye.webp", "Fish Eye"),
        FilterItem(PhotoFilter.GRAIN, "filters/grain.webp", "Grain"),
        FilterItem(PhotoFilter.GRAY_SCALE, "filters/gray_scale.webp", "Abu-abu"),
        FilterItem(PhotoFilter.LOMISH, "filters/lomish.webp", "Lomo"),
        FilterItem(PhotoFilter.NEGATIVE, "filters/negative.webp", "Negatif"),
        FilterItem(PhotoFilter.POSTERIZE, "filters/posterize.webp", "Poster"),
        FilterItem(PhotoFilter.SATURATE, "filters/saturate.webp", "Saturasi"),
        FilterItem(PhotoFilter.SEPIA, "filters/sepia.webp", "Sepia"),
        FilterItem(PhotoFilter.SHARPEN, "filters/sharpen.webp", "Tajam"),
        FilterItem(PhotoFilter.TEMPERATURE, "filters/temprature.webp", "Hangat"),
        FilterItem(PhotoFilter.TINT, "filters/tint.webp", "Tint"),
        FilterItem(PhotoFilter.VIGNETTE, "filters/vignette.webp", "Vignette"),
        FilterItem(PhotoFilter.CROSS_PROCESS, "filters/cross_process.webp", "Cross"),
        FilterItem(PhotoFilter.BLACK_WHITE, "filters/b_n_w.webp", "Hitam Putih"),
        FilterItem(PhotoFilter.FLIP_HORIZONTAL, "filters/flip_horizental.webp", "Balik H"),
        FilterItem(PhotoFilter.FLIP_VERTICAL, "filters/flip_vertical.webp", "Balik V"),
        FilterItem(PhotoFilter.ROTATE, "filters/rotate.webp", "Putar"),
    )
}

/** Strip filter horizontal: thumbnail + nama, filter aktif diberi bingkai putih. */
@Composable
fun FiltersSection(
    selectedFilter: PhotoFilter,
    filterListener: FilterListener,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        items(FilterData.filters, key = { it.filter }) { item ->
            FilterThumbnail(
                item = item,
                selected = item.filter == selectedFilter,
                onClick = { filterListener.onFilterSelected(item.filter) },
            )
        }
    }
}

@Composable
private fun FilterThumbnail(
    item: FilterItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.iconPath) {
        bitmap = withContext(Dispatchers.IO) { loadAsset(context, item.iconPath) }
    }
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 80.dp)
                .clip(shape)
                .background(Color(0xFF1F2C34))
                .then(if (selected) Modifier.border(2.dp, Color.White, shape) else Modifier),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.name,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun loadAsset(context: Context, path: String): Bitmap? =
    try {
        context.assets.open(path).use(BitmapFactory::decodeStream)
    } catch (e: IOException) {
        null
    }
