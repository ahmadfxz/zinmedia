package com.zinmedia.videoeditor.data

import android.graphics.Typeface

internal data class FontItem(
    val id: Int,
    val name: String,
    val typeface: Typeface? = null
)