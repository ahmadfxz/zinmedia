package com.zinmedia.photoeditor.imageeditor.filters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Close
import com.zinmedia.photoeditor.engine.PhotoFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

// FilterItem.kt
data class FilterItem(
    val filter: PhotoFilter,
    val iconPath: String,
    val name: String
)

// FilterData.kt
object FilterData {
    val filters = listOf(
        FilterItem(PhotoFilter.NONE, "filters/original.jpg", "Original"),
        FilterItem(PhotoFilter.AUTO_FIX, "filters/auto_fix.png", "Auto Fix"),
        FilterItem(PhotoFilter.BRIGHTNESS, "filters/brightness.png", "Brightness"),
        FilterItem(PhotoFilter.CONTRAST, "filters/contrast.png", "Contrast"),
        FilterItem(PhotoFilter.DOCUMENTARY, "filters/documentary.png", "Documentary"),
        FilterItem(PhotoFilter.DUE_TONE, "filters/dual_tone.png", "Dual Tone"),
        FilterItem(PhotoFilter.FILL_LIGHT, "filters/fill_light.png", "Fill Light"),
        FilterItem(PhotoFilter.FISH_EYE, "filters/fish_eye.png", "Fish Eye"),
        FilterItem(PhotoFilter.GRAIN, "filters/grain.png", "Grain"),
        FilterItem(PhotoFilter.GRAY_SCALE, "filters/gray_scale.png", "Gray Scale"),
        FilterItem(PhotoFilter.LOMISH, "filters/lomish.png", "Lomish"),
        FilterItem(PhotoFilter.NEGATIVE, "filters/negative.png", "Negative"),
        FilterItem(PhotoFilter.POSTERIZE, "filters/posterize.png", "Posterize"),
        FilterItem(PhotoFilter.SATURATE, "filters/saturate.png", "Saturate"),
        FilterItem(PhotoFilter.SEPIA, "filters/sepia.png", "Sepia"),
        FilterItem(PhotoFilter.SHARPEN, "filters/sharpen.png", "Sharpen"),
        FilterItem(PhotoFilter.TEMPERATURE, "filters/temprature.png", "Temperature"),
        FilterItem(PhotoFilter.TINT, "filters/tint.png", "Tint"),
        FilterItem(PhotoFilter.VIGNETTE, "filters/vignette.png", "Vignette"),
        FilterItem(PhotoFilter.CROSS_PROCESS, "filters/cross_process.png", "Cross Process"),
        FilterItem(PhotoFilter.BLACK_WHITE, "filters/b_n_w.png", "Black & White"),
        FilterItem(PhotoFilter.FLIP_HORIZONTAL, "filters/flip_horizental.png", "Flip Horizontal"),
        FilterItem(PhotoFilter.FLIP_VERTICAL, "filters/flip_vertical.png", "Flip Vertical"),
        FilterItem(PhotoFilter.ROTATE, "filters/rotate.png", "Rotate")
    )
}

// FiltersSection.kt
@Composable
fun FiltersSection(
    filterListener: FilterListener,
    onCloseClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = remember { FilterData.filters }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Close button di pojok kanan
            IconButton(
                onClick = onCloseClicked,
                modifier = Modifier
                    .size(24.dp)
            ) {
                Icon(
                    imageVector = EvaIcons.Fill.Close,
                    contentDescription = "Close filters",
                    tint = Color.White
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(filters) { filterItem ->
                FilterItem(
                    filterItem = filterItem,
                    onFilterSelected = { filterListener.onFilterSelected(it) }
                )
            }
        }
    }
}

@Composable
fun FilterItem(
    filterItem: FilterItem,
    onFilterSelected: (PhotoFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Load bitmap asynchronously
    LaunchedEffect(filterItem.iconPath) {
        bitmap = withContext(Dispatchers.IO) {
            getBitmapFromAsset(context, filterItem.iconPath)
        }
    }

    Column(
        modifier = modifier
            .width(80.dp)
            .clickable { onFilterSelected(filterItem.filter) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Filter Preview Image
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = filterItem.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            // Selection indicator (you can add this later)
            /*
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 2.dp,
                            color = Color(0xFF0095F6),
                            shape = RoundedCornerShape(12.dp)
                        )
                )
            }
            */
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Filter Name
        Text(
            text = filterItem.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Helper function to load bitmap from assets
private fun getBitmapFromAsset(context: Context, strName: String): Bitmap? {
    return try {
        context.assets.open(strName).use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}