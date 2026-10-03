package com.zinmedia.photoeditor.engine.shape

import android.graphics.Paint

/**
 * Simple data class to be put in an ordered Stack
 */
internal open class ShapeAndPaint(
    internal val shape: AbstractShape,
    internal val paint: Paint
)