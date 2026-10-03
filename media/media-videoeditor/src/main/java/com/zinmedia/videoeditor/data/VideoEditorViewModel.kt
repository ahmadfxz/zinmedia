package com.zinmedia.videoeditor.data

import com.zinmedia.videoeditor.draw.opaqueBounds
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleColorLut
import androidx.media3.exoplayer.ExoPlayer
import coil3.Bitmap
import com.zinmedia.videoeditor.core.helper.loadLutCubeFromUrl
import com.zinmedia.videoeditor.data.repository.VideoRepository
import com.zinmedia.videoeditor.textlayer.TextLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID



internal class VideoEditorViewModelFactory(
    private val repository: VideoRepository,
) : ViewModelProvider.Factory {
    @OptIn(UnstableApi::class)
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VideoEditorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VideoEditorViewModel(
                repository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


/**
 * Satu lapisan di atas video. Semuanya berupa gambar (stiker, emoji, teks, coretan) agar
 * preview dan hasil ekspor identik.
 *
 * @param posXpx posisi X (koordinat NDC -1..1, untuk ekspor).
 * @param widthFraction lebar relatif terhadap lebar frame (0..1) sesuai preview; 0 = belum diukur.
 * @param textLayer data teks bila overlay ini lapisan teks (bisa diedit ulang).
 */
internal data class Overlay(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: ImageBitmap,
    val posXpx: Float = 0f,
    val posYpx: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val widthFraction: Float = 0f,
    val textLayer: TextLayer? = null,
)

@UnstableApi
internal class VideoEditorViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    // ---------------------------------
    // ExoPlayer
    // ---------------------------------
    private var _exoPlayer: ExoPlayer? = null
    internal val exoPlayer: ExoPlayer?
        get() = _exoPlayer

    internal fun createExoPlayer(context: Context) {
        if (_exoPlayer == null) {
            _exoPlayer = ExoPlayer.Builder(context).build().apply {
                volume = 1f

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) startTrackingPosition() else stopTrackingPosition()
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        // Rotasi 90/270 berarti frame yang di-decode
                        // masih dalam orientasi landscape, jadi lebar
                        // dan tinggi perlu ditukar supaya rasio yang
                        // dipakai untuk layout sesuai tampilan aslinya.
                        val isRotated =
                            videoSize.unappliedRotationDegrees == 90 ||
                                    videoSize.unappliedRotationDegrees == 270

                        val width = if (isRotated) videoSize.height else videoSize.width
                        val height = if (isRotated) videoSize.width else videoSize.height

                        if (width > 0 && height > 0) {
                            _videoWidth.value = width
                            _videoHeight.value = height
                        }
                    }
                })
            }
        }
    }

    /**
     * Baca ukuran video dari metadata sebelum pemutar siap, agar bingkai preview langsung
     * memakai rasio yang benar (tidak melompat dari 9:16).
     */
    internal suspend fun prefetchVideoSize(context: Context, uri: Uri) {
        if (_videoWidth.value > 0 && _videoHeight.value > 0) return
        val size = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.zinmedia.videoeditor.data.repository.readDisplaySize(context, uri)
        }
        if (size.first > 0 && size.second > 0 && _videoWidth.value == 0) {
            _videoWidth.value = size.first
            _videoHeight.value = size.second
        }
    }

    internal fun releaseExoPlayer() {
        stopTrackingPosition()
        _exoPlayer?.release()
        _exoPlayer = null
    }

    // ---------------------------------
    // Video state
    // ---------------------------------
    private val _videoDurationMs = MutableStateFlow(0L)
    internal val videoDurationMs: StateFlow<Long> = _videoDurationMs

    private val _startMs = MutableStateFlow(0L)
    internal val startMs: StateFlow<Long> = _startMs

    private val _endMs = MutableStateFlow(0L)
    internal val endMs: StateFlow<Long> = _endMs

    private val _currentPlayTimeMs = MutableStateFlow(0L)
    internal val currentPlayTimeMs: StateFlow<Long> = _currentPlayTimeMs

    private var currentUri: Uri? = null

    // ---------------------------------
    // Export
    // ---------------------------------
    private val _exporting = MutableStateFlow(false)
    internal val exporting: StateFlow<Boolean> = _exporting

    private val _exportProgress = MutableStateFlow(0f)
    internal val exportProgress: StateFlow<Float> = _exportProgress

    private val _cubeUrl = MutableStateFlow("")
    internal val cubeUrl: StateFlow<String> = _cubeUrl

    private val _isInisialisasi = MutableStateFlow(true)

    internal val isInisialisasi: StateFlow<Boolean> = _isInisialisasi



    // ---------------------------------
    // Overlay
    // ---------------------------------
    private val _overlays = MutableStateFlow<List<Overlay>>(emptyList())
    internal val overlays: StateFlow<List<Overlay>> = _overlays

    private val _drawOverlay = MutableStateFlow<Overlay?>(null)
    internal val drawOverlay: StateFlow<Overlay?> = _drawOverlay

    private var _videoWidth = MutableStateFlow(0)
    internal val videoWidth: StateFlow<Int> = _videoWidth

    private var _videoHeight = MutableStateFlow(0)
    internal val videoHeight: StateFlow<Int> = _videoHeight

    init {
        viewModelScope.launch {
            cubeUrl.collectLatest { url -> applyCube(url) }
        }
    }

    /** Pasang (atau kosongkan) efek LUT pada pemutar preview. */
    private suspend fun applyCube(url: String) {
        if (url.isNotEmpty()) {
            val lutCube = loadLutCubeFromUrl(url)
            if (lutCube != null) {
                _exoPlayer?.apply {
                    stop()
                    setVideoEffects(listOf(SingleColorLut.createFromCube(lutCube)))
                    prepare()
                }
            } else {
                Log.e("LUT", "Gagal load LUT")
            }
        } else {
            // Tanpa filter: kosongkan efek preview.
            _exoPlayer?.apply {
                stop()
                setVideoEffects(emptyList())
                prepare()
            }
        }
    }

    internal fun addOverlay(overlay: Overlay) {
        _overlays.value = _overlays.value + overlay
    }

    internal fun updateOverlay(updated: Overlay) {
        _overlays.value = _overlays.value.map { if (it.id == updated.id) updated else it }
    }

    internal fun removeOverlay(id: String) {
        _overlays.value = _overlays.value.filter { it.id != id }
    }

    /** Area editor (lebar, tinggi) dalam satuan lebar video di preview: batas perluasan kanvas ekspor. */
    private var canvasLimit: Pair<Float, Float>? = null

    internal fun setPreviewLayout(videoWidthPx: Float, areaWidthPx: Float, areaHeightPx: Float) {
        if (videoWidthPx > 0f) canvasLimit = areaWidthPx / videoWidthPx to areaHeightPx / videoWidthPx
    }

    /** Piksel hasil ekspor per piksel preview; coretan dirender dengan skala ini agar tetap tajam. */
    internal fun drawingScale(videoWidthPx: Float): Float {
        val exportWidth = com.zinmedia.videoeditor.data.repository.fitExportSize(_videoWidth.value, _videoHeight.value).first
        return if (videoWidthPx > 1f) (exportWidth / videoWidthPx).coerceAtLeast(1f) else 1f
    }

    /**
     * Simpan coretan sebagai overlay. [bitmap] mencakup seluruh area editor (video di tengahnya);
     * dipotong ke bagian yang tergambar lalu diposisikan relatif terhadap frame video, seperti stiker.
     */
    internal fun setDrawOverlay(
        bitmap: Bitmap,
        areaWidthPx: Float,
        areaHeightPx: Float,
        videoWidthPx: Float,
        videoHeightPx: Float,
    ) {
        val bounds = bitmap.opaqueBounds()
        if (bounds == null || areaWidthPx <= 0f || videoWidthPx <= 1f || videoHeightPx <= 1f) {
            _drawOverlay.value = null
            return
        }
        val scale = bitmap.width / areaWidthPx
        val cropped = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())
        // Pusat potongan dalam koordinat frame video (preview px).
        val centerX = bounds.exactCenterX() / scale - (areaWidthPx - videoWidthPx) / 2f
        val centerY = bounds.exactCenterY() / scale - (areaHeightPx - videoHeightPx) / 2f
        _drawOverlay.value = Overlay(
            bitmap = cropped.asImageBitmap(),
            posXpx = centerX / videoWidthPx * 2f - 1f,
            posYpx = -(centerY / videoHeightPx * 2f - 1f),
            widthFraction = bounds.width() / scale / videoWidthPx,
        )
    }

    // ---------------------------------
    // Play tracking
    // ---------------------------------
    private var trackingJob: kotlinx.coroutines.Job? = null

    /** Pantau posisi pemutaran hanya selama video diputar (hemat baterai). */
    private fun startTrackingPosition() {
        if (trackingJob?.isActive == true) return
        trackingJob = viewModelScope.launch {
            while (true) {
                _currentPlayTimeMs.value = _exoPlayer?.currentPosition ?: 0L
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun stopTrackingPosition() {
        trackingJob?.cancel()
        trackingJob = null
        _exoPlayer?.let { _currentPlayTimeMs.value = it.currentPosition }
    }

    // ---------------------------------
    // Cube effect
    // ---------------------------------
    internal fun selectCubeEffect(urlCube: String) {
        _cubeUrl.value = urlCube
    }

    // ---------------------------------
    // Load video
    // ---------------------------------
    internal fun loadUri(uri: Uri) {
        // Pemutar dibuat ulang (mis. halaman editor dibuka lagi): pertahankan trim & filter.
        if (uri == currentUri && _videoDurationMs.value > 0) {
            _exoPlayer?.apply {
                setMediaItem(buildTrimmedMediaItem(uri, _startMs.value, _endMs.value))
                prepare()
                repeatMode = Player.REPEAT_MODE_ALL
            }
            if (_cubeUrl.value.isNotEmpty()) viewModelScope.launch { applyCube(_cubeUrl.value) }
            return
        }
        currentUri = uri
        val mediaItem = MediaItem.fromUri(uri)
        _exoPlayer?.apply {
            setMediaItem(mediaItem)
            prepare()
            repeatMode = Player.REPEAT_MODE_ALL
        }
        _exoPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val duration = _exoPlayer?.duration ?: 0L
                if (duration > 0) {
                    _videoDurationMs.value = duration
                    _endMs.value = duration
                    _exoPlayer?.removeListener(this)
                }
            }
        })
    }

    internal fun setTrim(
        startMs: Long,
        endMs: Long
    ) {
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtMost(videoDurationMs.value)
        // Nilai sama (mis. laporan awal dari timeline): jangan muat ulang video, bisa membuat preview berkedip.
        if (safeStart == _startMs.value && safeEnd == _endMs.value) return
        _startMs.value = safeStart
        _endMs.value = safeEnd
        reloadTrimmedMediaItem()
    }

    private fun reloadTrimmedMediaItem() {
        currentUri?.let {
            _exoPlayer?.setMediaItem(buildTrimmedMediaItem(it, _startMs.value, _endMs.value))
        }
    }

    internal fun buildTrimmedMediaItem(
        uri: Uri,
        startMs: Long,
        endMs: Long
    ): MediaItem {

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()

        return MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clipping)
            .build()
    }


    // ---------------------------------
    // Export video via repository
    // ---------------------------------
    /** Ada teks/stiker/coretan, filter, atau trim. */
    internal fun hasEdits(): Boolean {
        val duration = _videoDurationMs.value
        return _overlays.value.isNotEmpty() ||
            _drawOverlay.value != null ||
            _cubeUrl.value.isNotEmpty() ||
            _startMs.value > 0 ||
            (duration > 0 && _endMs.value in 1 until duration)
    }

    /** Ekspor video ke [outputFile]; mengembalikan path hasil, atau melempar error. */
    internal suspend fun exportToFile(context: Context, outputFile: File): String =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            exportVideo(context, outputFile.parentFile ?: context.cacheDir, outputFile) { path, error ->
                if (path != null) cont.resumeWith(Result.success(path))
                else cont.resumeWith(Result.failure(error ?: IllegalStateException("Ekspor video gagal")))
            }
        }

    internal fun exportVideo(
        context: Context,
        cacheDir: File,
        outputFile: File? = null,
        onResult: (outputPath: String?, error: Exception?) -> Unit
    ) {

        val uri = currentUri ?: run {
            onResult(null, IllegalStateException("No media loaded"))
            return
        }

        // Urutan tumpukan sama dengan preview: urutan penambahan, coretan paling atas.
        val allOverlay = _overlays.value + listOfNotNull(_drawOverlay.value)

        viewModelScope.launch {
            _exporting.value = true
            _exportProgress.value = 0f

            repository.exportVideo(
                context = context,
                uri = uri,
                audioUrl = null,
                startMs = _startMs.value,
                endMs = _endMs.value,
                overlays = allOverlay,
                canvasLimit = canvasLimit,
                cubeUrl = _cubeUrl.value,
                cacheDir = cacheDir,
                outputFile = outputFile,
                progressCallback = { progress -> _exportProgress.value = progress },
                resultCallback = { path, error ->
                    _exporting.value = false
                    onResult(path, error)
                }
            )
        }
    }

    // ---------------------------------
    // Add sticker via repository
    // ---------------------------------
    internal suspend fun addStickerFromUrl(
        context: Context,
        url: String,
        posXpx: Float = 0f,
        posYpx: Float = 0f,
        scale: Float = 1f,
        rotation: Float = 0f
    ): Overlay {
        return repository.loadSticker(context, url, posXpx, posYpx, scale, rotation)
    }

    override fun onCleared() {
        super.onCleared()
        releaseExoPlayer()
    }

}

private const val POSITION_POLL_MS = 50L
