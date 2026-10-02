package com.jernih.editor.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable

@Composable
fun SlideFadeVisibility(
    visible: Boolean,
    initialOffsetX: (Int) -> Int,
    targetOffsetX: (Int) -> Int,
    duration: Int = 200,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(duration)) +
                slideInHorizontally(
                    animationSpec = tween(duration),
                    initialOffsetX = initialOffsetX
                ),
        exit = fadeOut(tween(duration)) +
                slideOutHorizontally(
                    animationSpec = tween(duration),
                    targetOffsetX = targetOffsetX
                )
    ) {
        content()
    }
}

