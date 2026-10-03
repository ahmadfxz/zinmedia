package com.zinmedia.photoeditor.engine

import android.graphics.Bitmap

/**
 * @author [Burhanuddin Rashid](https://github.com/burhanrashid52)
 * @version 0.1.2
 * @since 5/21/2018
 */
internal interface OnSaveBitmap {
    fun onBitmapReady(saveBitmap: Bitmap)
}