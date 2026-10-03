package com.zinmedia.photoeditor.imageeditor.filters

import com.zinmedia.photoeditor.engine.PhotoFilter

internal fun interface FilterListener {
    fun onFilterSelected(photoFilter: PhotoFilter)
}