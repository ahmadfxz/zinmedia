package com.zinmedia.photoeditor.engine.shape

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 *
 *
 * Used to hold a Shape parameters: type, size, opacity and color.
 *
 */
internal class ShapeBuilder {

    internal var shapeType: ShapeType = ShapeType.Brush
        private set

    internal var shapeSize: Float = DEFAULT_SHAPE_SIZE
        private set

    @androidx.annotation.IntRange(from = 0, to = 255)
    internal var shapeOpacity: Int? = DEFAULT_SHAPE_OPACITY
        private set

    @get:ColorInt
    @ColorInt
    internal var shapeColor: Int = DEFAULT_SHAPE_COLOR
        private set

    internal fun withShapeType(shapeType: ShapeType): ShapeBuilder {
        this.shapeType = shapeType
        return this
    }

    internal fun withShapeSize(size: Float): ShapeBuilder {
        shapeSize = size
        return this
    }

    internal fun withShapeOpacity(
        @androidx.annotation.IntRange(
            from = 0,
            to = 255
        ) opacity: Int?
    ): ShapeBuilder {
        shapeOpacity = opacity
        return this
    }

    internal fun withShapeColor(@ColorInt color: Int): ShapeBuilder {
        shapeColor = color
        return this
    }

    internal companion object {
        internal const val DEFAULT_SHAPE_SIZE = 25.0f
        internal val DEFAULT_SHAPE_OPACITY = null
        internal const val DEFAULT_SHAPE_COLOR = Color.BLACK
    }

}