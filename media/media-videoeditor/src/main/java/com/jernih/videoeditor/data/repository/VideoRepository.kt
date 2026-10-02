package com.jernih.videoeditor.data.repository


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
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
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
import com.jernih.videoeditor.core.helper.createOverlaySpannable
import com.jernih.videoeditor.core.helper.loadLutCubeFromUrl
import com.jernih.videoeditor.data.Overlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

fun resizeBitmap(
    bitmap: Bitmap,
    maxSize: Int = 300
): ImageBitmap {
    val ratio = min(
        maxSize.toFloat() / bitmap.width,
        maxSize.toFloat() / bitmap.height
    )

    val width = (bitmap.width * ratio).toInt()
    val height = (bitmap.height * ratio).toInt()

    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

    return resizedBitmap.asImageBitmap()
}


class VideoRepository {

    suspend fun loadSticker(
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
//        val drawable = (result as? SuccessResult)?.image
//            ?: throw IllegalArgumentException("Gagal memuat stiker")
//        val bitmap = (drawable as BitmapDrawable).bitmap
        val bitmap = (result as? SuccessResult)
            ?.image
            ?.toBitmap()                      // ← gunakan ini
            ?: throw IllegalArgumentException("Gagal memuat stiker")
        Overlay(
            type = Overlay.Type.STICKER,
            bitmap = resizeBitmap(bitmap),
            posXpx = posXpx,
            posYpx = posYpx,
            scale = scale,
            rotation = rotation
        )
    }

    @UnstableApi
    suspend fun exportVideo(
        context: Context,
        uri: Uri,
        audioUrl: Uri?,
        startMs: Long,
        endMs: Long,
        overlays: List<Overlay>,
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
            //.build()

            val videoEffects = mutableListOf<Effect>()
            //UKURAN
            val maxWidth = 720
            val maxHeight = 1280

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val sourceWidth = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )!!.toInt()

            val sourceHeight = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )!!.toInt()

            retriever.release()

            val scale = minOf(
                maxWidth.toFloat() / sourceWidth,
                maxHeight.toFloat() / sourceHeight,
                1f // jangan perbesar video kecil
            )

            val targetWidth = (sourceWidth * scale).toInt()
            val targetHeight = (sourceHeight * scale).toInt()

            val presentation = Presentation.createForWidthAndHeight(
                targetWidth,
                targetHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            )

            videoEffects.add(presentation)
            //UKURAN

            // Convert overlays
            val textureOverlays = overlays.map { overlay ->
                StaticOverlaySettings.Builder()
                    .setScale(overlay.scale, overlay.scale)
                    .setRotationDegrees(overlay.rotation)
                    .setBackgroundFrameAnchor(overlay.posXpx, overlay.posYpx)
                    .build().let { setting ->
                        when (overlay.type) {
                            Overlay.Type.TEXT -> TextOverlay.createStaticTextOverlay(
                                createOverlaySpannable(
                                    overlay.text ?: "",
                                    overlay.color.toArgb(),
                                    overlay.bgcolor.toArgb(),
                                    overlay.typeface,
                                    20
                                ), setting
                            )

                            Overlay.Type.STICKER -> BitmapOverlay.createStaticBitmapOverlay(
                                overlay.bitmap!!.asAndroidBitmap(), setting
                            )
                        }
                    }
            }

            val lutCube = loadLutCubeFromUrl(cubeUrl)
            lutCube?.let {
                videoEffects.add(SingleColorLut.createFromCube(it))
            }
            videoEffects.add(OverlayEffect(textureOverlays))

            video.setEffects(Effects(emptyList(), videoEffects))

//            val videoSequence = EditedMediaItemSequence(
//                video.build()
//            )

//            val composition = audioItem?.let {
//                val audioSeq = EditedMediaItemSequence(
//                    ImmutableList.of(EditedMediaItem.Builder(it).setRemoveVideo(true).build())
//                )
//                Composition.Builder(videoSequence, audioSeq).build()
//            } ?: Composition.Builder(videoSequence).build()

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
