package com.zinmedia.videoeditor.data.repository

import androidx.core.graphics.scale
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.SingleColorLut
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.collect.ImmutableList
import com.zinmedia.videoeditor.core.helper.loadLutCubeFromUrl
import com.zinmedia.videoeditor.data.Overlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

internal fun resizeBitmap(
    bitmap: Bitmap,
    maxSize: Int = 300
): ImageBitmap {
    val ratio = min(
        maxSize.toFloat() / bitmap.width,
        maxSize.toFloat() / bitmap.height
    )

    val width = (bitmap.width * ratio).toInt()
    val height = (bitmap.height * ratio).toInt()

    val resizedBitmap = bitmap.scale(width, height)

    return resizedBitmap.asImageBitmap()
}

internal class VideoRepository {

    internal suspend fun loadSticker(
        context: Context,
        url: String,
        posXpx: Float,
        posYpx: Float,
        scale: Float,
        rotation: Float
    ): Overlay = withContext(Dispatchers.IO) {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val result = loader.execute(request)
        val bitmap = (result as? SuccessResult)
            ?.image
            ?.toBitmap()                      // ← gunakan ini
            ?: throw IllegalArgumentException("Gagal memuat stiker")
        Overlay(
            bitmap = resizeBitmap(bitmap),
            posXpx = posXpx,
            posYpx = posYpx,
            scale = scale,
            rotation = rotation
        )
    }

    @UnstableApi
    internal suspend fun exportVideo(
        context: Context,
        uri: Uri,
        audioUrl: Uri?,
        startMs: Long,
        endMs: Long,
        overlays: List<Overlay>,
        canvasLimit: Pair<Float, Float>?,
        cubeUrl: String,
        cacheDir: File,
        outputFile: File? = null,
        progressCallback: (Float) -> Unit,
        resultCallback: (String?, Exception?) -> Unit
    ) {
        try {
            val outFile = outputFile ?: File(cacheDir, "exported_video.mp4")
            val outputPath = outFile.absolutePath

            val videoMediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()

            val audioItem = audioUrl?.let {
                MediaItem.Builder()
                    .setUri(it)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(0)
                            .setEndPositionMs(endMs - startMs)
                            .build()
                    )
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle("audio-only")
                            .build()
                    )          // ⬅️ Audio ONLY
                    .build()
            }

            val video = EditedMediaItem.Builder(videoMediaItem)
                .setRemoveAudio(audioItem != null)

            val videoEffects = mutableListOf<Effect>()
            // Ukuran tampil video (metadata rotasi diperhitungkan).
            val (displayWidth, displayHeight) = readDisplaySize(context, uri)
            val heightRatio = if (displayWidth > 0 && displayHeight > 0) displayHeight.toFloat() / displayWidth else 16f / 9f
            // Kanvas = frame video + overlay yang keluar frame (area tambahan hitam), dibatasi area editor.
            val canvas = exportCanvas(
                boxes = overlays.map { it.toBox() },
                heightRatio = heightRatio,
                limit = canvasLimit,
            )
            val baseWidth = if (displayWidth > 0) displayWidth else MAX_SHORT_SIDE
            val (targetWidth, targetHeight) = fitExportSize(
                (canvas.width * baseWidth).toInt(),
                (canvas.height * baseWidth).toInt(),
            )

            // Filter warna hanya untuk video, sebelum ditempatkan di kanvas.
            loadLutCubeFromUrl(cubeUrl)?.let { videoEffects.add(SingleColorLut.createFromCube(it)) }
            videoEffects.add(CanvasPlacement(targetWidth, targetHeight, canvas, heightRatio))

            // Overlay ditempatkan relatif terhadap kanvas; ukurannya dari lebar relatif di preview.
            val textureOverlays = overlays.map { overlay ->
                val bitmap = overlay.bitmap.asAndroidBitmap()
                val overlayScale = if (overlay.widthFraction > 0f) {
                    overlay.widthFraction / canvas.width * targetWidth / bitmap.width
                } else {
                    overlay.scale
                }
                val (anchorX, anchorY) = canvas.toNdc(
                    (overlay.posXpx + 1f) / 2f,
                    (1f - overlay.posYpx) / 2f * heightRatio,
                )
                BitmapOverlay.createStaticBitmapOverlay(
                    bitmap,
                    StaticOverlaySettings.Builder()
                        .setScale(overlayScale, overlayScale)
                        .setRotationDegrees(overlay.rotation)
                        .setBackgroundFrameAnchor(anchorX, anchorY)
                        .build(),
                )
            }
            videoEffects.add(OverlayEffect(textureOverlays))

            video.setEffects(Effects(emptyList(), videoEffects))

            val videoSequence = EditedMediaItemSequence.Builder(
                video.build()
            ).build()

            val composition = audioItem?.let {

                val audioItemEdited = EditedMediaItem.Builder(it)
                    .setRemoveVideo(true)
                    .build()

                val audioSeq = EditedMediaItemSequence.Builder(
                    ImmutableList.of(audioItemEdited)
                ).build()

                Composition.Builder(videoSequence, audioSeq)
                    .build()

            } ?: Composition.Builder(videoSequence)
                .build()

            val transformer = Transformer.Builder(context)

                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressCallback(1f)
                        resultCallback(outputPath, null)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        resultCallback(null, exportException)
                    }
                }).build()

            transformer.start(composition, outputPath)

            // Progress loop
            val progressHolder = ProgressHolder()
            CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    when (transformer.getProgress(progressHolder)) {
                        Transformer.PROGRESS_STATE_AVAILABLE -> progressCallback(progressHolder.progress / 10000f * 100f)
                        else -> {}
                    }
                    delay(50)
                }
            }
        } catch (e: Exception) {
            resultCallback(null, e)
        }
    }
}

private fun Overlay.toBox(): OverlayBox = OverlayBox(
    anchorX = posXpx,
    anchorY = posYpx,
    widthFraction = widthFraction,
    aspect = bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1),
    rotationDegrees = rotation,
)

/**
 * Menempatkan frame video di posisinya dalam [canvas] berukuran [width]×[height]; sisa kanvas hitam.
 */
@UnstableApi
private class CanvasPlacement(
    private val width: Int,
    private val height: Int,
    canvas: CanvasRect,
    heightRatio: Float,
) : MatrixTransformation {
    private val matrix = android.graphics.Matrix().apply {
        // NDC frame video -> NDC kanvas.
        val (centerX, centerY) = canvas.toNdc(0.5f, heightRatio / 2f)
        setScale(1f / canvas.width, heightRatio / canvas.height)
        postTranslate(centerX, centerY)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(width, height)

    override fun getMatrix(presentationTimeUs: Long): android.graphics.Matrix = matrix
}

/** Batas ukuran video hasil ekspor (sisi panjang × sisi pendek). */
private const val MAX_LONG_SIDE = 1280
private const val MAX_SHORT_SIDE = 720
private const val ALIGNMENT = 16

/**
 * Ukuran video hasil ekspor untuk video tampil berukuran [width]×[height]: muat dalam
 * 1280×720 (sesuai orientasi), tidak diperbesar, dan kelipatan 16. Encoder umumnya membulatkan
 * ke kelipatan 16; bila tidak sudah pas, Media3 menambah garis hitam tipis di tepi.
 */
internal fun fitExportSize(width: Int, height: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return MAX_SHORT_SIDE to MAX_LONG_SIDE
    val landscape = width > height
    val maxW = if (landscape) MAX_LONG_SIDE else MAX_SHORT_SIDE
    val maxH = if (landscape) MAX_SHORT_SIDE else MAX_LONG_SIDE
    val scale = minOf(maxW.toFloat() / width, maxH.toFloat() / height, 1f)
    fun aligned(v: Float) = (v.toInt() / ALIGNMENT * ALIGNMENT).coerceAtLeast(ALIGNMENT)
    return aligned(width * scale) to aligned(height * scale)
}

/** Lebar×tinggi video sebagaimana tampil (ditukar bila metadata rotasi 90°/270°). */
internal fun readDisplaySize(context: android.content.Context, uri: android.net.Uri): Pair<Int, Int> {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation == 90 || rotation == 270) h to w else w to h
    } catch (e: Exception) {
        0 to 0
    } finally {
        retriever.release()
    }
}
