package com.jernih.videoeditor.data

import android.graphics.Typeface

data class FontItem(
    val id: Int,
    val name: String,
    val typeface: Typeface? = null
)