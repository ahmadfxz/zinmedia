package com.zinmedia.live

import android.graphics.SurfaceTexture
import android.view.Surface
import com.pedro.encoder.input.sources.OrientationConfig
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.sources.video.VideoSource
import com.zinmedia.camera.LiveCameraHost

/**
 * Sumber video RootEncoder dari kamera zinmedia: frame yang sudah diproses (filter, penghalus,
 * efek wajah), tegak 9:16 dan tidak di-mirror, digambar ke SurfaceTexture milik encoder.
 */
internal class LiveVideoSource(private val camera: LiveCameraHost) : VideoSource() {

    private var surface: Surface? = null
    private var running = false

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean = true

    override fun start(surfaceTexture: SurfaceTexture) {
        surfaceTexture.setDefaultBufferSize(width, height)
        val target = Surface(surfaceTexture)
        surface = target
        camera.addStreamOutput(target, width, height)
        running = true
    }

    override fun stop() {
        surface?.let {
            camera.removeStreamOutput(it)
            it.release()
        }
        surface = null
        running = false
    }

    override fun release() = stop()

    override fun isRunning(): Boolean = running

    /** Frame sudah tegak & berbingkai 9:16 (potret): jangan diputar lagi oleh RootEncoder. */
    override fun getOrientationConfig(): OrientationConfig =
        OrientationConfig(cameraOrientation = 0, isPortrait = true, forced = OrientationForced.NONE)
}
