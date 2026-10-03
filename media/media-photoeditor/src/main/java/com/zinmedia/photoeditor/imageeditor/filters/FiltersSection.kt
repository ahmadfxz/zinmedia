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
import com.zinmedia.photoeditor.PhotoEditorConfig
import com.zinmedia.photoeditor.PhotoFilterOption
import com.zinmedia.photoeditor.engine.PhotoFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Thumbnail bawaan untuk tiap filter (aset library). */
private val FilterThumbnails: Map<PhotoFilter, String> = mapOf(
    PhotoFilter.NONE to "filters/original.webp",
    PhotoFilter.AUTO_FIX to "filters/auto_fix.webp",
    PhotoFilter.BRIGHTNESS to "filters/brightness.webp",
    PhotoFilter.CONTRAST to "filters/contrast.webp",
    PhotoFilter.DOCUMENTARY to "filters/documentary.webp",
    PhotoFilter.DUE_TONE to "filters/dual_tone.webp",
    PhotoFilter.FILL_LIGHT to "filters/fill_light.webp",
    PhotoFilter.FISH_EYE to "filters/fish_eye.webp",
    PhotoFilter.GRAIN to "filters/grain.webp",
    PhotoFilter.GRAY_SCALE to "filters/gray_scale.webp",
    PhotoFilter.LOMISH to "filters/lomish.webp",
    PhotoFilter.NEGATIVE to "filters/negative.webp",
    PhotoFilter.POSTERIZE to "filters/posterize.webp",
    PhotoFilter.SATURATE to "filters/saturate.webp",
    PhotoFilter.SEPIA to "filters/sepia.webp",
    PhotoFilter.SHARPEN to "filters/sharpen.webp",
    PhotoFilter.TEMPERATURE to "filters/temprature.webp",
    PhotoFilter.TINT to "filters/tint.webp",
    PhotoFilter.VIGNETTE to "filters/vignette.webp",
    PhotoFilter.CROSS_PROCESS to "filters/cross_process.webp",
    PhotoFilter.BLACK_WHITE to "filters/b_n_w.webp",
    PhotoFilter.FLIP_HORIZONTAL to "filters/flip_horizental.webp",
    PhotoFilter.FLIP_VERTICAL to "filters/flip_vertical.webp",
    PhotoFilter.ROTATE to "filters/rotate.webp",
)

/** Strip filter horizontal: thumbnail + nama, filter aktif diberi bingkai putih. */
@Composable
internal fun FiltersSection(
    selectedFilter: PhotoFilter,
    filterListener: FilterListener,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        items(PhotoEditorConfig.filters, key = { it.filter }) { option ->
            FilterThumbnail(
                option = option,
                selected = option.filter == selectedFilter,
                onClick = { filterListener.onFilterSelected(option.filter) },
            )
        }
    }
}

@Composable
private fun FilterThumbnail(
    option: PhotoFilterOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(option.filter) {
        bitmap = FilterThumbnails[option.filter]?.let { path ->
            withContext(Dispatchers.IO) { loadAsset(context, path) }
        }
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
                    contentDescription = option.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = option.label,
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
