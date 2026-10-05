package com.zinmedia.camera

import com.zinmedia.effects.EffectFilter
import com.zinmedia.effects.LutFilter
import com.zinmedia.effects.effectFilters
import com.zinmedia.videoeditor.VideoEditorConfig

/** Filter warna kamera (sama dengan filter efek siaran). */
internal typealias CameraFilter = EffectFilter

/** Filter bawaan, lalu filter LUT milik aplikasi (dari [VideoEditorConfig.filters]). */
internal fun cameraFilters(): List<CameraFilter> =
    effectFilters(VideoEditorConfig.filters.map { LutFilter(it.name, it.cubeUrl) })
