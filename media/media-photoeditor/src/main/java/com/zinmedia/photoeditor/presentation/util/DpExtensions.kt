package com.zinmedia.photoeditor.presentation.util

import android.content.res.Resources
import androidx.compose.ui.unit.Dp

// Extension function for dp to px conversion
internal fun Dp.toPx(): Float {
    return this.value * Resources.getSystem().displayMetrics.density
}