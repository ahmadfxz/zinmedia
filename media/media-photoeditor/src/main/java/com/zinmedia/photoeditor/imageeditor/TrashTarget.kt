package com.zinmedia.photoeditor.imageeditor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zinmedia.photoeditor.R

/** Tempat sampah yang muncul saat lapisan diseret; membesar dan memerah saat jari di atasnya. */
@Composable
internal fun TrashTarget(active: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (active) 1.3f else 1f, label = "trash")
    Box(
        modifier = modifier
            .size(56.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .background(if (active) Color.Red.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.zm_ic_trash),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}
