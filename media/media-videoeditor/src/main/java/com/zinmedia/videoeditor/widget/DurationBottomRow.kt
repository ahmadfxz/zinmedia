package com.zinmedia.videoeditor.widget

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zinmedia.videoeditor.R

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
