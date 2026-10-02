package com.zinmedia.videoeditor.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Outline
import compose.icons.evaicons.outline.Close
import compose.icons.evaicons.outline.Music

@Composable
fun AddAudioButtonWidget(
    title: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onClear: (() -> Unit),
) {


    // Button
    Box(
        modifier = modifier
            .padding(16.dp)
            .width(200.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Color.Black.copy(alpha = 0.5f)
            )
            .clickable { onClick() }
        ,
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = EvaIcons.Outline.Music, // ganti dengan icon musikmu
                contentDescription = "Add Music",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                title ?: "Tambahkan Audio",
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            title?.let {
                Icon(
                    imageVector = EvaIcons.Outline.Close, // ganti dengan icon musikmu
                    contentDescription = "Add Music",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp).clickable {
                        onClear()
                    }
                )
            }

        }

    }
}
