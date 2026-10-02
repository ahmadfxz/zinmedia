package com.zinmedia.videoeditor.data

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.effect.SingleColorLut
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import coil3.Bitmap
import com.zinmedia.videoeditor.core.helper.loadLutCubeFromUrl
import com.zinmedia.videoeditor.data.repository.VideoRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File



@UnstableApi
class VideoEditorViewModelTest(
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
            _exoPlayer = ExoPlayer.Builder(context).build()
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

//   init {
//       viewModelScope.launch {
//           cubeUrl.collectLatest { url ->
//               if (url.isNotEmpty()) {
//                   _exoPlayer?.stop()
//
//                   val lutCube = loadLutCubeFromUrlSuspend(url) // suspend loader
//                   if (lutCube != null) {
//                       val effects = listOf(SingleColorLut.createFromCube(lutCube))
//                       withContext(Dispatchers.Main) {
//                           _exoPlayer?.setVideoEffects(effects)
//                           _exoPlayer?.prepare()
//                       }
//                   } else {
//                       Log.e("LUT", "Gagal load LUT")
//                   }
//               }
//           }
//       }
//
//   }

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
//
//            override fun onSurfaceSizeChanged(width: Int, height: Int) {
//                _videoWidth.value = width
//                _videoHeight.value = height
//                Log.d("VIDEO_SIZE", "surface width=$width height=$height")
//            }
        })
    }

//    fun setTrim(context: Context, start: Long, end: Long) {
//        viewModelScope.launch {
//        val safeStart = start.coerceAtLeast(0L)
//        val safeEnd = end.coerceAtMost(videoDurationMs.value)
//        _startMs.value = safeStart
//        _endMs.value = safeEnd
//
//        currentUri?.let { uri ->
//            val clippedItem = MediaItem.Builder()
//                .setUri(uri)
//                .setClippingConfiguration(
//                    MediaItem.ClippingConfiguration.Builder()
//                        .setStartPositionMs(safeStart)
//                        .setEndPositionMs(safeEnd)
//                        .build()
//                )
//                .build()
//            _exoPlayer?.setMediaItem(clippedItem)}
//        if (_isInisialisasi.value) {
//            _isInisialisasi.value = false
//        }
//        }
//    }


//    fun setTrim(
//        context: Context,
//        startMs: Long,
//        endMs: Long
//    ) {
//        val safeStart = startMs.coerceAtLeast(0L)
//        val safeEnd = endMs.coerceAtMost(videoDurationMs.value)
//        _startMs.value = safeStart
//        _endMs.value = safeEnd
//        currentUri?.let {
//            val videoSource = buildTrimmedVideoSource(context, it, safeStart, safeEnd)
//
//            val finalSource = if (currentAudioUrl != null) {
//                val audioSource =
//                    buildTrimmedAudioSource(context, currentAudioUrl!!, safeStart, safeEnd)
//                MergingMediaSource(true, videoSource, audioSource)
//            } else {
//                videoSource   // tidak memakai audio baru
//            }
//
//            _exoPlayer?.setMediaSource(finalSource)
//            // _exoPlayer?.prepare()
//        }
//    }

    fun setTrim(
        startMs: Long,
        endMs: Long
    ) {
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtMost(videoDurationMs.value)
        _startMs.value = safeStart
        _endMs.value = safeEnd
        currentUri?.let {
            val mediaItem = buildTrimmedMediaItem( it, safeStart, safeEnd)
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
            .setStartsAtKeyFrame(true) // lebih smooth
            .build()

        return MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clipping)
            .build()
    }


//    fun buildTrimmedVideoSource(
//        context: Context,
//        uri: Uri,
//        startMs: Long,
//        endMs: Long
//    ): MediaSource {
//        val dataSource = DefaultDataSource.Factory(context)
//        val mediaItem = MediaItem.Builder()
//            .setUri(uri)
//            .setMediaMetadata(MediaMetadata.Builder().setTitle("video").build())
//            .build()
//
//        val videoSource = ProgressiveMediaSource.Factory(dataSource).createMediaSource(mediaItem)
//
//        return ClippingMediaSource(
//            videoSource,
//            startMs * 1000,
//            endMs * 1000
//        )
//    }

    fun buildTrimmedAudioSource(
        context: Context,
        audioUrl: String,
        startMs: Long,
        endMs: Long
    ): MediaSource {
        val dataSource = DefaultDataSource.Factory(context)
        val audioItem = MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaMetadata(MediaMetadata.Builder().setTitle("audio").build())
            .build()

        val audioSource = ProgressiveMediaSource.Factory(dataSource).createMediaSource(audioItem)
        return ClippingMediaSource(
            audioSource,
            0L,
            (endMs - startMs) * 1000
        )
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
//
//
//data class VideoEditorUiState(
//    val durationMs: Long = 0L,
//    val currentPosMs: Long = 0L,
//
//    val startMs: Long = 0L,
//    val endMs: Long = 0L,
//
//    val cubeUrl: String = "",
//    val exporting: Boolean = false,
//    val exportProgress: Float = 0f,
//    val isInitialized: Boolean = true,
//
//    val overlays: List<Overlay> = emptyList(),
//    val drawOverlay: Overlay? = null,
//
//    val videoWidth: Int = 0,
//    val videoHeight: Int = 0
//)
//
//class VideoPlayerController {
//
//    private var player: ExoPlayer? = null
//
//    fun init(context: Context) {
//        if (player == null) {
//            player = ExoPlayer.Builder(context).build()
//        }
//    }
//
//    fun release() {
//        player?.release()
//        player = null
//    }
//
//    fun get(): ExoPlayer? = player
//}
//
//class OverlayController {
//
//    private val _overlays = MutableStateFlow<List<Overlay>>(emptyList())
//    val overlays: StateFlow<List<Overlay>> = _overlays
//
//    private val _drawOverlay = MutableStateFlow<Overlay?>(null)
//    val drawOverlay: StateFlow<Overlay?> = _drawOverlay
//
//    fun add(overlay: Overlay) {
//        _overlays.value = _overlays.value + overlay
//    }
//
//    fun update(updated: Overlay) {
//        _overlays.value = _overlays.value.map {
//            if (it.id == updated.id) updated else it
//        }
//    }
//
//    fun remove(id: String) {
//        _overlays.value = _overlays.value.filter { it.id != id }
//    }
//
//    fun setDrawOverlay(bitmap: Bitmap) {
//        _drawOverlay.value = Overlay(
//            type = Overlay.Type.STICKER,
//            bitmap = bitmap.asImageBitmap(),
//            posXpx = 0f,
//            posYpx = 0f,
//            scale = 1f
//        )
//    }
//}
//
//@UnstableApi
//class VideoEditorViewModelTest(
//    private val repository: VideoRepository,
//    private val playerController: VideoPlayerController = VideoPlayerController(),
//    private val overlayController: OverlayController = OverlayController()
//) : ViewModel() {
//
//    private val _state = MutableStateFlow(VideoEditorUiState())
//    val state: StateFlow<VideoEditorUiState> = _state
//
//    private var videoUri: Uri? = null
//    private var currentAudioUrl: String? = null
//
//    val overlays = overlayController.overlays
//    val drawOverlay = overlayController.drawOverlay
//
//    init {
//        observeCubeEffect()
//        observeOverlays()
//        observeDrawOverlay()
//    }
//
//    override fun onCleared() {
//        releasePlayer()
//    }
//
//    fun initPlayer(context: Context) {
//        playerController.init(context)
//    }
//
//    fun releasePlayer() {
//        playerController.release()
//    }
//
//    private fun update(block: VideoEditorUiState.() -> VideoEditorUiState) {
//        _state.value = _state.value.block()
//    }
//
//    fun getPlayer(): ExoPlayer? {
//        return playerController.get()
//    }
//
//    fun loadVideo(uri: Uri) {
//        videoUri = uri
//        val player = playerController.get() ?: return
//
//        val mediaItem = MediaItem.fromUri(uri)
//
//        player.setMediaItem(mediaItem)
//        player.prepare()
//        player.repeatMode = Player.REPEAT_MODE_ALL
//
//        player.addListener(object : Player.Listener {
//            override fun onPlaybackStateChanged(state: Int) {
//                val duration = player.duration
//                if (duration > 0) {
//                    update {
//                        copy(
//                            durationMs = duration,
//                            endMs = duration
//                        )
//                    }
//                    player.removeListener(this)
//                    startTracking()
//                }
//            }
//        })
//    }
//
//
//    fun setTrim(context: Context, start: Long, end: Long) {
//        val duration = state.value.durationMs
//
//        val safeStart = start.coerceAtLeast(0L)
//        val safeEnd = end.coerceAtMost(duration)
//
//        update { copy(startMs = safeStart, endMs = safeEnd, isInitialized = false) }
//
//        val uri = videoUri ?: return
//        val player = playerController.get() ?: return
//
//        val videoSource = repository.buildTrimmedVideoSource(context, uri, safeStart, safeEnd)
//
//        val mergedSource = currentAudioUrl?.let { audio ->
//            val audioSource = repository.buildTrimmedAudioSource(context, audio, safeStart, safeEnd)
//            MergingMediaSource(true, videoSource, audioSource)
//        } ?: videoSource
//
//        player.setMediaSource(mergedSource)
//    }
//
//    fun setAudio(url: String?, context: Context) {
//        currentAudioUrl = url
//        setTrim(context, state.value.startMs, state.value.endMs)
//    }
//
//    private fun observeCubeEffect() {
//        viewModelScope.launch {
//            state
//                .map { it.cubeUrl }
//                .distinctUntilChanged()   // ⬅ hanya lanjut kalau URL berbeda
//                .collectLatest { url ->
//                    if (url.isEmpty()) return@collectLatest
//                    val lutCube = loadLutCubeFromUrl(url)
//                    if (lutCube != null) {
//                        playerController.get()?.apply {
//                            stop()
//                            setVideoEffects(listOf(SingleColorLut.createFromCube(lutCube)))
//                            prepare()
//                        }
//                    }
//                }
//        }
//    }
//
//
//    private fun startTracking() {
//        val player = playerController.get() ?: return
//        viewModelScope.launch {
//            while (true) {
//                update { copy(currentPosMs = player.currentPosition) }
//                delay(50)
//            }
//        }
//    }
//
//    fun exportVideo(
//        context: Context,
//        cacheDir: File,
//        outputFile: File?,
//        onResult: (String?, Exception?) -> Unit
//    ) {
//        val uri = videoUri ?: return onResult(null, IllegalStateException("No video loaded"))
//
//        val overlaysFinal = buildList {
//            drawOverlay.value?.let { add(it) }
//            addAll(overlays.value)
//        }
//
//        viewModelScope.launch {
//            update { copy(exporting = true, exportProgress = 0f) }
//
//            repository.exportVideo(
//                context = context,
//                uri = uri,
//                audioUrl = currentAudioUrl?.toUri(),
//                startMs = state.value.startMs,
//                endMs = state.value.endMs,
//                overlays = overlaysFinal,
//                cubeUrl = state.value.cubeUrl,
//                cacheDir = cacheDir,
//                outputFile = outputFile,
//                progressCallback = { p -> update { copy(exportProgress = p) } },
//                resultCallback = { path, err ->
//                    update { copy(exporting = false) }
//                    onResult(path, err)
//                }
//            )
//        }
//    }
//
//    private fun observeOverlays() {
//        viewModelScope.launch {
//            overlayController.overlays
//                .distinctUntilChanged { old, new ->
//                    old.size == new.size && old == new
//                }
//                .collect { list ->
//                    update { copy(overlays = list) }
//                }
//        }
//    }
//
//
//
//    private fun observeDrawOverlay() {
//        viewModelScope.launch {
//            overlayController.drawOverlay
//                .distinctUntilChanged { old, new ->
//                    old == new
//                }
//                .collect { overlay ->
//                    update { copy(drawOverlay = overlay) }
//                }
//        }
//    }
//
//
//
//
//    //overlay
//    fun addOverlay(overlay: Overlay) = overlayController.add(overlay)
//    fun updateOverlay(overlay: Overlay) = overlayController.update(overlay)
//    fun removeOverlay(id: String) = overlayController.remove(id)
//    fun setDrawOverlay(bitmap: Bitmap) = overlayController.setDrawOverlay(bitmap)
//
//    //sticker
//    suspend fun addStickerFromUrl(
//        context: Context,
//        url: String,
//        posXpx: Float = 0f,
//        posYpx: Float = 0f,
//        scale: Float = 1f,
//        rotation: Float = 0f
//    ): Overlay {
//        return repository.loadSticker(context, url, posXpx, posYpx, scale, rotation)
//    }
//
//    fun selectCubeEffect(urlCube: String) {
//        update {
//            copy(cubeUrl = urlCube)
//        }
//    }
//}
