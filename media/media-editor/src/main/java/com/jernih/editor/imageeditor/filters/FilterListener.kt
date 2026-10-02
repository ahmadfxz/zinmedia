package com.jernih.editor.imageeditor.filters

import com.softech.photoeditor.PhotoFilter

interface FilterListener {
    fun onFilterSelected(photoFilter: PhotoFilter)
}