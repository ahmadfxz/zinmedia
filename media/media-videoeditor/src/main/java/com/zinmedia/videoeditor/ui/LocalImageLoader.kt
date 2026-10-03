package com.zinmedia.videoeditor.ui

import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader

internal val LocalImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("No ImageLoader provided")
}