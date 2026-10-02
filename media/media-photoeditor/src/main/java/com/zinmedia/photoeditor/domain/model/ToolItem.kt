package com.zinmedia.photoeditor.domain.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.zinmedia.photoeditor.imageeditor.tools.ToolType

data class ToolItem(
    val type: ToolType,
    @DrawableRes val iconRes: Int,
    val label: String,
    val color: Color
)