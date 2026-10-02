package com.zinmedia.videoeditor.widget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun ExportProgress(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(60.dp)
    ) {
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = modifier.fillMaxSize(),
            color = Color.White,
            strokeWidth = 5.dp,
            trackColor = Color.Gray.copy(alpha = 0.5f),
            strokeCap = StrokeCap.Round
        )
        Text(
            text = "${(animatedProgress * 100).toInt()}%",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}