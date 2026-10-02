package com.zinmedia.photoeditor.imageeditor.filters

import com.zinmedia.photoeditor.engine.PhotoFilter

interface FilterListener {
    fun onFilterSelected(photoFilter: PhotoFilter)
}