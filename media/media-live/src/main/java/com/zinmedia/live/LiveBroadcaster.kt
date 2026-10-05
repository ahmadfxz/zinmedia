package com.zinmedia.live

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.common.StreamingStatsReport
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.util.QueueAwareBitrateAdapter
import com.zinmedia.camera.LiveCameraHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Tahap siaran. */
public enum class LivePhase {
    /** Belum/tidak mengudara. */
    Idle,

    /** Menghubungi server. */
    Connecting,

    /** Mengudara. */
    Live,

    /** Koneksi terputus, sedang menyambung ulang otomatis. */
    Reconnecting,

    /** Gagal; lihat [LiveState.errorMessage]. Panggil [LiveCamera.clearError] atau mulai lagi. */
    Error,
}

/** Status siaran untuk UI aplikasi. */
public data class LiveState(
    val phase: LivePhase = LivePhase.Idle,
    val errorMessage: String? = null,
    val micMuted: Boolean = false,
    /** Waktu mulai mengudara (`SystemClock.elapsedRealtime()`), 0 bila tidak mengudara. */
    val liveSinceMs: Long = 0L,
) {
    /** Sedang menghubungi, mengudara, atau menyambung ulang. */
    val isActive: Boolean get() = phase == LivePhase.Connecting || phase == LivePhase.Live || phase == LivePhase.Reconnecting
}

/**
 * Mengirim kamera zinmedia (via [LiveVideoSource]) + mikrofon ke server RTMP dengan RootEncoder:
 * H.264 720×1280 30 fps + AAC, bitrate turun otomatis saat unggahan tersendat, dan menyambung
 * ulang otomatis bila siaran yang sudah mengudara terputus.
 */
internal class LiveBroadcaster(
    private val context: Context,
    camera: LiveCameraHost,
) : ConnectChecker {

    private val stream = RtmpStream(context, this, LiveVideoSource(camera), MicrophoneSource())
    private var bitrateAdapter: QueueAwareBitrateAdapter? = null
    private var prepared = false

    /** Disetel saat berhenti dengan sengaja, agar putusnya koneksi tidak dianggap gagal. */
    @Volatile
    private var stopping = false

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private fun prepare(): Boolean {
        if (prepared) return true
        val videoBitrate = when {
            prepareVideo(720, 1280, VIDEO_BITRATE) -> VIDEO_BITRATE
            prepareVideo(540, 960, LOW_VIDEO_BITRATE) -> LOW_VIDEO_BITRATE
            else -> 0
        }
        val audioReady = videoBitrate > 0 && runCatching {
            stream.prepareAudio(AUDIO_SAMPLE_RATE, true, AUDIO_BITRATE, true, true)
        }.getOrDefault(false)
        if (!audioReady) {
            _state.update { it.copy(phase = LivePhase.Error, errorMessage = context.getString(R.string.zm_live_error_open)) }
            return false
        }
        stream.getGlInterface().apply {
            // Frame dari sumber sudah tegak & berbingkai 9:16: jangan diputar ulang oleh RootEncoder.
            autoHandleOrientation = false
            setAspectRatioMode(AspectRatioMode.Fill)
        }
        stream.getStreamClient().setReTries(MAX_RECONNECTS)
        // Adaptor bekerja pada total bitrate (video + audio); hanya video yang diubah.
        bitrateAdapter = QueueAwareBitrateAdapter(videoBitrate + AUDIO_BITRATE, MIN_VIDEO_BITRATE + AUDIO_BITRATE) { total ->
            stream.setVideoBitrateOnFly(total - AUDIO_BITRATE)
        }
        prepared = true
        return true
    }

    private fun prepareVideo(width: Int, height: Int, bitrate: Int): Boolean =
        runCatching { stream.prepareVideo(width, height, bitrate, 30, 2, 0) }.getOrDefault(false)

    /** Mulai mengirim ke [publishUrl] (rtmp://… atau rtmps://…). */
    fun start(publishUrl: String) {
        if (stream.isStreaming || !prepare()) return
        stopping = false
        _state.update { it.copy(phase = LivePhase.Connecting, errorMessage = null) }
        runCatching { stream.startStream(publishUrl) }.onFailure { e ->
            _state.update {
                it.copy(phase = LivePhase.Error, errorMessage = e.message ?: context.getString(R.string.zm_live_error_start))
            }
        }
    }

    fun stop() {
        stopping = true
        if (stream.isStreaming) runCatching { stream.stopStream() }
        _state.update { it.copy(phase = LivePhase.Idle, liveSinceMs = 0L) }
    }

    fun setMicMuted(muted: Boolean) {
        (stream.audioSource as? MicrophoneSource)?.let { if (muted) it.mute() else it.unMute() }
        _state.update { it.copy(micMuted = muted) }
    }

    fun clearError() {
        _state.update { it.copy(phase = LivePhase.Idle, errorMessage = null) }
    }

    fun release() {
        stopping = true
        runCatching { stream.release() }
    }

    // region ConnectChecker (dipanggil RootEncoder di thread utama)

    override fun onConnectionStarted(url: String) {
        bitrateAdapter?.reset()
    }

    override fun onStreamingStats(report: StreamingStatsReport) {
        bitrateAdapter?.onStreamingStats(report)
    }

    override fun onConnectionSuccess() {
        _state.update {
            it.copy(
                phase = LivePhase.Live,
                errorMessage = null,
                liveSinceMs = if (it.liveSinceMs == 0L) android.os.SystemClock.elapsedRealtime() else it.liveSinceMs,
            )
        }
    }

    override fun onConnectionFailed(reason: String) {
        // Hanya siaran yang sudah mengudara yang menyambung ulang; koneksi pertama langsung gagal
        // agar penyiar segera tahu masalahnya.
        val phase = _state.value.phase
        val wasOnAir = phase == LivePhase.Live || phase == LivePhase.Reconnecting
        if (!stopping && wasOnAir && stream.getStreamClient().reTry(RECONNECT_DELAY_MS, reason)) {
            _state.update { it.copy(phase = LivePhase.Reconnecting) }
            return
        }
        fail(context.getString(R.string.zm_live_error_dropped, reason))
    }

    private fun fail(message: String) {
        stopping = true
        runCatching { stream.stopStream() }
        _state.update { it.copy(phase = LivePhase.Error, errorMessage = message, liveSinceMs = 0L) }
    }

    override fun onDisconnect() {
        if (stopping && _state.value.phase != LivePhase.Error) {
            _state.update { it.copy(phase = LivePhase.Idle, liveSinceMs = 0L) }
        }
    }

    override fun onAuthError() {
        fail(context.getString(R.string.zm_live_error_auth))
    }

    override fun onAuthSuccess() = Unit

    // endregion

    private companion object {
        const val VIDEO_BITRATE = 2_000_000
        const val LOW_VIDEO_BITRATE = 1_000_000
        const val MIN_VIDEO_BITRATE = 400_000
        const val AUDIO_BITRATE = 128_000
        const val AUDIO_SAMPLE_RATE = 44_100
        const val MAX_RECONNECTS = 10
        const val RECONNECT_DELAY_MS = 3_000L
    }
}
