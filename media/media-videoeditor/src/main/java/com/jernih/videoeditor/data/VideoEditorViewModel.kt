package com.jernih.videoeditor.data

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
import com.jernih.videoeditor.core.helper.loadLutCubeFromUrl
import com.jernih.videoeditor.data.repository.VideoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID



class VideoEditorViewModelFactory(
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


data class Overlay(
    val id: String = UUID.randomUUID().toString(),
    val type: Type,
    val bitmap: ImageBitmap? = null,
    val text: String? = null,
    val color: Color = Color.White,
    val bgcolor: Color = Color.Transparent,
    val fontSize: TextUnit = 50.sp,
    val posXpx: Float = 0f, // posisi pixel dari video
    val posYpx: Float = 0f,
    val scale: Float = 0.5f,
    val rotation: Float = 0f,
    val typeface: Typeface = Typeface.DEFAULT,
) {
    enum class Type { STICKER, TEXT }
}

@UnstableApi
class VideoEditorViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    // ---------------------------------
    // ExoPlayer
    // ---------------------------------
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer?
        get() = _exoPlayer

    fun createExoPlayer(context: Context) {
        if (_exoPlayer == null) {
            _exoPlayer = ExoPlayer.Builder(context).build().apply {
                volume = 1f

                addListener(object : Player.Listener {
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

    fun releaseExoPlayer() {
        _exoPlayer?.release()
        _exoPlayer = null
    }

    // ---------------------------------
    // Video state
    // ---------------------------------
    private val _videoDurationMs = MutableStateFlow(0L)
    val videoDurationMs: StateFlow<Long> = _videoDurationMs

    private val _startMs = MutableStateFlow(0L)
    val startMs: StateFlow<Long> = _startMs

    private val _endMs = MutableStateFlow(0L)
    val endMs: StateFlow<Long> = _endMs

    private val _currentPlayTimeMs = MutableStateFlow(0L)
    val currentPlayTimeMs: StateFlow<Long> = _currentPlayTimeMs

    private var currentUri: Uri? = null

    // ---------------------------------
    // Export
    // ---------------------------------
    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress

    private val _cubeUrl = MutableStateFlow("")
    val cubeUrl: StateFlow<String> = _cubeUrl

    private val _isInisialisasi = MutableStateFlow(true)

    val isInisialisasi: StateFlow<Boolean> = _isInisialisasi

    private var currentAudioUrl: String? = null


    // ---------------------------------
    // Overlay
    // ---------------------------------
    private val _overlays = MutableStateFlow<List<Overlay>>(emptyList())
    val overlays: StateFlow<List<Overlay>> = _overlays

    private val _drawOverlay = MutableStateFlow<Overlay?>(null)
    val drawOverlay: StateFlow<Overlay?> = _drawOverlay

    private var _videoWidth = MutableStateFlow(0)
    val videoWidth: StateFlow<Int> = _videoWidth

    private var _videoHeight = MutableStateFlow(0)
    val videoHeight: StateFlow<Int> = _videoHeight

    init {
        viewModelScope.launch {
            cubeUrl.collectLatest { url ->
                if (url.isNotEmpty()) {
                    val lutCube = loadLutCubeFromUrl(cubeUrl.value)
                    if (lutCube != null) {
                        _exoPlayer?.apply {
                            val videoEffects = listOf(SingleColorLut.createFromCube(lutCube))
                            stop()
                            setVideoEffects(videoEffects)
                            prepare()
                        }
                    } else {
                        Log.e("LUT", "Gagal load LUT")
                    }
                }
            }
        }
    }

    fun setAudioReplacement(contex: Context, url: String?) {
        currentAudioUrl = url
        setTrim(_startMs.value, _endMs.value)
    }

    fun addOverlay(overlay: Overlay) {
        _overlays.value = _overlays.value + overlay
    }

    fun updateOverlay(updated: Overlay) {
        _overlays.value = _overlays.value.map { if (it.id == updated.id) updated else it }
    }

    fun removeOverlay(id: String) {
        _overlays.value = _overlays.value.filter { it.id != id }
    }

    fun setDrawOverlay(bitmap: Bitmap) {
        _drawOverlay.value = Overlay(
            type = Overlay.Type.STICKER,
            bitmap = bitmap.asImageBitmap(),
            scale = 1f,
            posXpx = 0f,  // misal default pixel X
            posYpx = 0f   // misal default pixel Y
        )
    }

    // ---------------------------------
    // Play tracking
    // ---------------------------------
    fun startTrackingPosition() {
        viewModelScope.launch {
            while (true) {
                _currentPlayTimeMs.value = _exoPlayer?.currentPosition ?: 0L
                delay(50)
            }
        }
    }

    // ---------------------------------
    // Cube effect
    // ---------------------------------
    fun selectCubeEffect(urlCube: String) {
        _cubeUrl.value = urlCube
    }

    // ---------------------------------
    // Load video
    // ---------------------------------
    fun loadUri(uri: Uri) {
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
                    startTrackingPosition()
                }
            }
        })
    }

    fun setTrim(
        startMs: Long,
        endMs: Long
    ) {
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtMost(videoDurationMs.value)
        _startMs.value = safeStart
        _endMs.value = safeEnd
        currentUri?.let {
            val mediaItem = buildTrimmedMediaItem(it, safeStart, safeEnd)
            _exoPlayer?.setMediaItem(mediaItem)
            // _exoPlayer?.prepare()
        }
    }

    fun buildTrimmedMediaItem(
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
    fun exportVideo(
        context: Context,
        cacheDir: File,
        outputFile: File? = null,
        onResult: (outputPath: String?, error: Exception?) -> Unit
    ) {

        val uri = currentUri ?: run {
            onResult(null, IllegalStateException("No media loaded"))
            return
        }

        val allOverlay = if (_drawOverlay.value != null) {
            listOf(_drawOverlay.value!!) + _overlays.value
        } else {
            _overlays.value
        }

        viewModelScope.launch {
            _exporting.value = true
            _exportProgress.value = 0f

            repository.exportVideo(
                context = context,
                uri = uri,
                audioUrl = currentAudioUrl?.toUri(),
                startMs = _startMs.value,
                endMs = _endMs.value,
                overlays = allOverlay,
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
    suspend fun addStickerFromUrl(
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