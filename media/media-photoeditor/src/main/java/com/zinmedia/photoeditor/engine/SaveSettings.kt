package com.zinmedia.photoeditor.engine

import android.graphics.Bitmap.CompressFormat
import androidx.annotation.IntRange

/**
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @since 8/8/2018
 * Builder Class to apply multiple save options
 */
internal class SaveSettings private constructor(builder: Builder) {
    internal val isClearViewsEnabled: Boolean
    internal val compressFormat: CompressFormat
    internal val compressQuality: Int

    internal class Builder {
        @JvmField internal var isClearViewsEnabled = true
        @JvmField internal var compressFormat = CompressFormat.PNG
        @JvmField internal var compressQuality = 100


        /**
         * Define a flag to clear the view after saving the image
         *
         * @param clearViewsEnabled true if you want to clear all the views on [PhotoEditorView]
         * @return Builder
         */
        internal fun setClearViewsEnabled(clearViewsEnabled: Boolean): Builder {
            isClearViewsEnabled = clearViewsEnabled
            return this
        }

        /**
         * Set the compression format for the file to save: JPEG, PNG or WEBP
         * @see{android.graphics.Bitmap.CompressFormat}
         * @param compressFormat JPEG, PNG or WEBP
         * @return Builder
         */
        internal fun setCompressFormat(compressFormat: CompressFormat): Builder {
            this.compressFormat = compressFormat
            return this
        }

        /**
         * Set the expected compression quality for the output, a number between
         * 0 and 100
         * @param compressQuality An integer from 0 to 100
         * @return Builder
         */
        internal fun setCompressQuality(@IntRange(from = 0, to = 100) compressQuality: Int): Builder {
            this.compressQuality = compressQuality
            return this
        }

        internal fun build(): SaveSettings {
            return SaveSettings(this)
        }
    }

    init {
        isClearViewsEnabled = builder.isClearViewsEnabled
        compressFormat = builder.compressFormat
        compressQuality = builder.compressQuality
    }
}